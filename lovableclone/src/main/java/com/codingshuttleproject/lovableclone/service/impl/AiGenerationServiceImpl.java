package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.service.AiGenerationService;
import reactor.core.publisher.Flux;

public class AiGenerationServiceImpl implements AiGenerationService {
    @Override
    public Flux<String> streamResponse(String message, Long projectId) {
        return null;
    }
}
