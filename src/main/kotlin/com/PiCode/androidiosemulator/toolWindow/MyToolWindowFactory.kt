package com.PiCode.androidiosemulator.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.PiCode.androidiosemulator.MyBundle
import com.PiCode.androidiosemulator.services.MyProjectService
import java.io.File
import javax.swing.JButton

class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()
        private val propertiesComponent = PropertiesComponent.getInstance()

        companion object {
            private const val SDK_KEY = "android.sdk.path"
        }

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle.message("randomLabel", "?"))

            add(label)

            // Unified Mobile Emulator Button with Theme-Specific Icon
            add(JButton(IconLoader.getIcon("/icons/mobile.svg", javaClass)).apply {
                toolTipText = "Choose Emulator"
                addActionListener {
                    val options = arrayOf("Android Emulator", "iOS Simulator")
                    val choice = Messages.showChooseDialog(
                        "Select emulator type",
                        "Emulator Selection",
                        options,
                        options[0],
                        null
                    )

                    when (choice) {
                        0 -> startAndroidEmulator(label)
                        1 -> startIosSimulator(label)
                    }
                }
            })
        }

        private fun startAndroidEmulator(label: JBLabel) {
            var sdkPath = propertiesComponent.getValue(SDK_KEY)

            // Try to auto-detect SDK path if not set
            if (sdkPath.isNullOrEmpty()) {
                sdkPath = detectAndroidSdkPath()
                if (sdkPath != null) {
                    // Save the detected path for future use
                    propertiesComponent.setValue(SDK_KEY, sdkPath)
                }
            }

            if (sdkPath == null) {
                label.text = "Android SDK path not set and could not be auto-detected."
                Messages.showErrorDialog(
                    "Android SDK path is not set. Please configure it in the settings under 'File > Settings > Android-iOS Emulator'.",
                    "Error"
                )
                return
            }

            val emulatorPath = getEmulatorPath(sdkPath)
            val avdList = getAvailableAvds(emulatorPath)
            if (avdList.isEmpty()) {
                label.text = "No AVDs found. Please create an AVD in Android Studio."
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
                    label.text = "Android Emulator starting... ($avdChoice)"
                } catch (e: Exception) {
                    label.text = "Failed to start Android Emulator: ${e.message}"
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

        private fun startIosSimulator(label: JBLabel) {
            val osName = System.getProperty("os.name").lowercase()
            if (!osName.contains("mac")) {
                label.text = "iOS Simulator is only available on macOS."
                return
            }

            val simulatorList = getAvailableIosSimulators()
            if (simulatorList.isEmpty()) {
                label.text = "No iOS Simulators found."
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
                    label.text = "iOS Simulator starting... ($simulatorChoice)"
                } catch (e: Exception) {
                    label.text = "Failed to start iOS Simulator: ${e.message}"
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
}
