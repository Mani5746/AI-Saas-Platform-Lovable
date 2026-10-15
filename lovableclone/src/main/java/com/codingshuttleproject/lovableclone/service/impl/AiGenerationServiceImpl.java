package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.llm.PromptUtils;
import com.codingshuttleproject.lovableclone.llm.advisors.FileTreeContextAdvisor;
import com.codingshuttleproject.lovableclone.llm.tools.CodeGenerationTools;
import com.codingshuttleproject.lovableclone.security.AuthUtil;
import com.codingshuttleproject.lovableclone.service.AiGenerationService;
import com.codingshuttleproject.lovableclone.service.ProjectFileService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import  org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Slf4j
@Service
public class AiGenerationServiceImpl implements AiGenerationService {

    private final ChatClient chatClient;
    private final AuthUtil authUtil;
    private final ProjectFileService projectFileService;
    private final FileTreeContextAdvisor fileTreeContextAdvisor;
    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    private static final Pattern FILE_TAG_PATTERN = Pattern.compile(
            "<file\\s+path\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</file>", Pattern.DOTALL);
    // Models often wrap the file body in a markdown fence inside the <file> tag.
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^```[\\w-]*\\R?|\\R?```$");
    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public Flux<String> streamResponse(String userMessage, Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        createChatSessionIfNotExists(projectId,userId);
        Map<String,Object> advisorParams = Map.of(
                "userId",userId,
                "projectId",projectId
        );

        // Capture context while it's still valid on this thread
        SecurityContext securityContext = SecurityContextHolder.getContext();

        StringBuilder fullResponseBuffer= new StringBuilder();
        CodeGenerationTools codeGenerationTools=new CodeGenerationTools(projectFileService,projectId);


        return chatClient.prompt()
                .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT )
                .user(userMessage)
                .tools(codeGenerationTools)
                .advisors(advisorSpec ->{ advisorSpec.params(advisorParams);
                    advisorSpec.advisors(fileTreeContextAdvisor);})

                .stream()
                .chatResponse()
                // OpenRouter emits chunks with no choices (final usage frame) and
                // chunks whose content is null (tool-call deltas). Both make
                // getResult()/getText() return null, so unwrap defensively and drop
                // the empties instead of letting an NPE kill the stream mid-answer.
                .map(AiGenerationServiceImpl::textOf)
                .filter(content -> !content.isEmpty())
                .doOnNext(fullResponseBuffer::append)
                .doOnError(error -> {
                    Throwable cause = error;
                    while (cause != null && !(cause instanceof WebClientResponseException)) {
                        cause = cause.getCause();
                    }

                    if (cause instanceof WebClientResponseException responseError) {
                        log.error("AI provider returned {} for project {}. Response body: {}",
                                responseError.getStatusCode(),
                                projectId,
                                responseError.getResponseBodyAsString());
                    } else {
                        log.error("AI generation failed for project {}", projectId, error);
                    }
                })
                // Keep persistence in the reactive chain. The previous detached
                // doFinally task let the SSE request finish before MinIO/Postgres did.
                .concatWith(Mono.fromRunnable(() -> {
                    log.info("Generation for project {} finished with {} buffered chars",
                            projectId, fullResponseBuffer.length());
                    SecurityContextHolder.setContext(securityContext);
                    try {
                        parseAndSaveFiles(fullResponseBuffer.toString(), projectId);
                    } catch (Exception e) {
                        log.error("Failed to save generated files for project {}", projectId, e);
                        throw new IllegalStateException("Failed to save generated project files", e);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }).subscribeOn(Schedulers.boundedElastic()).thenMany(Flux.<String>empty()));
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private void parseAndSaveFiles(String fullResponse, Long projectId) {
//        String dummy= """
//                <message> I'm going to read the files and generate the Code</message>
//                <file path="src/App.jsx">
//                import App from './App.jsx'
//                ..........
//                .......
//                </file>
//
//                <message> I'm going to read the files and generate the Code</message>
//                <file path="src/App.jsx">
//                import App from './App.jsx'
//                ..........
//                .......
//                </file>
//                """;
        Matcher matcher = FILE_TAG_PATTERN.matcher(fullResponse);
        int saved = 0;
        int failed = 0;
        while (matcher.find()) {
            String filePath = matcher.group(1).trim();
            String fileContent = CODE_FENCE_PATTERN.matcher(matcher.group(2).trim()).replaceAll("");
            try {
                projectFileService.saveFile(projectId,filePath,fileContent);
                saved++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to save {} for project {}", filePath, projectId, e);
            }
        }
        if (saved == 0) {
            log.warn("No <file> tags found in the generated response for project {} ({} chars)",
                    projectId, fullResponse.length());
            throw new IllegalStateException("AI response contained no complete <file> tags to persist");
        } else {
            log.info("Saved {} file(s) for project {}", saved, projectId);
        }
        if (failed > 0) {
            throw new IllegalStateException("Failed to persist " + failed + " generated file(s)");
        }
    }

    private void createChatSessionIfNotExists(Long projectId, Long userId) {

    }
}
