package com.codingshuttleproject.lovableclone.llm.tools;

import com.codingshuttleproject.lovableclone.llm.advisors.FileTreeContextAdvisor;
import com.codingshuttleproject.lovableclone.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Slf4j
public class CodeGenerationTools {

    private final ProjectFileService projectFileService;
    private final Long projectId;

    // One instance per chat request, so these track this request only.
    // Stops the model looping on read_files (each round costs one more AI request).
    private static final int MAX_READ_CALLS = 3;
    private static final String STOP_READING =
            "STOP: you already have this file's content. Do not call read_files again. "
                    + "Output the <file> tags for your planned files now.";
    private final Set<String> alreadyRead = ConcurrentHashMap.newKeySet();
    private final AtomicInteger readCalls = new AtomicInteger();


    @Tool(name = "read_files",
            description = "Read the content of files. Only input the file names present inside the FILE_TREE. DO NOT input any path which is not present under the FILE_TREE.")

    public List<String> readFiles(
            @ToolParam(description = "List of relative paths (e.g., ['src/App.tsx'])")
            List<String> paths
    ){

        List<String> result = new ArrayList<>();

        if (readCalls.incrementAndGet() > MAX_READ_CALLS) {
            log.warn("read_files called more than {} times for project {}; refusing", MAX_READ_CALLS, projectId);
            result.add("STOP: read_files limit reached. You have enough context. "
                    + "Output the <file> tags for your planned files now, without calling any tool.");
            return result;
        }

        for(String path : paths){
            String cleanPath=path.startsWith("/")?path.substring(1):path;

            log.info("Requested file:{}",cleanPath);

            // Already sent in the prompt, or already returned earlier in this request.
            if (FileTreeContextAdvisor.PRELOADED_FILES.contains(cleanPath) || !alreadyRead.add(cleanPath)) {
                log.info("read_files: {} was already provided; not sending it again", cleanPath);
                result.add(String.format("FILE: %s --- %s", cleanPath, STOP_READING));
                continue;
            }

            String content;
            try {
                // Return only the file text; the record's toString() would add "FileContentResponse[path=..., content=...]".
                content = projectFileService.getFileContent(projectId, cleanPath).content();
            } catch (Exception e) {
                // A missing file must not kill the whole generation stream; tell the model instead.
                log.warn("read_files: {} could not be read", cleanPath, e);
                content = "ERROR: this file does not exist. Only read paths listed in FILE_TREE.";
            }
            result.add(String.format(
                    "START OF FILE: %s ---\n%s\n--- END OF FILE ---",
                    cleanPath,content
            ));


        }
        return result;

    }
}
