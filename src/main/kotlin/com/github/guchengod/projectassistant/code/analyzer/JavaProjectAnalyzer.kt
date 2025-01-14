package com.github.guchengod.projectassistant.code.analyzer

import com.github.guchengod.projectassistant.code.model.ProjectInfo
import com.github.guchengod.projectassistant.code.model.ProjectType
import org.w3c.dom.Document
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import java.io.File

class JavaProjectAnalyzer : BaseProjectAnalyzer() {
    override fun canAnalyze(projectPath: Path): Boolean {
        return File(projectPath.toFile(), "pom.xml").exists() ||
                File(projectPath.toFile(), "build.gradle").exists() ||
                File(projectPath.toFile(), "build.gradle.kts").exists()
    }

    override fun analyze(projectPath: Path): ProjectInfo {
        val dependencies = mutableListOf<String>()
        val frameworks = mutableListOf<String>()
        val buildTools = mutableListOf<String>()

        // 检查构建工具
        when {
            File(projectPath.toFile(), "pom.xml").exists() -> {
                buildTools.add("Maven")
                analyzeMavenProject(projectPath, dependencies, frameworks)
            }
            File(projectPath.toFile(), "build.gradle").exists() -> {
                buildTools.add("Gradle")
                analyzeGradleProject(projectPath, dependencies, frameworks)
            }
            File(projectPath.toFile(), "build.gradle.kts").exists() -> {
                buildTools.add("Gradle (Kotlin)")
                analyzeGradleProject(projectPath, dependencies, frameworks)
            }
        }

        val sourceFiles = countFileTypes(projectPath)
        val mainLanguages = determineMainLanguages(sourceFiles)

        return ProjectInfo(
            projectType = ProjectType.JAVA,
            name = projectPath.fileName.toString(),
            rootPath = projectPath.toString(),
            dependencies = dependencies,
            frameworks = frameworks,
            mainLanguages = mainLanguages,
            buildTools = buildTools,
            sourceFiles = sourceFiles,
            additionalInfo = mapOf(
                "hasTests" to hasTests(projectPath).toString(),
                "hasDocumentation" to hasDocumentation(projectPath).toString()
            )
        )
    }

    override fun generatePrompt(projectInfo: ProjectInfo): String {
        return buildString {
            appendLine("你现在是一个专业的Java开发助手，正在分析一个Java项目。项目信息如下：")
            appendLine("项目名称：${projectInfo.name}")
            appendLine("构建工具：${projectInfo.buildTools.joinToString(", ")}")
            appendLine("主要编程语言：${projectInfo.mainLanguages.joinToString(", ")}")
            appendLine("使用的框架：${projectInfo.frameworks.joinToString(", ")}")
            appendLine("主要依赖：${projectInfo.dependencies.take(5).joinToString(", ")}")
            appendLine()
            appendLine("你应该：")
            appendLine("1. 熟悉项目使用的框架和技术栈")
            appendLine("2. 了解Java最佳实践和设计模式")
            appendLine("3. 能够提供代码优化和重构建议")
            appendLine("4. 理解单元测试和集成测试")
            if (projectInfo.additionalInfo["hasTests"] == "true") {
                appendLine("5. 项目包含测试用例，注意维护测试代码质量")
            }
            if (projectInfo.frameworks.any { it.contains("Spring", ignoreCase = true) }) {
                appendLine("6. 特别注意Spring框架的最佳实践和设计原则")
            }
        }
    }

    private fun analyzeMavenProject(projectPath: Path, dependencies: MutableList<String>, frameworks: MutableList<String>) {
        val pomFile = File(projectPath.toFile(), "pom.xml")
        if (!pomFile.exists()) return

        try {
            val doc: Document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(pomFile)

            // 分析依赖
            val dependencyNodes = doc.getElementsByTagName("dependency")
            for (i in 0 until dependencyNodes.length) {
                val node = dependencyNodes.item(i)
                val groupId = node.childNodes.findNodeByName("groupId")?.textContent
                val artifactId = node.childNodes.findNodeByName("artifactId")?.textContent
                if (groupId != null && artifactId != null) {
                    dependencies.add("$groupId:$artifactId")
                    
                    // 检测常见框架
                    when {
                        artifactId.contains("spring-boot") -> frameworks.add("Spring Boot")
                        artifactId.contains("spring-framework") -> frameworks.add("Spring Framework")
                        artifactId.contains("hibernate") -> frameworks.add("Hibernate")
                        artifactId.contains("mybatis") -> frameworks.add("MyBatis")
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败时继续
        }
    }

    private fun analyzeGradleProject(projectPath: Path, dependencies: MutableList<String>, frameworks: MutableList<String>) {
        val gradleFile = File(projectPath.toFile(), "build.gradle")
        val gradleKtsFile = File(projectPath.toFile(), "build.gradle.kts")
        
        val buildFile = when {
            gradleFile.exists() -> gradleFile
            gradleKtsFile.exists() -> gradleKtsFile
            else -> return
        }

        try {
            val content = buildFile.readText()
            
            // 简单的文本分析来提取依赖信息
            val dependencyPattern = "implementation[\\s('\"]+([^):'\"]+)[:'\"]+([^):'\"]+)".toRegex()
            dependencyPattern.findAll(content).forEach { match ->
                val (group, name) = match.destructured
                dependencies.add("$group:$name")
                
                // 检测常见框架
                when {
                    name.contains("spring-boot") -> frameworks.add("Spring Boot")
                    name.contains("spring-framework") -> frameworks.add("Spring Framework")
                    name.contains("hibernate") -> frameworks.add("Hibernate")
                    name.contains("mybatis") -> frameworks.add("MyBatis")
                }
            }
        } catch (e: Exception) {
            // 解析失败时继续
        }
    }

    private fun determineMainLanguages(sourceFiles: Map<String, Int>): List<String> {
        return buildList {
            if (sourceFiles["java"] ?: 0 > 0) add("Java")
            if (sourceFiles["kt"] ?: 0 > 0) add("Kotlin")
            if (sourceFiles["groovy"] ?: 0 > 0) add("Groovy")
            if (sourceFiles["scala"] ?: 0 > 0) add("Scala")
        }
    }

    private fun hasTests(projectPath: Path): Boolean {
        return projectPath.toFile().walk()
            .any { file ->
                file.isFile && (
                    file.name.endsWith("Test.java") ||
                    file.name.endsWith("Tests.java") ||
                    file.name.endsWith("IT.java") ||
                    file.name.endsWith("Spec.groovy")
                )
            }
    }

    private fun hasDocumentation(projectPath: Path): Boolean {
        return File(projectPath.toFile(), "README.md").exists() ||
               projectPath.toFile().walk()
                   .any { it.isFile && it.extension == "md" }
    }
}

private fun org.w3c.dom.NodeList.findNodeByName(name: String): org.w3c.dom.Node? {
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeName == name) return node
    }
    return null
}
