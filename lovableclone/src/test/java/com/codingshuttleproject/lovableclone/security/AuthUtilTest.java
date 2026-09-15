package com.codingshuttleproject.lovableclone.security;

import com.codingshuttleproject.lovableclone.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUtilTest {

    private AuthUtil authUtil;

    @BeforeEach
    void setUp() {
        authUtil = new AuthUtil();
        ReflectionTestUtils.setField(authUtil, "jwtSecretKey", "test-secret-key-for-jwt-signing-purposes-only-0123456789");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateAndVerifyAccessToken_roundTripsUserIdAndUsername() {
        User user = User.builder()
                .id(42L)
                .username("jane.doe")
                .build();

        String token = authUtil.generateAccessToken(user);
        JwtUserPrincipal principal = authUtil.verifyAccessToken(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("jane.doe");
    }

    @Test
    void getCurrentUserId_returnsIdFromAuthenticatedPrincipal() {
        JwtUserPrincipal principal = new JwtUserPrincipal(7L, "someone", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(authUtil.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserId_throwsWhenNoAuthenticationPresent() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authUtil.getCurrentUserId())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
