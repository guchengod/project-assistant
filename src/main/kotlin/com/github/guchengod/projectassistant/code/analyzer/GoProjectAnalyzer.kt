package com.github.guchengod.projectassistant.code.analyzer

import com.github.guchengod.projectassistant.code.model.ProjectInfo
import com.github.guchengod.projectassistant.code.model.ProjectType
import java.nio.file.Path
import java.io.File

class GoProjectAnalyzer : BaseProjectAnalyzer() {
    override fun canAnalyze(projectPath: Path): Boolean {
        return File(projectPath.toFile(), "go.mod").exists()
    }

    override fun analyze(projectPath: Path): ProjectInfo {
        val dependencies = mutableListOf<String>()
        val frameworks = mutableListOf<String>()
        
        // 分析 go.mod 文件
        val goModFile = File(projectPath.toFile(), "go.mod")
        if (goModFile.exists()) {
            try {
                val content = goModFile.readText()
                val requirePattern = "require\\s+([^\\s]+)\\s+([^\\s]+)".toRegex()
                requirePattern.findAll(content).forEach { match ->
                    val (module, version) = match.destructured
                    dependencies.add("$module:$version")
                    
                    // 检测常见框架
                    when {
                        module.contains("gin-gonic/gin") -> frameworks.add("Gin")
                        module.contains("gorilla/mux") -> frameworks.add("Gorilla Mux")
                        module.contains("go-chi/chi") -> frameworks.add("Chi")
                        module.contains("labstack/echo") -> frameworks.add("Echo")
                        module.contains("gofiber/fiber") -> frameworks.add("Fiber")
                        module.contains("gorm.io/gorm") -> frameworks.add("GORM")
                    }
                }
            } catch (e: Exception) {
                // 解析失败时继续
            }
        }

        val sourceFiles = countFileTypes(projectPath)
        val mainPackages = findMainPackages(projectPath)

        return ProjectInfo(
            projectType = ProjectType.GO,
            name = projectPath.fileName.toString(),
            rootPath = projectPath.toString(),
            dependencies = dependencies,
            frameworks = frameworks,
            mainLanguages = listOf("Go"),
            buildTools = listOf("Go Modules"),
            sourceFiles = sourceFiles,
            additionalInfo = mapOf(
                "hasTests" to hasTests(projectPath).toString(),
                "mainPackages" to mainPackages.joinToString(", "),
                "hasDocumentation" to hasDocumentation(projectPath).toString()
            )
        )
    }

    override fun generatePrompt(projectInfo: ProjectInfo): String {
        return buildString {
            appendLine("你现在是一个专业的Go开发助手，正在分析一个Go项目。项目信息如下：")
            appendLine("项目名称：${projectInfo.name}")
            if (projectInfo.frameworks.isNotEmpty()) {
                appendLine("使用的框架：${projectInfo.frameworks.joinToString(", ")}")
            }
            appendLine("主要依赖：${projectInfo.dependencies.take(5).joinToString(", ")}")
            if (projectInfo.additionalInfo["mainPackages"]?.isNotEmpty() == true) {
                appendLine("主要包：${projectInfo.additionalInfo["mainPackages"]}")
            }
            appendLine()
            appendLine("你应该：")
            appendLine("1. 熟悉Go语言的最佳实践和惯用写法")
            appendLine("2. 了解Go的并发模型和错误处理机制")
            appendLine("3. 能够提供性能优化和代码改进建议")
            
            if (projectInfo.frameworks.isNotEmpty()) {
                appendLine("4. 特别注意${projectInfo.frameworks.first()}框架的最佳实践")
            }
            if (projectInfo.additionalInfo["hasTests"] == "true") {
                appendLine("5. 关注Go的测试实践和测试覆盖率")
            }
            appendLine("6. 遵循Go的代码规范和项目结构建议")
        }
    }

    private fun findMainPackages(projectPath: Path): List<String> {
        val mainPackages = mutableListOf<String>()
        projectPath.toFile().walk()
            .filter { it.isFile && it.name == "main.go" }
            .forEach { file ->
                try {
                    val content = file.readText()
                    val packagePattern = "package\\s+([^\\s]+)".toRegex()
                    packagePattern.find(content)?.let { match ->
                        mainPackages.add(match.groupValues[1])
                    }
                } catch (e: Exception) {
                    // 解析失败时继续
                }
            }
        return mainPackages
    }

    private fun hasTests(projectPath: Path): Boolean {
        return projectPath.toFile().walk()
            .any { file ->
                file.isFile && file.name.endsWith("_test.go")
            }
    }

    private fun hasDocumentation(projectPath: Path): Boolean {
        return File(projectPath.toFile(), "README.md").exists() ||
               projectPath.toFile().walk()
                   .any { it.isFile && it.name == "doc.go" }
    }
}
