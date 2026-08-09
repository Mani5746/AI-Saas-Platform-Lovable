package com.codingshuttleproject.lovableclone.mapper;

import com.codingshuttleproject.lovableclone.dto.project.FileNode;
import com.codingshuttleproject.lovableclone.entity.ProjectFile;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectFileMapper {
    List<FileNode> toListOfFileNode(List<ProjectFile> projectFileList);
}
