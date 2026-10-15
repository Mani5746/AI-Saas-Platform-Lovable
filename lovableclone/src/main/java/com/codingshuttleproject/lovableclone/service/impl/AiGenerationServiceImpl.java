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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Course-style generation: one streamed model call writes every file as
 * {@code <file path="...">...</file>} tags, so all files see the same API.
 *
 * <p>Files are saved all-or-nothing: if the response was cut off or has an
 * unclosed {@code <file>} tag, nothing is written, so a project can never end up
 * half old run, half new run.
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class AiGenerationServiceImpl implements AiGenerationService {

    private final ChatClient chatClient;
    private final AuthUtil authUtil;
    private final ProjectFileService projectFileService;
    private final FileTreeContextAdvisor fileTreeContextAdvisor;

    private static final Pattern FILE_TAG_PATTERN = Pattern.compile(
            "<file\\s+path\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</file>", Pattern.DOTALL);
    // Counts every opening tag, complete or not, to detect a truncated last file.
    private static final Pattern FILE_OPEN_TAG_PATTERN = Pattern.compile("<file\\s+path\\s*=");
    // Models often wrap the file body in a markdown fence inside the <file> tag.
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^```[\\w-]*\\R?|\\R?```$");
    // Fallback format some models use instead of <file> tags:
    //   ```typescript file="src/App.tsx"
    //   ...code...
    //   ```
    private static final Pattern FENCED_FILE_PATTERN = Pattern.compile(
            "^```[\\w.+-]*[ \\t]+(?:file|path|filename)\\s*=\\s*[\"']([^\"']+)[\"'][^\\n]*\\R(.*?)\\R```[ \\t]*$",
            Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern FENCED_FILE_OPEN_PATTERN = Pattern.compile(
            "^```[\\w.+-]*[ \\t]+(?:file|path|filename)\\s*=", Pattern.MULTILINE);
    // "length" is OpenAI's value; Gemini may report its own name for the same thing.
    private static final Set<String> TRUNCATED_FINISH_REASONS = Set.of("length", "max_tokens");

    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public Flux<String> streamResponse(String userMessage, Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        createChatSessionIfNotExists(projectId, userId);
        Map<String, Object> advisorParams = Map.of(
                "userId", userId,
                "projectId", projectId
        );

        // Capture context while it's still valid on this thread
        SecurityContext securityContext = SecurityContextHolder.getContext();

        StringBuilder fullResponseBuffer = new StringBuilder();
        AtomicReference<String> lastFinishReason = new AtomicReference<>();
        CodeGenerationTools codeGenerationTools = new CodeGenerationTools(projectFileService, projectId);

        Flux<String> firstPass = streamText(
                chatClient.prompt()
                        .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT)
                        .user(userMessage),
                codeGenerationTools, advisorParams, fullResponseBuffer, lastFinishReason, projectId);

        // Weaker models sometimes stop right after the plan ("Let's write these files now")
        // with no <file> tags. Ask once more, in the same conversation, for the files only.
        // Costs one extra AI request, and only when the first pass produced no files.
        Flux<String> followUp = Flux.defer(() -> {
            if (!needsFollowUp(fullResponseBuffer.toString(), lastFinishReason.get())) {
                return Flux.<String>empty();
            }
            log.info("Project {}: response had no <file> tags; asking the model once more for the files", projectId);
            String planSoFar = fullResponseBuffer.toString();
            return Flux.concat(
                    Flux.just("\n"),
                    streamText(
                            chatClient.prompt()
                                    .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT)
                                    .messages(new UserMessage(userMessage), new AssistantMessage(planSoFar))
                                    .user(PromptUtils.CONTINUE_WITH_FILES_PROMPT),
                            codeGenerationTools, advisorParams, fullResponseBuffer, lastFinishReason, projectId));
        });

        return firstPass
                .concatWith(followUp)
                // Persist inside the reactive chain, then stream one final status line.
                .concatWith(Mono.fromCallable(() -> {
                    SecurityContextHolder.setContext(securityContext);
                    try {
                        return parseAndSaveFiles(fullResponseBuffer.toString(), projectId, lastFinishReason.get());
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }).subscribeOn(Schedulers.boundedElastic()))
                // Stream failures (429, 5xx, network) reach the user as a message, not a dead stream.
                .onErrorResume(error -> Flux.just(
                        "\n[ERROR] Generation failed before it finished, so no files were changed. Please try again.\n"));
    }

    /** One streamed model call; appends its text to the buffer and records the finish reason. */
    private Flux<String> streamText(ChatClient.ChatClientRequestSpec spec, CodeGenerationTools tools,
                                    Map<String, Object> advisorParams, StringBuilder buffer,
                                    AtomicReference<String> lastFinishReason, Long projectId) {
        return spec
                .tools(tools)
                .advisors(advisorSpec -> {
                    advisorSpec.params(advisorParams);
                    advisorSpec.advisors(fileTreeContextAdvisor);
                })
                .stream()
                .chatResponse()
                // Remember the last finish reason so we can tell "done" from "cut off".
                .doOnNext(response -> {
                    String reason = finishReasonOf(response);
                    if (reason != null) {
                        lastFinishReason.set(reason);
                    }
                })
                // Some chunks have no choices (final usage frame) or null content
                // (tool-call deltas). Unwrap defensively and drop the empties.
                .map(AiGenerationServiceImpl::textOf)
                .filter(content -> !content.isEmpty())
                .doOnNext(buffer::append)
                .doOnError(error -> logProviderError(error, projectId));
    }

    /** True when the model finished normally but wrote no <file> tag at all. */
    private static boolean needsFollowUp(String response, String finishReason) {
        boolean truncated = finishReason != null && TRUNCATED_FINISH_REASONS.contains(finishReason.toLowerCase());
        return !truncated
                && !FILE_OPEN_TAG_PATTERN.matcher(response).find()
                && !FENCED_FILE_OPEN_PATTERN.matcher(response).find();
    }

    /**
     * Validates the whole response first and only then saves. Returns the status
     * line streamed to the user as the last message.
     */
    private String parseAndSaveFiles(String fullResponse, Long projectId, String finishReason) {
        log.info("Generation for project {} finished with {} chars, finish reason {}",
                projectId, fullResponse.length(), finishReason);

        // 1. Cut off by the token limit: the last file (or a file someone imports) is missing.
        if (finishReason != null && TRUNCATED_FINISH_REASONS.contains(finishReason.toLowerCase())) {
            log.warn("Response for project {} hit the token limit; nothing saved", projectId);
            return "\n[ERROR] The AI response was cut off (token limit), so no files were changed. "
                    + "Try a smaller request, or ask for the app in parts.\n";
        }

        // 2. Parse every complete <file> tag before touching storage.
        Map<String, String> files = new LinkedHashMap<>();
        Matcher matcher = FILE_TAG_PATTERN.matcher(fullResponse);
        while (matcher.find()) {
            String path = cleanPath(matcher.group(1));
            if (path == null) {
                log.warn("Rejected unsafe file path '{}' for project {}", matcher.group(1), projectId);
                return "\n[ERROR] The AI produced an invalid file path (" + matcher.group(1)
                        + "), so no files were changed. Please try again.\n";
            }
            String content = CODE_FENCE_PATTERN.matcher(matcher.group(2).trim()).replaceAll("").strip() + "\n";
            if (files.put(path, content) != null) {
                log.warn("{} was output more than once for project {}; keeping the last copy", path, projectId);
            }
        }

        // 3. An opening tag without a closing tag means the response is incomplete.
        long openTags = FILE_OPEN_TAG_PATTERN.matcher(fullResponse).results().count();
        long completeTags = FILE_TAG_PATTERN.matcher(fullResponse).results().count();
        if (openTags != completeTags) {
            log.warn("Project {}: {} <file> tags opened but only {} closed; nothing saved",
                    projectId, openTags, completeTags);
            return "\n[ERROR] The AI response had an unfinished file, so no files were changed. Please try again.\n";
        }

        // 3b. No <file> tags at all: accept ```lang file="path" fences instead (same all-or-nothing checks).
        if (openTags == 0) {
            long openFences = FENCED_FILE_OPEN_PATTERN.matcher(fullResponse).results().count();
            Matcher fenced = FENCED_FILE_PATTERN.matcher(fullResponse);
            while (fenced.find()) {
                String path = cleanPath(fenced.group(1));
                if (path == null) {
                    log.warn("Rejected unsafe file path '{}' for project {}", fenced.group(1), projectId);
                    return "\n[ERROR] The AI produced an invalid file path (" + fenced.group(1)
                            + "), so no files were changed. Please try again.\n";
                }
                files.put(path, fenced.group(2).strip() + "\n");
            }
            if (openFences != files.size()) {
                log.warn("Project {}: {} fenced files opened but {} complete; nothing saved",
                        projectId, openFences, files.size());
                return "\n[ERROR] The AI response had an unfinished file, so no files were changed. Please try again.\n";
            }
            if (!files.isEmpty()) {
                log.info("Project {}: model used ```file=\"...\" fences instead of <file> tags; parsed {} file(s)",
                        projectId, files.size());
            }
        }

        if (files.isEmpty()) {
            log.warn("No <file> tags in the response for project {}", projectId);
            return "\n[WARNING] The AI response contained no files, so nothing was changed.\n";
        }

        // 4. Everything checked out: save all files.
        int saved = 0;
        for (Map.Entry<String, String> file : files.entrySet()) {
            try {
                projectFileService.saveFile(projectId, file.getKey(), file.getValue());
                saved++;
            } catch (Exception e) {
                // Storage failed midway (e.g. MinIO down); report exactly what landed.
                log.error("Failed to save {} for project {} after {} file(s)", file.getKey(), projectId, saved, e);
                return "\n[ERROR] Saving failed at " + file.getKey() + " after " + saved + " of " + files.size()
                        + " file(s). Please run the same request again.\n";
            }
        }
        log.info("Saved {} file(s) for project {}: {}", saved, projectId, files.keySet());
        return "\n[DONE] Saved " + saved + " file(s): " + String.join(", ", files.keySet()) + "\n";
    }

    /** Relative, normalised path, or null if the path is unsafe. */
    private static String cleanPath(String rawPath) {
        String path = rawPath.trim().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty() || path.contains("..")) {
            return null;
        }
        return path;
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private static String finishReasonOf(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return null;
        }
        for (Generation generation : response.getResults()) {
            if (generation != null && generation.getMetadata() != null) {
                String reason = generation.getMetadata().getFinishReason();
                if (reason != null && !reason.isBlank()) {
                    return reason;
                }
            }
        }
        return null;
    }

    private static void logProviderError(Throwable error, Long projectId) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof WebClientResponseException)) {
            cause = cause.getCause();
        }
        if (cause instanceof WebClientResponseException responseError) {
            log.error("AI provider returned {} for project {}. Response body: {}",
                    responseError.getStatusCode(), projectId, responseError.getResponseBodyAsString());
        } else {
            log.error("AI generation failed for project {}", projectId, error);
        }
    }

    private void createChatSessionIfNotExists(Long projectId, Long userId) {

    }
}
