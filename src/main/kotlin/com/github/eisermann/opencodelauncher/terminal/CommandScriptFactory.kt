package com.github.eisermann.opencodelauncher.terminal

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Builds terminal execution plans for OpenCode commands, falling back to temporary scripts for
 * long inputs that would otherwise be truncated by the terminal widget.
 */
internal class CommandScriptFactory(
    private val project: Project,
    private val supportsPosixShell: () -> Boolean,
    cleanupService: TempScriptCleanupService? = runCatching {
        project.getService(TempScriptCleanupService::class.java)
    }.getOrNull()
) {

    companion object {
        internal const val MAX_INLINE_COMMAND_LENGTH = 1024
        private const val SCRIPT_PREFIX = "opencode-cmd-"
        private const val SCRIPT_SUFFIX = ".sh"
        private const val SCRIPT_TEMPLATE = "/scripts/opencode_command_wrapper.sh"
    }

    private val logger = logger<CommandScriptFactory>()
    private val scriptCleanupService = cleanupService

    internal fun buildPlan(command: String): TerminalCommandPlan? {
        if (command.length <= MAX_INLINE_COMMAND_LENGTH || !supportsPosixShell()) {
            return TerminalCommandPlan(command)
        }

        val scriptPath = createCommandScript(command) ?: return null
        val pathString = scriptPath.toAbsolutePath().toString()
        val escapedPath = quoteForPosix(pathString)
        val planCommand = "sh $escapedPath"

        return TerminalCommandPlan(planCommand) {
            runCatching { Files.deleteIfExists(scriptPath) }.onFailure {
                logger.info("Failed to delete temporary OpenCode script after dispatch failure", it)
            }
        }
    }

    private fun createCommandScript(command: String): Path? {
        val scriptPath = runCatching { Files.createTempFile(SCRIPT_PREFIX, SCRIPT_SUFFIX) }.getOrElse {
            logger.warn("Failed to create temporary OpenCode command script", it)
            return null
        }

        runCatching { scriptCleanupService?.register(scriptPath) }
            .onFailure { logger.warn("Failed to register temporary OpenCode script cleanup: $scriptPath", it) }
        runCatching { scriptPath.toFile().deleteOnExit() }
            .onFailure { logger.warn("Failed to mark temporary OpenCode script for JVM exit deletion: $scriptPath", it) }

        val writeResult = runCatching {
            val script = readTemplate(SCRIPT_TEMPLATE, command) ?: return null
            Files.writeString(scriptPath, script, StandardCharsets.UTF_8)
        }

        if (writeResult.isFailure) {
            logger.warn("Failed to write temporary OpenCode command script", writeResult.exceptionOrNull())
            runCatching { Files.deleteIfExists(scriptPath) }
            return null
        }

        return scriptPath
    }

    private fun readTemplate(resourcePath: String, command: String): String? {
        val stream = javaClass.getResourceAsStream(resourcePath)
        if (stream == null) {
            logger.warn("Template resource not found: $resourcePath")
            return null
        }

        return runCatching {
            stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val template = reader.readText()
                String.format(Locale.ROOT, template, command)
            }
        }.getOrElse {
            logger.warn("Failed to read template resource: $resourcePath", it)
            null
        }
    }

    private fun quoteForPosix(path: String): String = "'" + path.replace("'", "'\"'\"'") + "'"
}

internal data class TerminalCommandPlan(val command: String, val cleanupOnFailure: () -> Unit = {})
