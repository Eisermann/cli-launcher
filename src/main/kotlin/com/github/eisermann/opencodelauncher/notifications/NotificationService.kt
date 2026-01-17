package com.github.eisermann.opencodelauncher.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NotificationService(private val project: Project) {

    companion object {
        private const val NOTIFICATION_GROUP_ID = "OpenCodeLauncher"
    }

    fun notifyRefreshReceived(message: String = "OpenCode CLI processing completed.") {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)

        val notification = notificationGroup.createNotification(
            "OpenCode Launcher",
            message,
            NotificationType.INFORMATION
        )

        notification.notify(project)
    }

    fun notifyRefreshError(error: String) {
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)

        val notification = notificationGroup.createNotification(
            "OpenCode Launcher",
            "Error processing refresh request: $error",
            NotificationType.ERROR
        )

        notification.notify(project)
    }
}
