package com.github.eisermann.geminilauncher.terminal

import com.github.eisermann.geminilauncher.settings.GeminiLauncherSettings
import com.github.eisermann.geminilauncher.settings.options.WinShell
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
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import com.intellij.openapi.util.Disposer
import javax.swing.SwingUtilities

/**
 * Project-level service responsible for managing Gemini terminals.
 * Encapsulates lookup, reuse, focus, and command execution logic so actions stay thin.
 */
@Service(Service.Level.PROJECT)
class GeminiTerminalManager(private val project: Project) {

    companion object {
        private val GEMINI_TERMINAL_KEY = Key.create<Boolean>("gemini.launcher.geminiTerminal")
        private val GEMINI_TERMINAL_RUNNING_KEY = Key.create<Boolean>("gemini.launcher.geminiTerminal.running")
        private val GEMINI_TERMINAL_CALLBACK_KEY = Key.create<Boolean>("gemini.launcher.geminiTerminal.callbackRegistered")
        private val GEMINI_TERMINAL_SHIFT_ENTER_KEY = Key.create<Boolean>("gemini.launcher.geminiTerminal.shiftEnterRegistered")
    }

    private val logger = logger<GeminiTerminalManager>()
    private val scriptFactory = CommandScriptFactory(
        project = project,
        supportsPosixShell = {
            if (!SystemInfoRt.isWindows) {
                true
            } else {
                runCatching { service<GeminiLauncherSettings>().state.winShell == WinShell.WSL }.getOrDefault(false)
            }
        }
    )

    private data class GeminiTerminal(val widget: TerminalWidget, val content: Content)

    /**
     * Launches or reuses the Gemini terminal for the given command.
     * @throws Throwable when terminal creation or command execution fails.
     */
    fun launch(baseDir: String, command: String) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        var existingTerminal = locateGeminiTerminal(terminalManager)

        existingTerminal?.let { terminal ->
            ensureTerminationCallback(terminal.widget, terminal.content)
            ensureShiftEnterNewline(terminal.widget, terminal.content)
            if (isGeminiRunning(terminal)) {
                logger.info("Focusing active Gemini terminal")
                focusGeminiTerminal(terminalManager, terminal)
                return
            }

            if (reuseGeminiTerminal(terminal, command)) {
                logger.info("Reused existing Gemini terminal for new Gemini run")
                focusGeminiTerminal(terminalManager, terminal)
                return
            } else {
                clearGeminiMetadata(terminalManager, terminal.widget)
                existingTerminal = null
            }
        }

