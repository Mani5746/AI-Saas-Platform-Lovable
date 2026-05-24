package com.codingshuttleproject.lovableclone.controllers;

import com.codingshuttleproject.lovableclone.dto.auth.AuthResponse;
import com.codingshuttleproject.lovableclone.dto.auth.LoginRequest;
import com.codingshuttleproject.lovableclone.dto.auth.SignupRequest;
import com.codingshuttleproject.lovableclone.dto.auth.UserProfileResponse;
import com.codingshuttleproject.lovableclone.service.AuthService;
import com.codingshuttleproject.lovableclone.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@FieldDefaults(makeFinal = true, level= AccessLevel.PRIVATE)


// This will make all the fields inside class as private and final
public class AuthController {

     AuthService authService;
     UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request){
        return ResponseEntity.ok(authService.signup(request));

    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request){
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(){

        Long userId=1L;
        return ResponseEntity.ok(userService.getProfile(userId));
    }
}
