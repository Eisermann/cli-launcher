# OpenCode Launcher - GEMINI Context

## Project Overview
**OpenCode Launcher** is an unofficial IntelliJ IDEA plugin designed to integrate the OpenCode CLI directly into the IDE. It provides a one-click solution to launch a dedicated terminal tab running `opencode`, along with completion notifications, automatic file opening, and MCP server integration.

**Key Technologies:**
*   **Language:** Kotlin (JVM Target 21)
*   **Framework:** IntelliJ Platform SDK
*   **Build System:** Gradle (Kotlin DSL)
*   **Dependencies:** `org.jetbrains.plugins.terminal`, `com.google.code.gson`

## Building and Running
This project uses the IntelliJ Platform Gradle Plugin.

*   **Build Plugin:**
    ```bash
    ./gradlew buildPlugin
    ```
*   **Run IDE with Plugin:**
    ```bash
    ./gradlew runIde
    ```
    This starts a sandboxed instance of IntelliJ IDEA with the plugin installed.
*   **Run Tests:**
    ```bash
    ./gradlew test
    ```
*   **Clean:**
    ```bash
    ./gradlew clean
    ```

## Development Conventions

### Project Structure
*   **`src/main/kotlin/.../actions/`**: Contains `LaunchOpenCodeAction`, the entry point for the user interaction.
*   **`src/main/kotlin/.../cli/`**: Logic for building CLI arguments (`OpenCodeArgsBuilder`).
*   **`src/main/kotlin/.../settings/`**: Persistent state and configuration UI (`OpenCodeLauncherSettings`, `OpenCodeLauncherConfigurable`).
*   **`src/main/kotlin/.../terminal/`**: Manages the IDE's terminal tool window (`OpenCodeTerminalManager`).
*   **`src/main/resources/META-INF/plugin.xml`**: The plugin manifest file defining extensions, actions, and dependencies.

### Configuration
*   **Gradle:** `build.gradle.kts` configures the IntelliJ Platform version (currently targeting `2025.2`) and Kotlin version (`2.2.10`).
*   **Plugin ID:** `com.github.eisermann.opencode-launcher`
