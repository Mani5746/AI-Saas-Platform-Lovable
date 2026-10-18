package com.codingshuttleproject.lovableclone.service;
import com.codingshuttleproject.lovableclone.dto.chat.StreamResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public interface AiGenerationService {
    Flux<StreamResponse> streamResponse(String message, Long projectId);
}
