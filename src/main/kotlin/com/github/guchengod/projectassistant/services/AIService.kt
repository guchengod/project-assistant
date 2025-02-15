package com.github.guchengod.projectassistant.services

interface AIService {
    fun fetchModels(): List<Model>
    fun sendMessage(messages: List<ChatMessage>, onChunk: (String) -> Unit)
    fun fetchBalance(): BalanceResponse
} 