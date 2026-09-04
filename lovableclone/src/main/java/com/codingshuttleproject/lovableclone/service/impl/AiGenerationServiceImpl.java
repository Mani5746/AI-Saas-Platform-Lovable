package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.llm.PromptUtils;
import com.codingshuttleproject.lovableclone.llm.advisors.FileTreeContextAdvisor;
import com.codingshuttleproject.lovableclone.llm.tools.CodeGenerationTools;
import com.codingshuttleproject.lovableclone.security.AuthUtil;
import com.codingshuttleproject.lovableclone.service.AiGenerationService;
import com.codingshuttleproject.lovableclone.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import  org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Objects;
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

    private static final Pattern FILE_TAG_PATTERN = Pattern.compile("<file path=\"([^\"]+)\">(.*?)</file>", Pattern.DOTALL);
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
                .doOnNext(response->{
                    String content= response.getResult().getOutput().getText();
                    fullResponseBuffer.append(content);
                })
                .doOnComplete(()->{
                    Schedulers.boundedElastic().schedule(()->{
                        SecurityContextHolder.setContext(securityContext);
                        try {
                            parseAndSaveFiles(fullResponseBuffer.toString(),projectId);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    });
                })
                .doOnError(error->log.error("Error during streaming for project id"+projectId))
                .map(response-> Objects.requireNonNull(response.getResult().getOutput().getText()));
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
        while (matcher.find()) {
            String filePath = matcher.group(1);
            String fileContent = matcher.group(2).trim();
            projectFileService.saveFile(projectId,filePath,fileContent);
        }
    }

    private void createChatSessionIfNotExists(Long projectId, Long userId) {

    }
}
