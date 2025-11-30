package com.PiCode.androidiosemulator.util

import com.intellij.ide.util.PropertiesComponent
import java.io.File

/**
 * Utility class for Android SDK and emulator operations.
 */
object AndroidSdkUtils {

    const val SDK_KEY = "android.sdk.path"

    /**
     * Gets the Android SDK path, attempting auto-detection if not already configured.
     * If auto-detected, the path is saved for future use.
     */
    fun getOrDetectSdkPath(): String? {
        val propertiesComponent = PropertiesComponent.getInstance()
        var sdkPath = propertiesComponent.getValue(SDK_KEY)

        if (sdkPath.isNullOrEmpty()) {
            sdkPath = detectAndroidSdkPath()
            if (sdkPath != null) {
                propertiesComponent.setValue(SDK_KEY, sdkPath)
            }
        }

        return sdkPath
    }

    /**
     * Detects the Android SDK path from common locations and environment variables.
     */
    fun detectAndroidSdkPath(): String? {
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
        val separator = File.separator

        // Check common SDK locations based on OS
        val commonPaths = when {
            osName.contains("win") -> listOf(
                "$userHome${separator}AppData${separator}Local${separator}Android${separator}Sdk",
                "C:${separator}Android${separator}Sdk",
                "$userHome${separator}Android${separator}Sdk"
            )
            osName.contains("mac") -> listOf(
                "$userHome${separator}Library${separator}Android${separator}sdk",
                "${separator}Users${separator}Shared${separator}Android${separator}sdk"
            )
            else -> listOf(
                "$userHome${separator}Android${separator}Sdk",
                "${separator}opt${separator}android-sdk",
                "${separator}usr${separator}local${separator}android-sdk"
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
    fun getEmulatorPath(sdkPath: String): String {
        val osName = System.getProperty("os.name").lowercase()
        val separator = File.separator
        return if (osName.contains("win")) {
            "${sdkPath}${separator}emulator${separator}emulator.exe"
        } else {
            "${sdkPath}${separator}emulator${separator}emulator"
        }
    }

    /**
     * Gets the list of available AVDs from the Android emulator.
     * Filters out info/warning messages that may be in the output.
     */
    fun getAvailableAvds(emulatorPath: String): List<String> {
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
