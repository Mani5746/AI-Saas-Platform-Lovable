package com.codingshuttleproject.lovableclone.service;


import com.codingshuttleproject.lovableclone.dto.chat.ChatResponse;

import java.util.List;

public interface ChatService {
    List<ChatResponse> getProjectChatHistory(Long projectId);
}
