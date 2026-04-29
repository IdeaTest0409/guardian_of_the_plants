package com.example.smartphonapptest001.data.repository

import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun complete(messages: List<ChatMessage>, settings: AppSettings): String
    fun stream(messages: List<ChatMessage>, settings: AppSettings): Flow<String>
}
