package com.github.guchengod.projectassistant.code.analyzer

import com.github.guchengod.projectassistant.code.model.ProjectInfo
import com.github.guchengod.projectassistant.code.model.ProjectType
import com.github.guchengod.projectassistant.code.model.CodeChange
import com.github.guchengod.projectassistant.code.model.ChangeType
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

    fun analyzeCodeChange(oldContent: String?, newContent: String?, filePath: String): CodeChange {
        // 如果旧内容为空，说明是新增文件
        if (oldContent == null) {
            return analyzeAddedCode(newContent!!, filePath)
        }
        
        // 如果新内容为空，说明是删除文件
        if (newContent == null) {
            return analyzeDeletedCode(oldContent, filePath)
        }
        
        // 否则是修改文件
        return analyzeModifiedCode(oldContent, newContent, filePath)
    }
    
    private fun analyzeAddedCode(content: String, filePath: String): CodeChange {
        val description = buildString {
            append("新增了Java文件: ${File(filePath).name}\n")
            
            // 分析代码内容
            when {
                content.contains("@RestController") -> append("添加了新的REST控制器\n")
                content.contains("@Service") -> append("添加了新的服务类\n")
                content.contains("@Repository") -> append("添加了新的数据访问层\n")
                content.contains("@Entity") -> append("添加了新的实体类\n")
                content.contains("@Test") -> append("添加了新的测试类\n")
            }
            
            // 检查是否包含重要注解
            if (content.contains("@Autowired") || content.contains("@Inject")) {
                append("使用了依赖注入\n")
            }
        }
        
        val impact = buildString {
            append("影响分析：\n")
            when {
                content.contains("@RestController") -> {
                    append("- 新增了API接口，需要更新API文档\n")
                    append("- 需要考虑接口的安全性和权限控制\n")
                }
                content.contains("@Service") -> {
                    append("- 新增了业务逻辑，需要添加单元测试\n")
                    append("- 确保符合事务管理规范\n")
                }
                content.contains("@Repository") -> {
                    append("- 新增了数据库访问，需要注意SQL性能\n")
                    append("- 确保有适当的错误处理\n")
                }
                content.contains("@Entity") -> {
                    append("- 新增了数据库表，需要考虑数据库迁移\n")
                    append("- 检查索引设计是否合理\n")
                }
            }
        }
        
        val suggestions = mutableListOf<String>()
        // 根据代码内容提供建议
        if (content.contains("@RestController")) {
            suggestions.add("添加Swagger或OpenAPI文档注解")
            suggestions.add("实现统一的错误处理")
        }
        if (content.contains("@Service")) {
            suggestions.add("添加单元测试用例")
            suggestions.add("考虑使用接口来定义服务契约")
        }
        if (content.contains("@Repository")) {
            suggestions.add("添加数据库索引")
            suggestions.add("实现缓存机制")
        }
        if (!content.contains("@Test") && !filePath.contains("test")) {
            suggestions.add("为新增的代码编写单元测试")
        }
        
        return CodeChange(
            type = ChangeType.ADD,
            file = filePath,
            newContent = content,
            description = description,
            impact = impact,
            suggestions = suggestions
        )
    }
    
    private fun analyzeDeletedCode(content: String, filePath: String): CodeChange {
        val description = buildString {
            append("删除了Java文件: ${File(filePath).name}\n")
            
            // 分析被删除的代码
            when {
                content.contains("@RestController") -> append("删除了REST控制器\n")
                content.contains("@Service") -> append("删除了服务类\n")
                content.contains("@Repository") -> append("删除了数据访问层\n")
                content.contains("@Entity") -> append("删除了实体类\n")
                content.contains("@Test") -> append("删除了测试类\n")
            }
        }
        
        val impact = buildString {
            append("影响分析：\n")
            when {
                content.contains("@RestController") -> {
                    append("- API接口被移除，需要更新API文档\n")
                    append("- 检查是否有其他服务依赖这些接口\n")
                }
                content.contains("@Service") -> {
                    append("- 业务逻辑被移除，检查依赖此服务的其他组件\n")
                    append("- 相关的测试用例可能需要更新或删除\n")
                }
                content.contains("@Repository") -> {
                    append("- 数据访问层被移除，检查是否有遗留数据需要处理\n")
                    append("- 确保没有破坏数据完整性\n")
                }
                content.contains("@Entity") -> {
                    append("- 实体类被移除，需要考虑数据库迁移\n")
                    append("- 检查外键约束和关联表\n")
                }
            }
        }
        
        val suggestions = mutableListOf<String>()
        // 根据删除的内容提供建议
        if (content.contains("@RestController")) {
            suggestions.add("更新API文档，标记已删除的接口")
            suggestions.add("检查客户端代码是否需要更新")
        }
        if (content.contains("@Service")) {
            suggestions.add("检查并清理相关的配置和依赖")
            suggestions.add("更新或删除相关的测试用例")
        }
        if (content.contains("@Repository")) {
            suggestions.add("检查是否需要数据库迁移脚本")
            suggestions.add("更新数据访问层的文档")
        }
        if (content.contains("@Entity")) {
            suggestions.add("创建数据库迁移脚本")
            suggestions.add("检查并更新相关的查询和报表")
        }
        
        return CodeChange(
            type = ChangeType.DELETE,
            file = filePath,
            oldContent = content,
            description = description,
            impact = impact,
            suggestions = suggestions
        )
    }
    
    private fun analyzeModifiedCode(oldContent: String, newContent: String, filePath: String): CodeChange {
        val description = buildString {
            append("修改了Java文件: ${File(filePath).name}\n")
            
            // 分析修改的内容
            val oldAnnotations = extractAnnotations(oldContent)
            val newAnnotations = extractAnnotations(newContent)
            
            // 检查注解的变化
            val addedAnnotations = newAnnotations - oldAnnotations
            val removedAnnotations = oldAnnotations - newAnnotations
            
            if (addedAnnotations.isNotEmpty()) {
                append("添加了注解: ${addedAnnotations.joinToString(", ")}\n")
            }
            if (removedAnnotations.isNotEmpty()) {
                append("移除了注解: ${removedAnnotations.joinToString(", ")}\n")
            }
            
            // 检查方法的变化
            val oldMethods = extractMethods(oldContent)
            val newMethods = extractMethods(newContent)
            
            val addedMethods = newMethods - oldMethods
            val removedMethods = oldMethods - newMethods
            
            if (addedMethods.isNotEmpty()) {
                append("添加了方法: ${addedMethods.joinToString(", ")}\n")
            }
            if (removedMethods.isNotEmpty()) {
                append("移除了方法: ${removedMethods.joinToString(", ")}\n")
            }
        }
        
        val impact = buildString {
            append("影响分析：\n")
            
            // 分析修改的影响
            if (oldContent.contains("@RestController") || newContent.contains("@RestController")) {
                append("- API接口发生变化，需要更新API文档\n")
                append("- 检查是否影响现有客户端\n")
            }
            if (oldContent.contains("@Service") || newContent.contains("@Service")) {
                append("- 业务逻辑发生变化，需要更新相关测试\n")
                append("- 检查是否影响其他依赖服务\n")
            }
            if (oldContent.contains("@Repository") || newContent.contains("@Repository")) {
                append("- 数据访问层发生变化，检查SQL性能\n")
                append("- 确保数据一致性\n")
            }
            if (oldContent.contains("@Entity") || newContent.contains("@Entity")) {
                append("- 实体类发生变化，可能需要数据库迁移\n")
                append("- 检查ORM映射是否正确\n")
            }
        }
        
        val suggestions = mutableListOf<String>()
        // 根据修改的内容提供建议
        if (oldContent.contains("@RestController") || newContent.contains("@RestController")) {
            suggestions.add("更新API文档")
            suggestions.add("添加接口版本控制")
        }
        if (oldContent.contains("@Service") || newContent.contains("@Service")) {
            suggestions.add("更新单元测试")
            suggestions.add("检查事务边界")
        }
        if (oldContent.contains("@Repository") || newContent.contains("@Repository")) {
            suggestions.add("检查SQL性能")
            suggestions.add("考虑添加缓存")
        }
        if (oldContent.contains("@Entity") || newContent.contains("@Entity")) {
            suggestions.add("创建数据库迁移脚本")
            suggestions.add("更新数据库文档")
        }
        if (!newContent.contains("@Test") && !filePath.contains("test")) {
            suggestions.add("更新或添加相关的单元测试")
        }
        
        return CodeChange(
            type = ChangeType.MODIFY,
            file = filePath,
            oldContent = oldContent,
            newContent = newContent,
            description = description,
            impact = impact,
            suggestions = suggestions
        )
    }
    
    private fun extractAnnotations(content: String): Set<String> {
        val annotationPattern = "@([A-Za-z0-9_]+)".toRegex()
        return annotationPattern.findAll(content)
            .map { it.groupValues[1] }
            .toSet()
    }
    
    private fun extractMethods(content: String): Set<String> {
        val methodPattern = "(?:public|private|protected)\\s+(?:static\\s+)?[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)".toRegex()
        return methodPattern.findAll(content)
            .map { it.groupValues[1] }
            .toSet()
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
