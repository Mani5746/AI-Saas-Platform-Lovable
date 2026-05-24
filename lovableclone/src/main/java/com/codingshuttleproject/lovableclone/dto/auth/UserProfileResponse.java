package com.codingshuttleproject.lovableclone.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String name
) {
}
