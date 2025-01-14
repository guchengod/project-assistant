package com.github.guchengod.projectassistant.code.model

data class ProjectInfo(
    val projectType: ProjectType,
    val name: String,
    val rootPath: String,
    val dependencies: List<String>,
    val frameworks: List<String>,
    val mainLanguages: List<String>,
    val buildTools: List<String>,
    val sourceFiles: Map<String, Int>, // 文件类型及其数量
    val additionalInfo: Map<String, String> // 其他特定项目类型的信息
)

enum class ProjectType {
    JAVA,
    KOTLIN,
    JAVASCRIPT,
    TYPESCRIPT,
    REACT,
    VUE,
    GO,
    UNKNOWN
}
