package com.codingshuttleproject.lovableclone.mapper;

import com.codingshuttleproject.lovableclone.dto.chat.ChatResponse;
import com.codingshuttleproject.lovableclone.entity.ChatMessage;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMapper {
    List<ChatResponse> fromListOfChatMessage(List<ChatMessage> chatMessageList);
}
