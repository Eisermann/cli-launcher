package com.github.eisermann.geminilauncher.mcp

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides a custom file editor for GeminiDiffVirtualFile.
 * When a GeminiDiffVirtualFile is opened, this provider creates a GeminiDiffFileEditor.
 */
class GeminiDiffFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = file is GeminiDiffVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return GeminiDiffFileEditor(project, file as GeminiDiffVirtualFile)
    }

    override fun getEditorTypeId(): String = "gemini-diff-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
