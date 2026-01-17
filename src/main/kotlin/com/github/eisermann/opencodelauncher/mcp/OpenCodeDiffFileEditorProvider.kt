package com.github.eisermann.opencodelauncher.mcp

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides a custom file editor for OpenCodeDiffVirtualFile.
 * When an OpenCodeDiffVirtualFile is opened, this provider creates an OpenCodeDiffFileEditor.
 */
class OpenCodeDiffFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = file is OpenCodeDiffVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return OpenCodeDiffFileEditor(project, file as OpenCodeDiffVirtualFile)
    }

    override fun getEditorTypeId(): String = "opencode-diff-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
