package com.PiCode.androidiosemulator.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.File

class StartAndroidEmulatorAction : AnAction("Start Android Emulator") {

    companion object {
        private const val SDK_KEY = "android.sdk.path"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val propertiesComponent = PropertiesComponent.getInstance()
        var sdkPath = propertiesComponent.getValue(SDK_KEY)

        // Try to auto-detect SDK path if not set
        if (sdkPath.isNullOrEmpty()) {
            sdkPath = detectAndroidSdkPath()
            if (sdkPath != null) {
                propertiesComponent.setValue(SDK_KEY, sdkPath)
            }
        }

        if (sdkPath.isNullOrEmpty()) {
            Messages.showErrorDialog(
                "Android SDK path is not set and could not be auto-detected. Please configure it in 'File > Settings > Android-iOS Emulator'.",
                "SDK Path Not Found"
            )
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
            } catch (ex: Exception) {
                Messages.showErrorDialog("Failed to start Android Emulator: ${ex.message}", "Error")
            }
        }
    }

    private fun detectAndroidSdkPath(): String? {
        System.getenv("ANDROID_HOME")?.let { path ->
            if (File(path).exists()) return path
        }
        System.getenv("ANDROID_SDK_ROOT")?.let { path ->
            if (File(path).exists()) return path
        }

        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

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
                val emulatorDir = File(sdkDir, "emulator")
                if (emulatorDir.exists()) {
                    return path
                }
            }
        }
        return null
    }

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
}
