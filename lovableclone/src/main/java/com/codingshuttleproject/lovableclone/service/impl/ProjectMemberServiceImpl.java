package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.dto.member.InviteMemberRequest;
import com.codingshuttleproject.lovableclone.dto.member.MemberResponse;
import com.codingshuttleproject.lovableclone.dto.member.UpdateMemberRoleRequest;
import com.codingshuttleproject.lovableclone.entity.Project;
import com.codingshuttleproject.lovableclone.entity.ProjectMember;
import com.codingshuttleproject.lovableclone.entity.ProjectMemberId;
import com.codingshuttleproject.lovableclone.entity.User;
import com.codingshuttleproject.lovableclone.mapper.ProjectMemberMapper;
import com.codingshuttleproject.lovableclone.repository.ProjectMemberRepository;
import com.codingshuttleproject.lovableclone.repository.ProjectRepository;
import com.codingshuttleproject.lovableclone.repository.UserRepository;
import com.codingshuttleproject.lovableclone.service.ProjectMemberService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {
    ProjectMemberRepository projectMemberRepository;
    ProjectMemberMapper projectMemberMapper;
    ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Override
    public List<MemberResponse> getProjectMembers(Long projectId, Long userId) {
        Project project = getAccessibleProjectById(projectId, userId);
        List<MemberResponse> memberResponseList =
                projectMemberRepository.findByIdProjectId(projectId)
                        .stream()
                        .map(projectMemberMapper::toProjectMemeberResponseFromMember)
                        .toList();

        return memberResponseList;
    }

    @Override
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request, Long userId) {

        Project project=getAccessibleProjectById(projectId, userId);
        User invitee= userRepository.findByEmail(request.email()).orElseThrow();

        if(invitee.getId().equals(userId)){
            throw new RuntimeException("Cannot Invite yourself");
        }
        ProjectMemberId projectMemberId= new ProjectMemberId(projectId, invitee.getId());

        if(projectMemberRepository.existsById(projectMemberId)){
            throw new RuntimeException("Cannot invite once again");
        }

        ProjectMember member=ProjectMember.builder()
                .id(projectMemberId)
                .project(project)
                .user(invitee)
                .projectRole(request.role())
                .invitedAt(Instant.now())
                .invitedAt(Instant.now())
                .build();
         projectMemberRepository.save(member);
         return projectMemberMapper.toProjectMemeberResponseFromMember(member);
    }

    @Override
    public MemberResponse updateMemberRole(Long projectId, Long memberId, @Valid UpdateMemberRoleRequest request, Long userId) {
        Project project=getAccessibleProjectById(projectId, userId);
        ProjectMemberId projectMemberId= new ProjectMemberId(projectId,memberId);
        ProjectMember projectMember=projectMemberRepository.findById(projectMemberId).orElseThrow();
        projectMember.setProjectRole(request.role());
        projectMemberRepository.save(projectMember);
        return projectMemberMapper.toProjectMemeberResponseFromMember(projectMember);

    }

    @Override
    public void removeProjectMember(Long projectId, Long memberId, Long userId) {
        Project project=getAccessibleProjectById(projectId, userId);
        ProjectMemberId projectMemberId= new ProjectMemberId(projectId,memberId);

        if(!projectMemberRepository.existsById(projectMemberId)){
            throw new RuntimeException("Project Member Does not Found");
        }
        projectMemberRepository.deleteById(projectMemberId);

    }

    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId).orElseThrow();
    }
}
