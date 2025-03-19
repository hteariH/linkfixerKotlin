package com.mamoru.repository

import com.mamoru.entity.ChatSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.stereotype.Repository

@Repository
@EnableJpaRepositories
public interface ChatJpaRepository : JpaRepository<ChatSettings, Long> {
    fun findByChatId(chatId: Long): ChatSettings?
}

