package com.github.guchengod.projectassistant.code

import com.github.guchengod.projectassistant.code.analyzer.*
import com.github.guchengod.projectassistant.code.model.ProjectInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path

@Service
class ProjectAnalyzerService(private val project: Project) {
    private val analyzers = listOf(
        JavaProjectAnalyzer(),
        FrontendProjectAnalyzer(),
        GoProjectAnalyzer()
    )

    fun analyzeProject(): ProjectInfo? {
        val projectPath = Path(project.basePath ?: return null)
        
        // 找到合适的分析器
        val analyzer = analyzers.find { it.canAnalyze(projectPath) }
            ?: return null

        return try {
            analyzer.analyze(projectPath)
        } catch (e: Exception) {
            // 分析失败时返回null
            null
        }
    }

    fun generatePrompt(): String {
        val projectInfo = analyzeProject() ?: return DEFAULT_PROMPT
        return projectInfo.projectType.let { type ->
            analyzers.find { it.canAnalyze(Path(project.basePath ?: return DEFAULT_PROMPT)) }
                ?.generatePrompt(projectInfo)
                ?: DEFAULT_PROMPT
        }
    }

    companion object {
        var DEFAULT_PROMPT = """
            你是一个专业的编程助手，可以：
            1. 理解并分析代码
            2. 提供代码优化建议
            3. 帮助解决编程问题
            4. 遵循编程最佳实践
            5. 提供清晰的代码解释
        """.trimIndent()
    }
}
