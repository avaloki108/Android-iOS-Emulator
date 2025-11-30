package com.PiCode.androidiosemulator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.PiCode.androidiosemulator.util.AndroidSdkUtils

class StartAndroidEmulatorAction : AnAction("Start Android Emulator") {

    override fun actionPerformed(e: AnActionEvent) {
        val sdkPath = AndroidSdkUtils.getOrDetectSdkPath()

        if (sdkPath.isNullOrEmpty()) {
            Messages.showErrorDialog(
                "Android SDK path is not set and could not be auto-detected. Please configure it in 'File > Settings > Android-iOS Emulator'.",
                "SDK Path Not Found"
            )
            return
        }

        val emulatorPath = AndroidSdkUtils.getEmulatorPath(sdkPath)
        val avdList = AndroidSdkUtils.getAvailableAvds(emulatorPath)

        if (avdList.isEmpty()) {
            Messages.showErrorDialog("No AVDs found. Please create an AVD in Android Studio's AVD Manager.", "Error")
            return
        }

        val avdChoice = Messages.showEditableChooseDialog(
            "Select an AVD",
            "Available AVDs",
            null,
            avdList.toTypedArray(),
            avdList.firstOrNull() ?: "",
            null
        )

        if (avdChoice != null) {
            try {
                ProcessBuilder(emulatorPath, "-avd", avdChoice)
                    .redirectErrorStream(true)
                    .start()
                Messages.showInfoMessage("Android Emulator starting... ($avdChoice)", "Success")
            } catch (ex: Exception) {
                Messages.showErrorDialog("Failed to start Android Emulator: ${ex.message}", "Error")
            }
        }
    }
}
