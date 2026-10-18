package com.codingshuttleproject.lovableclone.llm.tools;

import com.codingshuttleproject.lovableclone.llm.advisors.FileTreeContextAdvisor;
import com.codingshuttleproject.lovableclone.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    + "Write your planned files with write_files now.";
    private final Set<String> alreadyRead = ConcurrentHashMap.newKeySet();
    private final AtomicInteger readCalls = new AtomicInteger();

    // write_files only records the file. finalizeChats saves it (FILE_EDIT event), same as a <file> tag.
    private static final int MAX_WRITE_FILES = 15;
    private final Map<String, String> writtenFiles = Collections.synchronizedMap(new LinkedHashMap<>());

    /** Files written with write_files, as <file> tags so LlmResponseParser turns them into FILE_EDIT events. */
    public List<String> writtenFileTags() {
        List<String> tags = new ArrayList<>();
        synchronized (writtenFiles) {
            writtenFiles.forEach((path, content) ->
                    tags.add(String.format("\n<file path=\"%s\">\n%s\n</file>\n", path, content)));
        }
        return tags;
    }


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
                    + "Write your planned files with write_files now, without calling read_files again.");
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

    /** One file for write_files. */
    public record FileToWrite(
            @ToolParam(description = "Relative file path, e.g. 'src/components/Navbar.tsx'") String path,
            @ToolParam(description = "The complete file content. No placeholders, no markdown code fences.") String content
    ) {}

    // Takes a list so every file goes in ONE call: each tool round is one more AI request,
    // and the free tier allows only a few requests per day.
    @Tool(name = "write_files",
            description = "Create or overwrite project files with their COMPLETE new content. "
                    + "Put ALL files of your change in ONE call.")
    public List<String> writeFiles(
            @ToolParam(description = "Every file to write, each with its relative path and complete content")
            List<FileToWrite> files
    ) {
        List<String> result = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            result.add("ERROR: no files given. Pass every file with its path and complete content.");
            return result;
        }

        for (FileToWrite file : files) {
            String path = file.path();
            String content = file.content();
            String cleanPath = path == null ? "" : path.trim().replace('\\', '/').replaceFirst("^/+", "");
            if (cleanPath.isEmpty() || cleanPath.contains("..")) {
                log.warn("write_files: rejected unsafe path '{}' for project {}", path, projectId);
                result.add("ERROR: invalid path '" + path + "'. Use a relative path like src/App.tsx.");
                continue;
            }
            if (content == null || content.isBlank()) {
                result.add("ERROR: content for " + cleanPath + " is empty. Send the complete file content.");
                continue;
            }
            if (!writtenFiles.containsKey(cleanPath) && writtenFiles.size() >= MAX_WRITE_FILES) {
                log.warn("write_files: more than {} files for project {}; refusing {}", MAX_WRITE_FILES, projectId, cleanPath);
                result.add("ERROR: file limit reached, " + cleanPath + " not written. "
                        + "Do not write more files; finish with a short <message phase=\"completed\">.");
                continue;
            }

            writtenFiles.put(cleanPath, content.strip());
            log.info("write_files: {} ({} chars) for project {}", cleanPath, content.length(), projectId);
            result.add("OK: " + cleanPath + " written.");
        }
        return result;
    }
}
