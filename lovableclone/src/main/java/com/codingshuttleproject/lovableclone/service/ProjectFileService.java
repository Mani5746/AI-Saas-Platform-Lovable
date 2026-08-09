package com.codingshuttleproject.lovableclone.service;

import com.codingshuttleproject.lovableclone.dto.project.FileContentResponse;
import com.codingshuttleproject.lovableclone.dto.project.FileNode;

import java.util.List;

public interface ProjectFileService {
     List<FileNode>getFileTree(Long projectId);

     FileContentResponse getFileContent(Long projectId, String path);

     List<FileNode> getFileTree(Long projectId, Long userId);

     FileContentResponse getFileContent(Long projectId, String path, Long userId);

     void saveFile(Long projectId, String filePath, String fileContent);
}
