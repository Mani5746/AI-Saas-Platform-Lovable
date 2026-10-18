package com.codingshuttleproject.lovableclone.dto.chat;

import com.codingshuttleproject.lovableclone.entity.ChatEvent;
import com.codingshuttleproject.lovableclone.entity.ChatSession;
import com.codingshuttleproject.lovableclone.enums.MessageRole;

import java.time.Instant;
import java.util.List;

public record ChatResponse (
        Long id,
        ChatSession chatSession,
        MessageRole role,
        List<ChatEvent> events,
        String content,
        Integer tokenUsed,
        Instant createdAt
){
}
