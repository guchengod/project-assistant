package com.github.guchengod.projectassistant.code.analyzer

import com.github.guchengod.projectassistant.code.model.ProjectInfo
import java.nio.file.Path

interface ProjectAnalyzer {
    fun canAnalyze(projectPath: Path): Boolean
    fun analyze(projectPath: Path): ProjectInfo
    fun generatePrompt(projectInfo: ProjectInfo): String
}

abstract class BaseProjectAnalyzer : ProjectAnalyzer {
    protected fun countFileTypes(projectPath: Path): Map<String, Int> {
        val fileCount = mutableMapOf<String, Int>()
        projectPath.toFile().walk()
            .filter { it.isFile }
            .forEach { file ->
                val extension = file.extension.lowercase()
                fileCount[extension] = fileCount.getOrDefault(extension, 0) + 1
            }
        return fileCount
    }

    protected fun readFileContent(filePath: Path): String {
        return filePath.toFile().readText()
    }

    protected fun findFiles(projectPath: Path, extension: String): List<Path> {
        return projectPath.toFile()
            .walk()
            .filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            .map { it.toPath() }
            .toList()
    }
}
