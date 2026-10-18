package com.codingshuttleproject.lovableclone.service.impl;

import com.codingshuttleproject.lovableclone.dto.chat.ChatResponse;
import com.codingshuttleproject.lovableclone.entity.ChatMessage;
import com.codingshuttleproject.lovableclone.entity.ChatSession;
import com.codingshuttleproject.lovableclone.entity.ChatSessionId;
import com.codingshuttleproject.lovableclone.mapper.ChatMapper;
import com.codingshuttleproject.lovableclone.repository.ChatMessageRepository;
import com.codingshuttleproject.lovableclone.repository.ChatSessionRepository;
import com.codingshuttleproject.lovableclone.security.AuthUtil;
import com.codingshuttleproject.lovableclone.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final AuthUtil authUtil;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMapper chatMapper;

    @Override
    public List<ChatResponse> getProjectChatHistory(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        ChatSession chatSession=chatSessionRepository.getReferenceById(
                new ChatSessionId(projectId,userId)
        );

        List<ChatMessage>chatMessageList=chatMessageRepository.findByChatSession(chatSession);
        return chatMapper.fromListOfChatMessage(chatMessageList);
    }
}
