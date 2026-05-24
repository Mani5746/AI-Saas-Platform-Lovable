package com.codingshuttleproject.lovableclone.mapper;


import com.codingshuttleproject.lovableclone.dto.member.MemberResponse;
import com.codingshuttleproject.lovableclone.entity.ProjectMember;
import com.codingshuttleproject.lovableclone.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(target="userId", source = "id")
    @Mapping(target="projectRole", constant = "OWNER")
    MemberResponse toProjectMemberResponseFromOwner(User user);

    @Mapping(target="userId",source="user.id")
    @Mapping(target="username", source="user.username")
    @Mapping(target="name",source = "user.name")
    MemberResponse toProjectMemeberResponseFromMember(ProjectMember projectMember);

}
