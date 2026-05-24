package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.dto.auth.AuthResponse;
import com.codingshuttleproject.lovableclone.dto.auth.LoginRequest;
import com.codingshuttleproject.lovableclone.dto.auth.SignupRequest;
import com.codingshuttleproject.lovableclone.entity.User;
import com.codingshuttleproject.lovableclone.errors.BadRequestException;
import com.codingshuttleproject.lovableclone.mapper.UserMapper;
import com.codingshuttleproject.lovableclone.repository.UserRepository;
import com.codingshuttleproject.lovableclone.service.AuthService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    @Override
    public AuthResponse signup(SignupRequest request) {
        userRepository.findByUsername(request.username()).ifPresent(user->{
                throw new BadRequestException("User already exists with username:"+request.username());
        });

        User user = userMapper.toEntity(request);
       user.setPassword(passwordEncoder.encode(request.password()));
       userRepository.save(user);
        return new AuthResponse("dummy",userMapper.toUserProfileResponse(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return null;
    }
}
