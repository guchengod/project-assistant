package com.github.guchengod.projectassistant.code.model

enum class ChangeType {
    ADD, MODIFY, DELETE
}

data class CodeChange(
    val type: ChangeType,
    val file: String,
    val oldContent: String? = null,
    val newContent: String? = null,
    val description: String,
    val impact: String,
    val suggestions: List<String> = emptyList()
)
