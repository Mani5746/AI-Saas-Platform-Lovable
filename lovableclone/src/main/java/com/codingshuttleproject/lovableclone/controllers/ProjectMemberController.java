package com.codingshuttleproject.lovableclone.controllers;
import com.codingshuttleproject.lovableclone.dto.member.InviteMemberRequest;
import com.codingshuttleproject.lovableclone.dto.member.MemberResponse;
import com.codingshuttleproject.lovableclone.dto.member.UpdateMemberRoleRequest;
import com.codingshuttleproject.lovableclone.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    public ResponseEntity<List<MemberResponse>> getProjectMembers(
            @PathVariable Long projectId
    ){

 return ResponseEntity.ok(projectMemberService.getProjectMembers(projectId));
    }

    @PostMapping
    public ResponseEntity<MemberResponse> inviteMember(
            @PathVariable Long projectId,
           @RequestBody @Valid InviteMemberRequest request ){


        return ResponseEntity.status(HttpStatus.CREATED).body(
                projectMemberService.inviteMember(projectId,request)
        );

    }
    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @RequestBody @Valid UpdateMemberRoleRequest request
    )
    {

     return ResponseEntity.ok(projectMemberService.updateMemberRole(projectId,memberId,request));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> deleteMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long memberId
    )
    {

        projectMemberService.removeProjectMember(projectId,memberId);
       return ResponseEntity.noContent().build();
    }
}
