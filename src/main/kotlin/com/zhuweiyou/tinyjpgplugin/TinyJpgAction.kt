package com.zhuweiyou.tinyjpgplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

class TinyJpgAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val files = getSelectedFiles(event)
        val hasImage = files.any { file ->
            if (file.isDirectory) true
            else file.extension?.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }
        event.presentation.isEnabledAndVisible = hasImage
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val files = getSelectedFiles(event)
        if (files.isEmpty()) return

        val imageFiles = TinyJpgService.collectImageFiles(files)
        TinyJpgService.compressFiles(project, imageFiles)
    }

    private fun getSelectedFiles(event: AnActionEvent): List<VirtualFile> {
        val arr = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!arr.isNullOrEmpty()) return arr.toList()
        val single = event.getData(CommonDataKeys.VIRTUAL_FILE)
        if (single != null) return listOf(single)
        return emptyList()
    }
}
