package com.github.guchengod.projectassistant.services

interface AIService {
    fun fetchModels(): List<Model>
    fun sendMessage(message: String, onChunk: (String) -> Unit)
    fun fetchBalance(): BalanceResponse
} 