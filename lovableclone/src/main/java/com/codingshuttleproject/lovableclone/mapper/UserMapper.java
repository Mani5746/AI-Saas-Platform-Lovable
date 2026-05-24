package com.codingshuttleproject.lovableclone.mapper;

import com.codingshuttleproject.lovableclone.dto.auth.SignupRequest;
import com.codingshuttleproject.lovableclone.dto.auth.UserProfileResponse;
import com.codingshuttleproject.lovableclone.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toEntity(SignupRequest signupRequest);

    UserProfileResponse toUserProfileResponse(User user);
}
