package com.codingshuttleproject.lovableclone.errors;

import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex){
        ApiError apiError= new ApiError(HttpStatus.BAD_REQUEST, ex.getMessage());
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex){
        ApiError apiError= new ApiError(HttpStatus.NOT_FOUND, ex.getResourceName()+" with Id "+ ex.getResourceId()+" not found ");
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInputValidation(MethodArgumentNotValidException ex){
        List<ApiFieldError> errors=ex.getBindingResult().getFieldErrors().stream()
                .map(error->new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ApiError apiError= new ApiError(HttpStatus.BAD_REQUEST, "Input Validation Failed", errors);
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiError> handleUsernameNotFound(UsernameNotFoundException ex){
        ApiError apiError= new ApiError(HttpStatus.NOT_FOUND, "Username not found with username"+ex.getMessage());
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex){
        ApiError apiError= new ApiError(HttpStatus.UNAUTHORIZED, "Authentication failed: invalid credentials");
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiError> handleInvalidJwt(JwtException ex){
        ApiError apiError= new ApiError(HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token");
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex){
        ApiError apiError= new ApiError(HttpStatus.FORBIDDEN, "Access denied: you do not have permission to perform this action");
        log.error(apiError.toString(),ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }
}