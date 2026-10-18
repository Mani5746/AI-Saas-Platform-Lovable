package com.codingshuttleproject.lovableclone.controllers;

import com.codingshuttleproject.lovableclone.dto.chat.ChatRequest;
import com.codingshuttleproject.lovableclone.dto.chat.ChatResponse;
import com.codingshuttleproject.lovableclone.dto.chat.StreamResponse;
import com.codingshuttleproject.lovableclone.entity.ChatMessage;
import com.codingshuttleproject.lovableclone.service.AiGenerationService;
import com.codingshuttleproject.lovableclone.service.ChatService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private final AiGenerationService aiGenerationService;
    private final ChatService chatService;

    @PostMapping(value="/api/chat/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamResponse>> streamChat(
            @RequestBody ChatRequest request){
return aiGenerationService.streamResponse(request.message(),request.projectId())
        .map(data-> ServerSentEvent.<StreamResponse>builder()
                .data(data)
                .build());
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<List<ChatResponse>>getChatHistory
            (@PathVariable("projectId") Long projectId){
        return  ResponseEntity.ok(chatService.getProjectChatHistory(projectId));
    }
}
