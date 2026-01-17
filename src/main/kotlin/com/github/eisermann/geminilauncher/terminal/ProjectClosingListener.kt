package com.github.eisermann.geminilauncher.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * Listener that triggers cleanup of temporary Gemini scripts when a project is closing.
 */
class ProjectClosingListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        project.getService(TempScriptCleanupService::class.java)?.cleanup()
    }
}

