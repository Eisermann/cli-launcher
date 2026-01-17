package com.github.eisermann.opencodelauncher.startup

import com.github.eisermann.opencodelauncher.files.FileOpenService
import com.github.eisermann.opencodelauncher.http.HttpTriggerService
import com.github.eisermann.opencodelauncher.mcp.IdeContextTracker
import com.github.eisermann.opencodelauncher.mcp.McpServer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Startup activity that initializes essential services when a project is opened.
 *
 * This activity ensures that both FileOpenService and HttpTriggerService are properly
 * initialized and ready to handle file monitoring and HTTP requests.
 *
 * It also initializes the MCP Server and Context Tracker for the OpenCode CLI companion integration.
 *
 * @since 1.0.0
 */

class OpenCodeStartupActivity : StartupActivity {

    /**
     * Runs the startup activity for the given project.
     */
    override fun runActivity(project: Project) {
        // Explicitly initialize core services
        project.getService(FileOpenService::class.java)

        // HttpTriggerService is application-level (Legacy Launcher support)
        ApplicationManager.getApplication().getService(HttpTriggerService::class.java)

        // MCP Server (OpenCode companion integration)
        // Use synchronized block to prevent race condition when multiple projects open simultaneously
        synchronized(startLock) {
            if (!isMcpServerStarted) {
                val mcpServer = ApplicationManager.getApplication().service<McpServer>()
                mcpServer.start()
                isMcpServerStarted = true
            }
        }

        // Attach context tracker listeners for this project
        val contextTracker = ApplicationManager.getApplication().service<IdeContextTracker>()
        contextTracker.attachProjectListeners(project)
    }

    companion object {
        private val startLock = Any()
        @Volatile
        private var isMcpServerStarted = false
    }
}
