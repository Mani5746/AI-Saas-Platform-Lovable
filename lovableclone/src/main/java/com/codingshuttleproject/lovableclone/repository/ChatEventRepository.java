package com.codingshuttleproject.lovableclone.repository;

import com.codingshuttleproject.lovableclone.entity.ChatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
}
