package com.github.eisermann.geminilauncher.terminal

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Service responsible for tracking and cleaning up temporary script files
 * created for Gemini command execution within a project.
 */
@Service(Service.Level.PROJECT)
class TempScriptCleanupService {
    private val logger = logger<TempScriptCleanupService>()
    private val scriptPaths = Collections.synchronizedSet(mutableSetOf<Path>())

    fun register(path: Path) {
        scriptPaths.add(path)
    }

    fun cleanup() {
        val snapshot = scriptPaths.toList()
        snapshot.forEach { path ->
            runCatching { Files.deleteIfExists(path) }.onFailure {
                logger.info("Failed to delete Gemini temporary script on project close: $path", it)
            }
        }
        scriptPaths.clear()
    }
}

