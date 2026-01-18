package com.github.eisermann.opencodelink.mcp

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Custom file editor that shows a diff view using DiffManager.createRequestPanel().
 * User accepts/rejects through OpenCode CLI terminal.
 */
internal class OpenCodeDiffFileEditor(
    private val project: Project,
    private val file: OpenCodeDiffVirtualFile
) : UserDataHolderBase(), FileEditor, Disposable {

    private val mainPanel = JPanel(BorderLayout())
    private var diffPanel: DiffRequestPanel? = null

    init {
        createUI()
    }

    private fun createUI() {
        // Get current file content
        val currentText = ReadAction.compute<String, Throwable> {
            FileDocumentManager.getInstance().getDocument(file.targetFile)?.text ?: ""
        }

        // Create diff content
        val contentFactory = DiffContentFactory.getInstance()
        val currentContent = contentFactory.create(project, currentText, file.targetFile)
        val proposedContent = contentFactory.create(file.proposedContent, file.targetFile)

        // Create diff request
        val request = SimpleDiffRequest(
            "Review Changes: ${file.targetFile.name}",
            currentContent,
            proposedContent,
            "Current",
            "Proposed (OpenCode)"
        )

        // Create embedded diff panel using DiffManager
        diffPanel = DiffManager.getInstance().createRequestPanel(project, this, null)
        diffPanel?.setRequest(request)

        // Just the diff panel, no buttons
        mainPanel.add(diffPanel?.component ?: JPanel(), BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = mainPanel

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel?.preferredFocusedComponent

    override fun getName(): String = "OpenCode Diff: ${file.targetFile.name}"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = !project.isDisposed && file.targetFile.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        diffPanel?.let { Disposer.dispose(it) }
    }

    override fun getFile() = file
}
