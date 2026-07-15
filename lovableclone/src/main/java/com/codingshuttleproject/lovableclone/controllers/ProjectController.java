package com.codingshuttleproject.lovableclone.controllers;

import com.codingshuttleproject.lovableclone.dto.project.ProjectRequest;
import com.codingshuttleproject.lovableclone.dto.project.ProjectResponse;
import com.codingshuttleproject.lovableclone.dto.project.ProjectSummaryResponse;
import com.codingshuttleproject.lovableclone.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")

public class ProjectController{

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectSummaryResponse>>getMyProjects(){


        return ResponseEntity.ok(projectService.getUserProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse>getProjectById(@PathVariable Long id){
Long userId=1L;
return ResponseEntity.ok(projectService.getUserProjectById(id));
    }

    @PostMapping
    ResponseEntity<ProjectResponse> createProject(@RequestBody @Valid ProjectRequest request ){
        Long userId=1L;
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id,@RequestBody @Valid ProjectRequest request){
        Long userId=1L;
return ResponseEntity.ok(projectService.updateProject(id,request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id){
        Long userId=1L;
        projectService.softDelete(id);
        return ResponseEntity.noContent().build();
    }


}
