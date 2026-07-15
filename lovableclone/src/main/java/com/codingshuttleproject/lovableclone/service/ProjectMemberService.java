package com.codingshuttleproject.lovableclone.service;

import com.codingshuttleproject.lovableclone.dto.member.InviteMemberRequest;
import com.codingshuttleproject.lovableclone.dto.member.MemberResponse;
import com.codingshuttleproject.lovableclone.dto.member.UpdateMemberRoleRequest;
import jakarta.validation.Valid;

import java.util.List;

public interface ProjectMemberService {
    List<MemberResponse> getProjectMembers(Long projectId);

    MemberResponse inviteMember(Long projectId, InviteMemberRequest request);

     MemberResponse updateMemberRole(Long projectId, Long memberId, @Valid UpdateMemberRoleRequest request);

    void removeProjectMember(Long projectId, Long memberId);
}
