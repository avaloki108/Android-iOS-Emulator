package com.PiCode.androidiosemulator.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import com.PiCode.androidiosemulator.settings.EmulatorSettingsConfigurable
import java.io.File

class EmulatorAction : AnAction() {

    companion object {
        private const val SDK_KEY = "android.sdk.path"
    }

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
        val propertiesComponent = PropertiesComponent.getInstance()
        var sdkPath = propertiesComponent.getValue(SDK_KEY)

        // Try to auto-detect SDK path if not set
        if (sdkPath.isNullOrEmpty()) {
            sdkPath = detectAndroidSdkPath()
            if (sdkPath != null) {
                // Save the detected path for future use
                propertiesComponent.setValue(SDK_KEY, sdkPath)
            }
        }

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

        val emulatorPath = getEmulatorPath(sdkPath)
        val avdList = getAvailableAvds(emulatorPath)

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

    /**
     * Detects the Android SDK path from common locations and environment variables.
     */
    private fun detectAndroidSdkPath(): String? {
        // Check ANDROID_HOME environment variable first
        System.getenv("ANDROID_HOME")?.let { path ->
            if (File(path).exists()) return path
        }

        // Check ANDROID_SDK_ROOT environment variable
        System.getenv("ANDROID_SDK_ROOT")?.let { path ->
            if (File(path).exists()) return path
        }

        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        // Check common SDK locations based on OS
        val commonPaths = when {
            osName.contains("win") -> listOf(
                "$userHome\\AppData\\Local\\Android\\Sdk",
                "C:\\Android\\Sdk",
                "$userHome\\Android\\Sdk"
            )
            osName.contains("mac") -> listOf(
                "$userHome/Library/Android/sdk",
                "/Users/Shared/Android/sdk"
            )
            else -> listOf(
                "$userHome/Android/Sdk",
                "/opt/android-sdk",
                "/usr/local/android-sdk"
            )
        }

        for (path in commonPaths) {
            val sdkDir = File(path)
            if (sdkDir.exists() && sdkDir.isDirectory) {
                // Verify it's a valid SDK by checking for emulator directory
                val emulatorDir = File(sdkDir, "emulator")
                if (emulatorDir.exists()) {
                    return path
                }
            }
        }

        return null
    }

    /**
     * Returns the correct emulator executable path based on the OS.
     */
    private fun getEmulatorPath(sdkPath: String): String {
        val osName = System.getProperty("os.name").lowercase()
        val separator = File.separator
        return if (osName.contains("win")) {
            "${sdkPath}${separator}emulator${separator}emulator.exe"
        } else {
            "${sdkPath}${separator}emulator${separator}emulator"
        }
    }

    private fun getAvailableAvds(emulatorPath: String): List<String> {
        return try {
            val process = ProcessBuilder(emulatorPath, "-list-avds")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                return emptyList()
            }
            process.inputStream.bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.startsWith("INFO") && !it.startsWith("WARNING") && !it.contains(":") }
        } catch (e: Exception) {
            emptyList()
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