        var widget: TerminalWidget? = null
        try {
            widget = terminalManager.createShellWidget(baseDir, "Gemini", true, true)
            val content = markGeminiTerminal(terminalManager, widget)
            ensureShiftEnterNewline(widget, content)
            if (!sendCommandToTerminal(widget, content, command)) {
                throw IllegalStateException("Failed to execute Gemini command")
            }
            if (content != null) {
                focusGeminiTerminal(terminalManager, GeminiTerminal(widget, content))
            }
        } catch (sendError: Throwable) {
            widget?.let { clearGeminiMetadata(terminalManager, it) }
            throw sendError
        }
    }

    /**
     * Returns true when the Gemini terminal tab is currently selected in the terminal tool window.
     */
    fun isGeminiTerminalActive(): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            findDisplayedGeminiTerminal(terminalManager) != null
        } catch (t: Throwable) {
            logger.warn("Failed to inspect Gemini terminal active state", t)
            false
        }
    }

    fun typeIntoActiveGeminiTerminal(text: String): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val terminal = findDisplayedGeminiTerminal(terminalManager) ?: return false
            val success = typeText(terminal.widget, text)
            if (success) {
                focusGeminiTerminal(terminalManager, terminal)
            }
            success
        } catch (t: Throwable) {
            logger.warn("Failed to type into Gemini terminal", t)
            false
        }
    }

    private fun locateGeminiTerminal(manager: TerminalToolWindowManager): GeminiTerminal? = try {
        manager.terminalWidgets.asSequence().mapNotNull { widget ->
            val content = manager.getContainer(widget)?.content ?: return@mapNotNull null
            val isGemini = content.getUserData(GEMINI_TERMINAL_KEY) == true || content.displayName == "Gemini"
            if (!isGemini) {
                return@mapNotNull null
            }
            GeminiTerminal(widget, content)
        }.firstOrNull()
    } catch (t: Throwable) {
        logger.warn("Failed to inspect existing terminal widgets", t)
        null
    }

    private fun findDisplayedGeminiTerminal(
        manager: TerminalToolWindowManager
    ): GeminiTerminal? {
        val terminal = locateGeminiTerminal(manager) ?: return null
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

    private fun focusGeminiTerminal(
        manager: TerminalToolWindowManager,
        terminal: GeminiTerminal
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            try {
                val toolWindow = resolveTerminalToolWindow(manager)
                if (toolWindow == null) {
                    logger.warn("Terminal tool window is not available for focusing Gemini")
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
                        logger.warn("Failed to request focus for Gemini terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Gemini terminal", focusError)
            }
        }
    }

    private fun resolveTerminalToolWindow(
        manager: TerminalToolWindowManager
    ) = manager.getToolWindow()
        ?: ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

    private fun markGeminiTerminal(manager: TerminalToolWindowManager, widget: TerminalWidget): Content? {
        return try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(GEMINI_TERMINAL_KEY, true)
                setGeminiRunning(content, false)
                ensureTerminationCallback(widget, content)
                content.displayName = "Gemini"
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag Gemini terminal metadata", t)
            null
        }
    }

    private fun clearGeminiMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            manager.getContainer(widget)?.content?.let { content ->
                clearGeminiMetadata(content)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear Gemini terminal metadata", t)
        }
    }

    private fun clearGeminiMetadata(content: Content) {
        content.putUserData(GEMINI_TERMINAL_KEY, null)
        content.putUserData(GEMINI_TERMINAL_RUNNING_KEY, null)
        content.putUserData(GEMINI_TERMINAL_CALLBACK_KEY, null)
    }

    private fun reuseGeminiTerminal(
        terminal: GeminiTerminal,
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
            setGeminiRunning(content, true)
            true
        } catch (t: Throwable) {
            logger.warn("Failed to execute Gemini command", t)
            setGeminiRunning(content, false)
            runCatching { plan.cleanupOnFailure() }
            false
        }
    }

    private fun isGeminiRunning(terminal: GeminiTerminal): Boolean {
        val liveState = invokeIsCommandRunning(terminal.widget)
        if (liveState != null) {
            setGeminiRunning(terminal.content, liveState)
            return liveState
        }
        return terminal.content.getUserData(GEMINI_TERMINAL_RUNNING_KEY) ?: false
    }

    private fun setGeminiRunning(content: Content?, running: Boolean) {
        content?.putUserData(GEMINI_TERMINAL_RUNNING_KEY, running)
    }

    private fun ensureTerminationCallback(widget: TerminalWidget, content: Content?) {
        if (content == null) return
        if (content.getUserData(GEMINI_TERMINAL_CALLBACK_KEY) == true) return
        try {
            widget.addTerminationCallback({ setGeminiRunning(content, false) }, content)
            content.putUserData(GEMINI_TERMINAL_CALLBACK_KEY, true)
        } catch (t: Throwable) {
            logger.warn("Failed to register termination callback", t)
        }
    }

	    private fun ensureShiftEnterNewline(widget: TerminalWidget, content: Content?) {
	        if (content == null) return
	        if (content.getUserData(GEMINI_TERMINAL_SHIFT_ENTER_KEY) == true) return

        val component = resolveTerminalComponent(widget) ?: return
        // Use a scoped KeyEventDispatcher tied to this terminal component.
        // KeyListener/InputMap bindings are sometimes bypassed by the terminal's internal key handling.
	        val dispatcher = KeyEventDispatcher { event ->
	            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
	            if (event.keyCode != KeyEvent.VK_ENTER || !event.isShiftDown) return@KeyEventDispatcher false
	            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return@KeyEventDispatcher false
	            if (!SwingUtilities.isDescendingFrom(focusOwner, component)) return@KeyEventDispatcher false
	            // Prepend ESC and let the terminal handle the actual Enter press.
	            // This avoids sending a second Enter via ttyConnector (which can yield a blank line).
	            typeText(widget, "\u001b")
	            false
	        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        Disposer.register(project) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }

        content.putUserData(GEMINI_TERMINAL_SHIFT_ENTER_KEY, true)
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
                logger.warn("Failed to write to Gemini terminal connector", it)
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
                logger.warn("Failed to invoke typeText on Gemini terminal", it)
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
                logger.warn("Failed to invoke pasteText on Gemini terminal", it)
                false
            }
        }

        return false
    }
}
