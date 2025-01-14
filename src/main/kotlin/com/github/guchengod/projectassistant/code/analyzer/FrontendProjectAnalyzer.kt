package com.github.guchengod.projectassistant.code.analyzer

import com.github.guchengod.projectassistant.code.model.ProjectInfo
import com.github.guchengod.projectassistant.code.model.ProjectType
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Path
import java.io.File

class FrontendProjectAnalyzer : BaseProjectAnalyzer() {
    private val gson = Gson()

    override fun canAnalyze(projectPath: Path): Boolean {
        return File(projectPath.toFile(), "package.json").exists()
    }

    override fun analyze(projectPath: Path): ProjectInfo {
        val packageJson = File(projectPath.toFile(), "package.json")
        val dependencies = mutableListOf<String>()
        val frameworks = mutableListOf<String>()
        val buildTools = mutableListOf<String>()
        
        try {
            val packageData = gson.fromJson(packageJson.readText(), JsonObject::class.java)
            
            // 分析依赖
            listOf("dependencies", "devDependencies").forEach { depType ->
                packageData.getAsJsonObject(depType)?.entrySet()?.forEach { (name, version) ->
                    dependencies.add("$name:${version.asString}")
                    
                    // 检测框架和构建工具
                    when (name) {
                        "react" -> frameworks.add("React")
                        "vue" -> frameworks.add("Vue.js")
                        "angular" -> frameworks.add("Angular")
                        "next" -> frameworks.add("Next.js")
                        "nuxt" -> frameworks.add("Nuxt.js")
                        "webpack" -> buildTools.add("Webpack")
                        "vite" -> buildTools.add("Vite")
                        "parcel" -> buildTools.add("Parcel")
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败时继续
        }

        val sourceFiles = countFileTypes(projectPath)
        val projectType = determineProjectType(frameworks, sourceFiles)
        val mainLanguages = determineMainLanguages(sourceFiles)

        return ProjectInfo(
            projectType = projectType,
            name = projectPath.fileName.toString(),
            rootPath = projectPath.toString(),
            dependencies = dependencies,
            frameworks = frameworks,
            mainLanguages = mainLanguages,
            buildTools = buildTools,
            sourceFiles = sourceFiles,
            additionalInfo = mapOf(
                "hasTests" to hasTests(projectPath).toString(),
                "hasTypeScript" to (sourceFiles["ts"] ?: 0 > 0).toString(),
                "hasStyling" to hasStylingFramework(dependencies).toString()
            )
        )
    }

    override fun generatePrompt(projectInfo: ProjectInfo): String {
        return buildString {
            appendLine("你现在是一个专业的前端开发助手，正在分析一个${projectInfo.frameworks.firstOrNull() ?: "前端"}项目。项目信息如下：")
            appendLine("项目名称：${projectInfo.name}")
            appendLine("项目类型：${projectInfo.projectType}")
            appendLine("构建工具：${projectInfo.buildTools.joinToString(", ")}")
            appendLine("主要框架：${projectInfo.frameworks.joinToString(", ")}")
            appendLine("使用语言：${projectInfo.mainLanguages.joinToString(", ")}")
            appendLine()
            appendLine("你应该：")
            appendLine("1. 熟悉${projectInfo.frameworks.firstOrNull() ?: "前端"}框架的最佳实践")
            appendLine("2. 了解现代前端开发工具和技术栈")
            appendLine("3. 能够提供代码优化和性能改进建议")
            
            if (projectInfo.additionalInfo["hasTypeScript"] == "true") {
                appendLine("4. 注意TypeScript类型安全和接口定义")
            }
            if (projectInfo.additionalInfo["hasTests"] == "true") {
                appendLine("5. 关注前端测试实践和测试覆盖率")
            }
            if (projectInfo.additionalInfo["hasStyling"] == "true") {
                appendLine("6. 注意CSS架构和样式最佳实践")
            }
        }
    }

    private fun determineProjectType(frameworks: List<String>, sourceFiles: Map<String, Int>): ProjectType {
        return when {
            frameworks.any { it.contains("React", ignoreCase = true) } -> ProjectType.REACT
            frameworks.any { it.contains("Vue", ignoreCase = true) } -> ProjectType.VUE
            sourceFiles["ts"]?.let { it > 0 } == true -> ProjectType.TYPESCRIPT
            else -> ProjectType.JAVASCRIPT
        }
    }

    private fun determineMainLanguages(sourceFiles: Map<String, Int>): List<String> {
        return buildList {
            if ((sourceFiles["js"] ?: 0) > 0) add("JavaScript")
            if ((sourceFiles["ts"] ?: 0) > 0) add("TypeScript")
            if ((sourceFiles["jsx"] ?: 0) > 0) add("JSX")
            if ((sourceFiles["tsx"] ?: 0) > 0) add("TSX")
            if ((sourceFiles["vue"] ?: 0) > 0) add("Vue")
        }
    }

    private fun hasTests(projectPath: Path): Boolean {
        return projectPath.toFile().walk()
            .any { file ->
                file.isFile && (
                    file.name.endsWith(".test.js") ||
                    file.name.endsWith(".test.ts") ||
                    file.name.endsWith(".spec.js") ||
                    file.name.endsWith(".spec.ts")
                )
            }
    }

    private fun hasStylingFramework(dependencies: List<String>): Boolean {
        return dependencies.any { dep ->
            dep.contains("sass") || dep.contains("less") || 
            dep.contains("styled-components") || dep.contains("tailwind") ||
            dep.contains("emotion") || dep.contains("material-ui") ||
            dep.contains("antd") || dep.contains("chakra")
        }
    }
}
