package com.codingshuttleproject.lovableclone.service;

import com.codingshuttleproject.lovableclone.dto.project.FileContentResponse;
import com.codingshuttleproject.lovableclone.dto.project.FileNode;
import com.codingshuttleproject.lovableclone.dto.project.FileTreeResponse;

import java.util.List;

public interface ProjectFileService {

     List<FileNode> getFileTree(Long projectId);


     FileContentResponse getFileContent(Long projectId, String path);

     void saveFile(Long projectId, String filePath, String fileContent);
}
