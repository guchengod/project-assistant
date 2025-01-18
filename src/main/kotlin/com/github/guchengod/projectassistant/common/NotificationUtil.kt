package com.github.guchengod.projectassistant.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.jetbrains.rd.generator.nova.PredefinedType

object NotificationUtil {

    // 显示通知
    fun showNotification(tile:String, content:String, type: NotificationType = NotificationType.INFORMATION) {
        val notification = Notification(
            "project.assistant.notification.group.id", // 通知组 ID（唯一标识）
            tile,                     // 通知标题
            content, // 通知内容
            type    // 通知类型（INFO, WARNING, ERROR）
        )
        Notifications.Bus.notify(notification)
    }
}