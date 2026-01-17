package com.github.eisermann.opencodelauncher.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class DiffToolService {
    private val logger = logger<DiffToolService>()

    // Track open diff tabs
    private val openDiffTabs = ConcurrentHashMap<String, OpenCodeDiffVirtualFile>()

    /**
     * Opens a diff view in the IDE. Returns immediately.
     * User accepts/rejects through OpenCode CLI terminal.
     */
    fun openDiff(filePath: String, newContent: String): JsonObject {
        logger.info("openDiff called for: $filePath")

        val result = JsonObject()
        val contentArray = JsonArray()
        result.add("content", contentArray)

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (virtualFile == null) {
            result.addProperty("isError", true)
            val errorContent = JsonObject()
            errorContent.addProperty("type", "text")
            errorContent.addProperty("text", "File not found: $filePath")
            contentArray.add(errorContent)
            return result
        }

        val project = ProjectManager.getInstance().openProjects.find {
            filePath.startsWith(it.basePath ?: "")
        }

        if (project == null) {
            result.addProperty("isError", true)
            val errorContent = JsonObject()
            errorContent.addProperty("type", "text")
            errorContent.addProperty("text", "No open project found for file: $filePath")
            contentArray.add(errorContent)
            return result
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                // Close any existing diff tab for this file
                openDiffTabs[filePath]?.let { existingDiff ->
                    runCatching { FileEditorManager.getInstance(project).closeFile(existingDiff) }
                }

                // Create and open new diff
                val diffVirtualFile = OpenCodeDiffVirtualFile(
                    targetFile = virtualFile,
                    proposedContent = newContent,
                    filePath = filePath
                )
                openDiffTabs[filePath] = diffVirtualFile

                FileEditorManager.getInstance(project).openFile(diffVirtualFile, true)
                logger.info("Opened diff editor tab for: $filePath")
            } catch (e: Exception) {
                logger.error("Failed to open diff", e)
            }
        }

        val textContent = JsonObject()
        textContent.addProperty("type", "text")
        textContent.addProperty("text", "Diff view opened for: $filePath")
        contentArray.add(textContent)
        result.addProperty("isError", false)

        return result
    }

    fun closeDiff(filePath: String): JsonObject {
        val result = JsonObject()
        val contentArray = JsonArray()
        val textContent = JsonObject()
        textContent.addProperty("type", "text")

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        val currentContent = if (virtualFile != null) {
            ReadAction.compute<String, Throwable> {
                FileDocumentManager.getInstance().getDocument(virtualFile)?.text ?: File(filePath).readText()
            }
        } else {
            ""
        }

        textContent.addProperty("text", currentContent)
        contentArray.add(textContent)
        result.add("content", contentArray)
        result.addProperty("isError", false)

        // Close the diff tab if it exists
        ApplicationManager.getApplication().invokeLater {
            val diffFile = openDiffTabs.remove(filePath) ?: return@invokeLater
            val project = ProjectManager.getInstance().openProjects.find {
                filePath.startsWith(it.basePath ?: "")
            } ?: return@invokeLater
            runCatching { FileEditorManager.getInstance(project).closeFile(diffFile) }
        }

        return result
    }
}
