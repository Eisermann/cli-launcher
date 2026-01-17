package com.github.eisermann.geminilauncher.mcp

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/**
 * Virtual file that holds diff data for the Gemini diff editor.
 * When opened, it triggers GeminiDiffFileEditorProvider to show a diff view.
 */
internal class GeminiDiffVirtualFile(
    val targetFile: VirtualFile,
    val proposedContent: String,
    val filePath: String
) : LightVirtualFile("Diff: ${targetFile.name}", targetFile.fileType, "") {

    override fun toString(): String = "GeminiDiffVirtualFile($filePath)"

    override fun isWritable(): Boolean = false

    override fun getFileType(): FileType = targetFile.fileType
}
