package com.github.eisermann.opencodelink.mcp

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/**
 * Virtual file that holds diff data for the OpenCode diff editor.
 * When opened, it triggers OpenCodeDiffFileEditorProvider to show a diff view.
 */
internal class OpenCodeDiffVirtualFile(
    val targetFile: VirtualFile,
    val proposedContent: String,
    val filePath: String
) : LightVirtualFile("Diff: ${targetFile.name}", targetFile.fileType, "") {

    override fun toString(): String = "OpenCodeDiffVirtualFile($filePath)"

    override fun isWritable(): Boolean = false

    override fun getFileType(): FileType = targetFile.fileType
}
