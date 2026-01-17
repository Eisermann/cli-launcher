package com.github.eisermann.opencodelauncher.terminal

import com.github.eisermann.opencodelauncher.settings.OpenCodeLauncherSettings
import com.github.eisermann.opencodelauncher.settings.options.WinShell
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import java.awt.Component
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Project-level service responsible for managing OpenCode terminals.
 * Encapsulates lookup, reuse, focus, and command execution logic so actions stay thin.
 */
@Service(Service.Level.PROJECT)
class OpenCodeTerminalManager(private val project: Project) {

    companion object {
        private val OPENCODE_TERMINAL_KEY = Key.create<Boolean>("opencode.launcher.openCodeTerminal")
        private val OPENCODE_TERMINAL_RUNNING_KEY = Key.create<Boolean>("opencode.launcher.openCodeTerminal.running")
        private val OPENCODE_TERMINAL_CALLBACK_KEY = Key.create<Boolean>("opencode.launcher.openCodeTerminal.callbackRegistered")
        private val OPENCODE_TERMINAL_SHIFT_ENTER_KEY = Key.create<Boolean>("opencode.launcher.openCodeTerminal.shiftEnterRegistered")
    }

    private val logger = logger<OpenCodeTerminalManager>()
    private val scriptFactory = CommandScriptFactory(
        project = project,
        supportsPosixShell = {
            if (!SystemInfoRt.isWindows) {
                true
            } else {
                runCatching { service<OpenCodeLauncherSettings>().state.winShell == WinShell.WSL }.getOrDefault(false)
            }
        }
    )

    private data class OpenCodeTerminal(val widget: TerminalWidget, val content: Content)

    /**
     * Launches or reuses the OpenCode terminal for the given command.
     * @throws Throwable when terminal creation or command execution fails.
     */
    fun launch(baseDir: String, command: String) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        var existingTerminal = locateOpenCodeTerminal(terminalManager)

        existingTerminal?.let { terminal ->
            ensureTerminationCallback(terminal.widget, terminal.content)
            ensureShiftEnterNewline(terminal.widget, terminal.content)
            if (isOpenCodeRunning(terminal)) {
                logger.info("Focusing active OpenCode terminal")
                focusOpenCodeTerminal(terminalManager, terminal)
                return
            }

            if (reuseOpenCodeTerminal(terminal, command)) {
                logger.info("Reused existing OpenCode terminal for new OpenCode run")
                focusOpenCodeTerminal(terminalManager, terminal)
                return
            } else {
                clearOpenCodeMetadata(terminalManager, terminal.widget)
                existingTerminal = null
            }
        }

