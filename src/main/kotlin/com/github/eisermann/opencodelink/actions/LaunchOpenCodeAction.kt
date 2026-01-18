package com.github.eisermann.opencodelink.actions

import com.github.eisermann.opencodelink.http.HttpTriggerService
import com.github.eisermann.opencodelink.mcp.McpServer
import com.github.eisermann.opencodelink.settings.OpenCodeLinkSettings
import com.github.eisermann.opencodelink.settings.options.WinShell
import com.github.eisermann.opencodelink.terminal.OpenCodeTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import javax.swing.Icon

class LaunchOpenCodeAction : AnAction(DEFAULT_TEXT, DEFAULT_DESCRIPTION, null), DumbAware {

    companion object {
        private const val OPENCODE_COMMAND = "opencode"
        private const val NOTIFICATION_TITLE = "OpenCode Link"
        private const val DEFAULT_TEXT = "Launch OpenCode"
        private const val DEFAULT_DESCRIPTION = "Open an OpenCode terminal"
        private const val ACTIVE_TEXT = "Insert File Path into OpenCode"
        private const val ACTIVE_DESCRIPTION = "Send the current file path to the OpenCode terminal"
        private val DEFAULT_ICON = IconLoader.getIcon("/icons/opencode.svg", LaunchOpenCodeAction::class.java)
        private val ACTIVE_ICON = IconLoader.getIcon("/icons/opencode_active.svg", LaunchOpenCodeAction::class.java)
    }

    private val logger = logger<LaunchOpenCodeAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project context available for OpenCode launch")
            return
        }

        val terminalManager = project.service<OpenCodeTerminalManager>()
        if (terminalManager.isOpenCodeTerminalActive()) {
            performInsert(project, terminalManager)
            return
        }

        launchOpenCode(project, terminalManager)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val state = determineToolbarState(e.project)
        e.presentation.icon = state.icon
        e.presentation.text = state.text
        e.presentation.description = state.description
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun performInsert(project: Project, terminalManager: OpenCodeTerminalManager) {
        val insertText = resolveInsertText(project)
        if (insertText == null) {
            notify(project, "No active file to send to OpenCode", NotificationType.INFORMATION)
            return
        }

        if (!terminalManager.typeIntoActiveOpenCodeTerminal(insertText)) {
            notify(project, "Failed to send file path to OpenCode terminal", NotificationType.WARNING)
            return
        }

        logger.info("Sent active file path to OpenCode terminal: $insertText")
    }

    private fun launchOpenCode(project: Project, terminalManager: OpenCodeTerminalManager) {
        val baseDir = project.basePath ?: System.getProperty("user.home")
        logger.info("Launching OpenCode in directory: $baseDir")

        try {
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            // Note: port can be 0 if failed, handled gracefully by CLI usually or we warn

            val mcpServer = ApplicationManager.getApplication().service<McpServer>()
            val mcpPort = mcpServer.port

            val settings = service<OpenCodeLinkSettings>()
            val args = settings.getArgs(port, baseDir)
            val command = buildCommand(args, mcpPort, settings.state.winShell)

            terminalManager.launch(baseDir, command)
            logger.info("OpenCode command executed successfully: $command")
        } catch (t: Throwable) {
            logger.error("Failed to launch OpenCode", t)
            notify(project, "Failed to launch OpenCode: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun buildCommand(args: String, mcpPort: Int, winShell: WinShell): String {
        val openCodeCmd = buildString {
            append(OPENCODE_COMMAND)
            if (args.isNotBlank()) {
                append(' ')
                append(args)
            }
        }

        if (mcpPort <= 0) {
            return openCodeCmd
        }

        // Prepend environment variable for tie-breaking if possible
        return if (SystemInfo.isWindows) {
            if (winShell == WinShell.WSL) {
                 "export OPENCODE_CLI_IDE_SERVER_PORT=$mcpPort && $openCodeCmd"
            } else if (winShell == WinShell.POWERSHELL_73_PLUS || winShell == WinShell.POWERSHELL_LT_73) {
                 // PowerShell syntax: $env:VAR='val'; cmd
                 "\$env:OPENCODE_CLI_IDE_SERVER_PORT='$mcpPort'; $openCodeCmd"
            } else {
                // Default cmd syntax? IntelliJ terminal on Windows typically uses cmd.exe or PowerShell.
                // If it's cmd.exe: set VAR=val && cmd
                "set OPENCODE_CLI_IDE_SERVER_PORT=$mcpPort && $openCodeCmd"
            }
        } else {
            // Unix/Mac
            "export OPENCODE_CLI_IDE_SERVER_PORT=$mcpPort && $openCodeCmd"
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("OpenCodeLink")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        }.onFailure { error ->
            logger.error("Failed to show notification: $content", error)
        }
    }

    private fun resolveInsertText(project: Project): String? {
        val payload = resolveInsertPayload(project) ?: return null
        return buildString {
            append('@')
            append(payload.relativePath)
            payload.lineRange?.let { range ->
                append(':')
                append(range.start)
                range.end?.takeIf { it != range.start }?.let { end ->
                    append('-')
                    append(end)
                }
            }
            append(' ')
        }
    }

    private fun resolveInsertPayload(project: Project): InsertPayload? {
        val editorManager = FileEditorManager.getInstance(project)
        val file = editorManager.selectedFiles.firstOrNull() ?: return null
        val rawPath = file.canonicalPath ?: file.presentableUrl ?: file.path
        if (rawPath.isNullOrBlank()) {
            return null
        }

        val relativePath = project.basePath?.let { basePath ->
            runCatching {
                val base = Path.of(basePath).normalize()
                val target = Path.of(rawPath).normalize()
                if (target.startsWith(base)) base.relativize(target).toString() else rawPath
            }.getOrElse { rawPath }
        } ?: rawPath

        val lineRange = resolveSelectedLineRange(editorManager)
        return InsertPayload(relativePath, lineRange)
    }

    private fun resolveSelectedLineRange(editorManager: FileEditorManager): LineRange? {
        val editor = editorManager.selectedTextEditor ?: return null
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            return null
        }
        if (selectionModel.selectedText.isNullOrEmpty()) {
            return null
        }

        val document = editor.document
        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd
        val startLine = runCatching { document.getLineNumber(startOffset) }.getOrElse {
            logger.warn("Failed to resolve start line number", it)
            return null
        }
        if (startLine < 0) {
            return null
        }

        val endLine = runCatching {
            val adjustedEnd = when {
                endOffset <= startOffset -> startOffset
                endOffset == document.textLength -> endOffset
                else -> endOffset - 1
            }
            document.getLineNumber(adjustedEnd.coerceAtLeast(startOffset))
        }.getOrElse {
            logger.warn("Failed to resolve end line number", it)
            startLine
        }

        val start = startLine + 1
        val end = (endLine + 1).takeIf { it > start }
        return LineRange(start, end)
    }

    private fun determineToolbarState(project: Project?): ToolbarState {
        if (project == null) {
            return ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }

        val manager = project.service<OpenCodeTerminalManager>()
        return if (manager.isOpenCodeTerminalActive()) {
            ToolbarState(ACTIVE_ICON, ACTIVE_TEXT, ACTIVE_DESCRIPTION)
        } else {
            ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }
    }

    private data class InsertPayload(val relativePath: String, val lineRange: LineRange?)

    private data class LineRange(val start: Int, val end: Int?)

    private data class ToolbarState(val icon: Icon, val text: String, val description: String)
}
