package com.PiCode.androidiosemulator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import com.PiCode.androidiosemulator.settings.EmulatorSettingsConfigurable
import com.PiCode.androidiosemulator.util.AndroidSdkUtils

class EmulatorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val options = arrayOf("Android Emulator", "iOS Simulator")
        val choice = Messages.showDialog(
            "Select emulator type",
            "Emulator Selection",
            options,
            0, // Default option index
            null
        )

        when (choice) {
            0 -> startAndroidEmulator()
            1 -> startIosSimulator()
        }
    }

    private fun startAndroidEmulator() {
        val sdkPath = AndroidSdkUtils.getOrDetectSdkPath()

        if (sdkPath.isNullOrEmpty()) {
            val openSettings = Messages.showYesNoDialog(
                "Android SDK path is not set and could not be auto-detected. Would you like to configure it in settings?",
                "SDK Path Not Found",
                "Open Settings",
                "Cancel",
                null
            )

            if (openSettings == Messages.YES) {
                ApplicationManager.getApplication().invokeLater {
                    ShowSettingsUtil.getInstance().showSettingsDialog(null, EmulatorSettingsConfigurable::class.java)
                }
            }
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
            } catch (e: Exception) {
                Messages.showErrorDialog("Failed to start Android Emulator: ${e.message}", "Error")
            }
        }
    }

    private fun startIosSimulator() {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("mac")) {
            Messages.showErrorDialog("iOS Simulator is only available on macOS.", "Error")
            return
        }

        val simulatorList = getAvailableIosSimulators()
        if (simulatorList.isEmpty()) {
            Messages.showErrorDialog("No iOS Simulators found.", "Error")
            return
        }

        val simulatorChoice = Messages.showEditableChooseDialog(
            "Select an iOS Simulator",
            "Available Simulators",
            null,
            simulatorList.toTypedArray(),
            simulatorList.firstOrNull() ?: "",
            null
        )

        if (simulatorChoice != null) {
            try {
                ProcessBuilder("xcrun", "simctl", "boot", simulatorChoice)
                    .redirectErrorStream(true)
                    .start()
                Messages.showInfoMessage("iOS Simulator starting... ($simulatorChoice)", "Success")
            } catch (e: Exception) {
                Messages.showErrorDialog("Failed to start iOS Simulator: ${e.message}", "Error")
            }
        }
    }

    private fun getAvailableIosSimulators(): List<String> {
        return try {
            val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "--json")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            // Parse JSON to extract simulator names (simplified for brevity)
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(output).map { it.groupValues[1] }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
