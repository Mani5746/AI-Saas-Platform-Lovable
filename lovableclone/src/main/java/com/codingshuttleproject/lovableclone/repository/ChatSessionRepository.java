package com.codingshuttleproject.lovableclone.repository;

import com.codingshuttleproject.lovableclone.entity.ChatSession;
import com.codingshuttleproject.lovableclone.entity.ChatSessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, ChatSessionId> {
}