        var widget: TerminalWidget? = null
        try {
            widget = terminalManager.createShellWidget(baseDir, "OpenCode", true, true)
            val content = markOpenCodeTerminal(terminalManager, widget)
            ensureShiftEnterNewline(widget, content)
            if (!sendCommandToTerminal(widget, content, command)) {
                throw IllegalStateException("Failed to execute OpenCode command")
            }
            if (content != null) {
                focusOpenCodeTerminal(terminalManager, OpenCodeTerminal(widget, content))
            }
        } catch (sendError: Throwable) {
            widget?.let { clearOpenCodeMetadata(terminalManager, it) }
            throw sendError
        }
    }

    /**
     * Returns true when the OpenCode terminal tab is currently selected in the terminal tool window.
     */
    fun isOpenCodeTerminalActive(): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            findDisplayedOpenCodeTerminal(terminalManager) != null
        } catch (t: Throwable) {
            logger.warn("Failed to inspect OpenCode terminal active state", t)
            false
        }
    }

    fun typeIntoActiveOpenCodeTerminal(text: String): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val terminal = findDisplayedOpenCodeTerminal(terminalManager) ?: return false
            val success = typeText(terminal.widget, text)
            if (success) {
                focusOpenCodeTerminal(terminalManager, terminal)
            }
            success
        } catch (t: Throwable) {
            logger.warn("Failed to type into OpenCode terminal", t)
            false
        }
    }

    private fun locateOpenCodeTerminal(manager: TerminalToolWindowManager): OpenCodeTerminal? = try {
        manager.terminalWidgets.asSequence().mapNotNull { widget ->
            val content = manager.getContainer(widget)?.content ?: return@mapNotNull null
            val isOpenCode = content.getUserData(OPENCODE_TERMINAL_KEY) == true || content.displayName == "OpenCode"
            if (!isOpenCode) {
                return@mapNotNull null
            }
            OpenCodeTerminal(widget, content)
        }.firstOrNull()
    } catch (t: Throwable) {
        logger.warn("Failed to inspect existing terminal widgets", t)
        null
    }

    private fun findDisplayedOpenCodeTerminal(
        manager: TerminalToolWindowManager
    ): OpenCodeTerminal? {
        val terminal = locateOpenCodeTerminal(manager) ?: return null
        val toolWindow = resolveTerminalToolWindow(manager) ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null
        if (selectedContent != terminal.content) {
            return null
        }

        val isDisplayed = toolWindow.isVisible
        if (!isDisplayed) {
            return null
        }

        return terminal
    }

    private fun focusOpenCodeTerminal(
        manager: TerminalToolWindowManager,
        terminal: OpenCodeTerminal
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            try {
                val toolWindow = resolveTerminalToolWindow(manager)
                if (toolWindow == null) {
                    logger.warn("Terminal tool window is not available for focusing OpenCode")
                    return@invokeLater
                }

                val contentManager = toolWindow.contentManager
                if (contentManager.selectedContent != terminal.content) {
                    contentManager.setSelectedContent(terminal.content, true)
                }

                toolWindow.activate({
                    try {
                        terminal.widget.requestFocus()
                    } catch (focusError: Throwable) {
                        logger.warn("Failed to request focus for OpenCode terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing OpenCode terminal", focusError)
            }
        }
    }

    private fun resolveTerminalToolWindow(
        manager: TerminalToolWindowManager
    ) = manager.getToolWindow()
        ?: ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

    private fun markOpenCodeTerminal(manager: TerminalToolWindowManager, widget: TerminalWidget): Content? {
        return try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(OPENCODE_TERMINAL_KEY, true)
                setOpenCodeRunning(content, false)
                ensureTerminationCallback(widget, content)
                content.displayName = "OpenCode"
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag OpenCode terminal metadata", t)
            null
        }
    }

    private fun clearOpenCodeMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            manager.getContainer(widget)?.content?.let { content ->
                clearOpenCodeMetadata(content)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear OpenCode terminal metadata", t)
        }
    }

    private fun clearOpenCodeMetadata(content: Content) {
        content.putUserData(OPENCODE_TERMINAL_KEY, null)
        content.putUserData(OPENCODE_TERMINAL_RUNNING_KEY, null)
        content.putUserData(OPENCODE_TERMINAL_CALLBACK_KEY, null)
    }

    private fun reuseOpenCodeTerminal(
        terminal: OpenCodeTerminal,
        command: String
    ): Boolean {
        ensureTerminationCallback(terminal.widget, terminal.content)
        return sendCommandToTerminal(terminal.widget, terminal.content, command)
    }

    private fun sendCommandToTerminal(
        widget: TerminalWidget,
        content: Content?,
        command: String
    ): Boolean {
        val plan = scriptFactory.buildPlan(command) ?: return false

        return try {
            widget.sendCommandToExecute(plan.command)
            setOpenCodeRunning(content, true)
            true
        } catch (t: Throwable) {
            logger.warn("Failed to execute OpenCode command", t)
            setOpenCodeRunning(content, false)
            runCatching { plan.cleanupOnFailure() }
            false
        }
    }

    private fun isOpenCodeRunning(terminal: OpenCodeTerminal): Boolean {
        val liveState = invokeIsCommandRunning(terminal.widget)
        if (liveState != null) {
            setOpenCodeRunning(terminal.content, liveState)
            return liveState
        }
        return terminal.content.getUserData(OPENCODE_TERMINAL_RUNNING_KEY) ?: false
    }

    private fun setOpenCodeRunning(content: Content?, running: Boolean) {
        content?.putUserData(OPENCODE_TERMINAL_RUNNING_KEY, running)
    }

    private fun ensureTerminationCallback(widget: TerminalWidget, content: Content?) {
        if (content == null) return
        if (content.getUserData(OPENCODE_TERMINAL_CALLBACK_KEY) == true) return
        try {
            widget.addTerminationCallback({ setOpenCodeRunning(content, false) }, content)
            content.putUserData(OPENCODE_TERMINAL_CALLBACK_KEY, true)
        } catch (t: Throwable) {
            logger.warn("Failed to register termination callback", t)
        }
    }

    private fun ensureShiftEnterNewline(widget: TerminalWidget, content: Content?) {
        if (content == null) return
        if (content.getUserData(OPENCODE_TERMINAL_SHIFT_ENTER_KEY) == true) return

        val component = resolveTerminalComponent(widget) ?: return

        val specialNewline = "\n"
        var suppressUntilEnterReleased = false

        // Debounce to prevent double handling (repeat / multiple pipelines)
        var lastHandledAtNanos = 0L

        val dispatcher = KeyEventDispatcher { event ->
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return@KeyEventDispatcher false
            if (!SwingUtilities.isDescendingFrom(focusOwner, component)) {
                if (event.id == KeyEvent.KEY_RELEASED && event.keyCode == KeyEvent.VK_ENTER) {
                    suppressUntilEnterReleased = false
                }
                return@KeyEventDispatcher false
            }

            when (event.id) {
                KeyEvent.KEY_PRESSED -> {
                    if (event.keyCode != KeyEvent.VK_ENTER || !event.isShiftDown) return@KeyEventDispatcher false

                    val now = System.nanoTime()
                    if (now - lastHandledAtNanos < 150_000_000) { // 150ms
                        event.consume()
                        return@KeyEventDispatcher true
                    }
                    lastHandledAtNanos = now

                    suppressUntilEnterReleased = true
                    event.consume()

                    ApplicationManager.getApplication().invokeLater {
                        typeSpecialNewline(widget, specialNewline)
                    }

                    true
                }

                KeyEvent.KEY_TYPED -> {
                    if (!suppressUntilEnterReleased) return@KeyEventDispatcher false
                    event.consume()
                    true
                }

                KeyEvent.KEY_RELEASED -> {
                    if (!suppressUntilEnterReleased) return@KeyEventDispatcher false
                    if (event.keyCode != KeyEvent.VK_ENTER) return@KeyEventDispatcher false
                    suppressUntilEnterReleased = false
                    event.consume()
                    true
                }

                else -> false
            }
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)

        // IMPORTANT: dispose with terminal/content, not with project (otherwise dispatchers accumulate!)
        runCatching {
            Disposer.register(content) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
            }
        }.getOrElse {
            Disposer.register(project) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
            }
        }

        content.putUserData(OPENCODE_TERMINAL_SHIFT_ENTER_KEY, true)
    }

    private fun typeSpecialNewline(widget: TerminalWidget, sequence: String) {
        val ok = typeText(widget, sequence)
        if (!ok) {
            logger.warn("Failed to inject Shift+Enter newline sequence into terminal")
        }
    }

    private fun resolveTerminalComponent(widget: TerminalWidget): Component? {
        val direct = widget as? Component
        if (direct != null) return direct

        return runCatching {
            val method = widget.javaClass.methods.firstOrNull { it.name == "getComponent" && it.parameterCount == 0 }
            method?.apply { isAccessible = true }?.invoke(widget) as? Component
        }.getOrNull()
    }

    private fun invokeIsCommandRunning(widget: TerminalWidget): Boolean? {
        return runCatching {
            val method = widget.javaClass.methods.firstOrNull { it.name == "isCommandRunning" && it.parameterCount == 0 }
            method?.apply { isAccessible = true }?.invoke(widget) as? Boolean
        }.getOrNull()
    }

    private fun typeText(widget: TerminalWidget, text: String): Boolean {
        val connector = runCatching { widget.ttyConnector }.getOrNull()
        if (connector != null) {
            return runCatching {
                connector.write(text)
                true
            }.getOrElse {
                logger.warn("Failed to write to OpenCode terminal connector", it)
                false
            }
        }

        val methods = widget.javaClass.methods
        val typeMethod = methods.firstOrNull { it.name == "typeText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (typeMethod != null) {
            return runCatching {
                typeMethod.isAccessible = true
                typeMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke typeText on OpenCode terminal", it)
                false
            }
        }

        val pasteMethod = methods.firstOrNull { it.name == "pasteText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (pasteMethod != null) {
            return runCatching {
                pasteMethod.isAccessible = true
                pasteMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke pasteText on OpenCode terminal", it)
                false
            }
        }

        return false
    }
}
