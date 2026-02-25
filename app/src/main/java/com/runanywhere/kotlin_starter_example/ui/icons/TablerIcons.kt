package com.runanywhere.kotlin_starter_example.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.runanywhere.kotlin_starter_example.R

object TablerIcons {
    // Navigation
    val ArrowLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_left)

    // Chat & Communication
    val Message: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_message)
    val Send: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_send)
    val Robot: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_robot)
    val User: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_user)

    // Audio & Voice
    val Microphone: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_microphone)
    val Volume: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_volume)
    val Ear: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ear)
    val Speakerphone: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_speakerphone)
    val PlayerStop: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_player_stop)

    // AI & Magic
    val Sparkles: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_sparkles)

    // Tools & Actions
    val Tool: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tool)
    val Calculator: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_calculator)
    val Adjustments: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_adjustments)
    val Tune: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tune)
    val Download: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_download)
    val Plus: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_plus)
    val X: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_x)
    val TrashX: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_trash_x)

    // Status & Info
    val ShieldCheck: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_shield_check)
    val CircleCheck: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_circle_check)
    val CircleX: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_circle_x)
    val AlertTriangle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_alert_triangle)
    val Clock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_clock)

    // Vision & Media
    val Eye: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_eye)
    val Photo: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_photo)
    val PhotoPlus: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_photo_plus)

    // System
    val Cpu: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_cpu)
    val Cloud: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_cloud)
    val Typography: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_typography)
    val Language: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_language)
}
