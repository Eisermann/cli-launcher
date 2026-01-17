package com.github.eisermann.geminilauncher.cli

import com.github.eisermann.geminilauncher.settings.GeminiLauncherSettings
import com.github.eisermann.geminilauncher.settings.options.Model
import com.github.eisermann.geminilauncher.settings.options.Mode
import com.github.eisermann.geminilauncher.settings.options.ModelReasoningEffort
import com.intellij.testFramework.LightPlatformTestCase
import com.github.eisermann.geminilauncher.settings.options.WinShell
import org.junit.Assert.assertEquals

/**
 * Test OS provider for mocking Windows/non-Windows behavior
 */
class TestOsProvider(override val isWindows: Boolean) : OsProvider

/**
 * Windows-specific and PowerShell 7.3+ specific tests for GeminiArgsBuilder.
 * These tests verify the OS-specific formatting behavior.
 */
class GeminiArgsBuilderTest : LightPlatformTestCase() {

    private lateinit var state: GeminiLauncherSettings.State
    private val mcpNonWindows = """
        {
          "type": "stdio",
          "env": {
            "IJ_MCP_SERVER_PORT": "64342"
          },
          "command": "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java",
          "args": [
            "-classpath",
            "/Applications/IntelliJ IDEA.app/Contents/plugins/mcpserver/lib/mcpserver-frontend.jar:/Applications/IntelliJ IDEA.app/Contents/lib/util-8.jar",
            "com.intellij.mcpserver.stdio.McpStdioRunnerKt"
          ]
        }
        """.trimIndent()

    override fun setUp() {
        super.setUp()
        state = GeminiLauncherSettings.State()
        state.mode = Mode.FULL_AUTO
        state.model = Model.CUSTOM
        state.customModel = "gemini-pro"
        state.modelReasoningEffort = ModelReasoningEffort.HIGH
    }

    // === Complex arguments formatting tests ===

    fun testComplexArgsFormattingOnNonWindows() {
        // Test non-Windows formatting
        val osProvider = TestOsProvider(isWindows = false)
        state.mcpConfigInput = mcpNonWindows
        state.enableNotification = true
        state.enableSearch = true
        state.enableCdProjectRoot = true

        val result = GeminiArgsBuilder.build(
            state,
            11111,
            osProvider = osProvider,
            projectBasePath = "/home/user/project"
        )

        // Verify that complex arguments are properly formatted for non-Windows
        assertEquals(
            listOf(
                """--full-auto""",
                """--enable""",
                """web_search_request""",
                """--cd""",
                "'/home/user/project'",
                """--model""",
                "'gemini-pro'",
                """-c""",
                "'model_reasoning_effort=high'",
                """-c""",
                "'notify=[\"curl\", \"-s\", \"-X\", \"POST\", \"http://localhost:11111/refresh\", \"-d\"]'",
                """-c""",
                "'mcp_servers.intellij.command=/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home/bin/java'",
                """-c""",
                """'mcp_servers.intellij.args=["-classpath", "/Applications/IntelliJ IDEA.app/Contents/plugins/mcpserver/lib/mcpserver-frontend.jar:/Applications/IntelliJ IDEA.app/Contents/lib/util-8.jar", "com.intellij.mcpserver.stdio.McpStdioRunnerKt"]'""",
                """-c""",
                """'mcp_servers.intellij.env={"IJ_MCP_SERVER_PORT"="64342"}'"""
            ),
            result
        )
    }

    fun testMinimalArgs() {
        // Test minimal args on non-Windows
        val osProvider = TestOsProvider(isWindows = false)
        state.mode = Mode.DEFAULT
        state.model = Model.DEFAULT
        state.modelReasoningEffort = ModelReasoningEffort.DEFAULT
        state.isPowerShell73OrOver = false
        state.mcpConfigInput = ""
        state.openFileOnChange = false
        state.enableNotification = false

        val result = GeminiArgsBuilder.build(state, 5555, osProvider = osProvider)

        // Verify that only necessary arguments are included
        assertEquals(0, result.size)
    }

    fun testComplexArgsFormattingOnWindowsWithWSL() {
        // Test Windows host but WSL selected; should format like non-Windows
        // WSL ignores notify and MCP config, so we just verify basic args
        val osProvider = TestOsProvider(isWindows = true)
        state.winShell = WinShell.WSL
        state.mcpConfigInput = "" // WSL ignores MCP config anyway
        state.openFileOnChange = true
        state.enableNotification = true
        state.enableCdProjectRoot = true

        val result = GeminiArgsBuilder.build(state, 44444, osProvider = osProvider)

        // Verify non-Windows style quoting and no SystemRoot (WSL ignores MCP/notify)
        assertEquals(
            listOf(
                """--full-auto""",
                """--model""",
                "'gemini-pro'",
                """-c""",
                "'model_reasoning_effort=high'"
            ),
            result
        )
    }
}
