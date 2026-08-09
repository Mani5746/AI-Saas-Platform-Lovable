package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.dto.project.FileContentResponse;
import com.codingshuttleproject.lovableclone.dto.project.FileNode;
import com.codingshuttleproject.lovableclone.service.ProjectFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProjectFileServiceImpl implements ProjectFileService {

    @Override
    public List<FileNode> getFileTree(Long projectId, Long userId) {
        // TODO: implement get file tree logic
        return null;
    }

    @Override
    public FileContentResponse getFileContent(Long projectId, String path, Long userId) {
        // TODO: implement get file content logic
        return null;
    }

    @Override
    public void saveFile(Long projectId, String filePath, String fileContent) {
        log.info("Saving file: {}",filePath);
        // Save the file MetaData in postgres
        // Save the content inside minio
    }
}
