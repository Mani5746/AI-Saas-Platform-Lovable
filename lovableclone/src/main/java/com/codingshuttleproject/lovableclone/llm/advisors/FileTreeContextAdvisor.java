package com.codingshuttleproject.lovableclone.llm.advisors;

import com.codingshuttleproject.lovableclone.dto.project.FileNode;
import com.codingshuttleproject.lovableclone.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Component
@Slf4j
@RequiredArgsConstructor
public class FileTreeContextAdvisor implements StreamAdvisor {

    /**
     * Files sent with every request, so the model rarely needs read_files.
     * Each read_files round is one more paid/quota request to the AI provider.
     */
    public static final List<String> PRELOADED_FILES =
            List.of("package.json", "index.html", "src/main.tsx", "src/App.tsx", "src/index.css");
    private static final int MAX_PRELOAD_CHARS_PER_FILE = 12_000;

    private final ProjectFileService projectFileService;


    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain streamAdvisorChain) {
        Map<String,Object> context=request.context();
       Long projectId=Long.parseLong(context.getOrDefault("projectId",0).toString());
        ChatClientRequest augmentedChatClientRequest=augmentRequestWithFileTree(request,projectId);
        return streamAdvisorChain.nextStream(augmentedChatClientRequest);

    }

    private ChatClientRequest augmentRequestWithFileTree(ChatClientRequest request,Long projectId){

        List<Message> incomingMessages = request.prompt().getInstructions();

        Message systemMessage = incomingMessages.stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .findFirst()
                .orElse(null);

        List<Message> userMessages = incomingMessages.stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM)
                .toList();

        List<Message> allMessages = new ArrayList<>();

        // Add original system message
        if (systemMessage != null) {
            allMessages.add(systemMessage);
        }


        List<FileNode> fileTree = projectFileService.getFileTree(projectId);
        String fileTreeContext = "\n\n ---- FILE_TREE ----\n" + fileTree.toString()
                + preloadedFilesContext(projectId, fileTree);
        allMessages.add(new SystemMessage(fileTreeContext));

        allMessages.addAll(userMessages);

        return request
                .mutate()
                .prompt(new Prompt(allMessages, request.prompt().getOptions()))
                .build();
    }

    /** Current content of PRELOADED_FILES that exist in this project. */
    private String preloadedFilesContext(Long projectId, List<FileNode> fileTree) {
        List<String> paths = fileTree.stream().map(FileNode::path).toList();
        StringBuilder sb = new StringBuilder("\n\n ---- ALREADY LOADED FILES ----\n")
                .append("You already have the current content of these files. Do NOT call read_files for them.\n");
        for (String path : PRELOADED_FILES) {
            if (!paths.contains(path)) {
                continue;
            }
            try {
                String content = projectFileService.getFileContent(projectId, path).content();
                if (content.length() > MAX_PRELOAD_CHARS_PER_FILE) {
                    content = content.substring(0, MAX_PRELOAD_CHARS_PER_FILE) + "\n... (truncated, use read_files for the rest)";
                }
                sb.append("\nSTART OF FILE: ").append(path).append(" ---\n")
                        .append(content).append("\n--- END OF FILE ---\n");
            } catch (Exception e) {
                log.warn("Could not preload {} for project {}", path, projectId, e);
            }
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return "fileTreeContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
