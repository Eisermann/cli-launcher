# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenCode Launcher is an unofficial IntelliJ IDEA plugin that integrates the OpenCode CLI into the IDE. The plugin provides one-click terminal launching, completion notifications, automatic file opening, and MCP server integration.

**Technology Stack:**
- Language: Kotlin 2.2.10
- Target: JVM 21
- Framework: IntelliJ Platform SDK 2025.2
- Build: Gradle with Kotlin DSL
- Plugin ID: `com.github.eisermann.opencode-launcher`

## Essential Commands

### Development
```bash
# Run IDE with plugin (sandboxed instance)
./gradlew runIde

# Build plugin distribution
./gradlew buildPlugin

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.github.eisermann.opencodelauncher.cli.OpenCodeArgsBuilderTest"
```

## Architecture

### Core Components

**Action Layer** (`actions/`)
- `LaunchOpenCodeAction`: Entry point for user interaction
- Handles dual behavior: launches OpenCode terminal OR inserts file paths when terminal active
- Icon/text changes dynamically based on terminal state (default icon vs active icon)

**Terminal Management** (`terminal/`)
- `OpenCodeTerminalManager`: Project-level service managing OpenCode terminal lifecycle
- Handles terminal reuse, focus management, command execution
- Metadata tracking via `Content` UserData keys (`OPENCODE_TERMINAL_KEY`, `OPENCODE_TERMINAL_RUNNING_KEY`)
- Reflection-based compatibility for terminal API variations

**MCP Server** (`mcp/`)
- `McpServer`: Application-level HTTP server implementing MCP protocol
- Provides SSE (Server-Sent Events) for notifications
- JSON-RPC endpoints: `initialize`, `tools/list`, `tools/call`
- Tools: `openDiff`, `closeDiff` for IDE diff view integration
- `DiscoveryService`: Creates MCP discovery files for OpenCode CLI
- `DiffToolService`: Manages diff view lifecycle
- `IdeContextTracker`: Tracks IDE state for context-aware operations

**CLI Arguments** (`cli/`)
- `OpenCodeArgsBuilder`: Constructs command-line arguments from settings
- Handles mode, model, reasoning effort, web search, project root flags
- Notify command integration for HTTP callback

**Settings** (`settings/`)
- `OpenCodeLauncherSettings`: Application-level persistent state
- `OpenCodeLauncherConfigurable`: Settings UI panel
- Options enums: `Mode`, `Model`, `ModelReasoningEffort`, `WinShell`

**Notification & HTTP** (`notifications/`, `http/`)
- `NotificationService`: IDE balloon notifications for OpenCode completion
- `HttpTriggerService`: HTTP server for receiving OpenCode completion callbacks
- `FileOpenService`: Handles automatic file opening based on OpenCode output

**Startup** (`startup/`)
- `OpenCodeStartupActivity`: Initializes HTTP service and MCP server on IDE startup

### Plugin Extension Points

**From `plugin.xml`:**
- `applicationConfigurable`: Settings panel registration
- `postStartupActivity`: HTTP/MCP server initialization
- `notificationGroup`: Balloon notification group (`OpenCodeLauncher`)
- Main toolbar action: `LaunchOpenCodeAction` in multiple toolbar locations

### Service Architecture

**Application-Level Services:**
- `OpenCodeLauncherSettings`: Settings persistence
- `HttpTriggerService`: OpenCode completion callback receiver
- `McpServer`: MCP protocol server for IDE integration
- `DiscoveryService`: MCP discovery file management
- `DiffToolService`: Diff view management
- `IdeContextTracker`: IDE context tracking

**Project-Level Services:**
- `OpenCodeTerminalManager`: Terminal lifecycle per project
- `FileOpenService`: File opening per project
- `NotificationService`: Notifications per project

### Terminal State Management

The plugin manages terminal state through UserData keys on terminal `Content`:
- `OPENCODE_TERMINAL_KEY`: Marks content as OpenCode terminal
- `OPENCODE_TERMINAL_RUNNING_KEY`: Tracks if command is executing
- `OPENCODE_TERMINAL_CALLBACK_KEY`: Tracks termination callback registration

Terminal reuse logic:
1. Check if OpenCode terminal exists
2. If exists and running → focus it
3. If exists and idle → reuse for new command
4. If not exists → create new terminal tab named "OpenCode"

### Windows Shell Handling

The plugin handles three Windows terminal shells via `WinShell` enum:
- `POWERSHELL_73_PLUS`: PowerShell 7.3+ (`$env:VAR='val'; cmd`)
- `POWERSHELL_LT_73`: PowerShell <7.3 (same syntax)
- `WSL`: Windows Subsystem for Linux (`export VAR=val && cmd`)

Environment variable injection differs by shell for MCP port configuration.

### MCP Integration

The plugin implements the MCP protocol for IDE ↔ OpenCode CLI integration:
- HTTP server on dynamic port (exposed via `OPENCODE_CLI_IDE_SERVER_PORT`)
- Discovery file at `opencode/ide/opencode-ide-server-$pid-$port.json`
- Authorization via bearer token in discovery file
- SSE endpoint for server-to-client notifications
- JSON-RPC for client-to-server tool calls

**MCP Tools:**
- `openDiff(filePath, newContent)`: Open diff view in IDE
- `closeDiff(filePath)`: Close diff view

## Development Patterns

### Settings Migration

The plugin handles legacy setting migration in `loadState()`:
```kotlin
if (state.isPowerShell73OrOver) {
    state.winShell = WinShell.POWERSHELL_73_PLUS
    state.isPowerShell73OrOver = false
}
```

Always maintain backward compatibility when adding new settings.

### Reflection for API Compatibility

Terminal API varies across IntelliJ versions. Use reflection for version-agnostic access:
```kotlin
private fun invokeIsCommandRunning(widget: TerminalWidget): Boolean? {
    return runCatching {
        val method = widget.javaClass.methods.firstOrNull {
            it.name == "isCommandRunning" && it.parameterCount == 0
        }
        method?.apply { isAccessible = true }?.invoke(widget) as? Boolean
    }.getOrNull()
}
```

### EDT Threading

UI updates must occur on Event Dispatch Thread:
```kotlin
ApplicationManager.getApplication().invokeLater {
    if (project.isDisposed) return@invokeLater
    // UI operations here
}
```

### Error Handling

Use `runCatching` for graceful degradation:
```kotlin
runCatching {
    // risky operation
}.onFailure { error ->
    logger.error("Context for debugging", error)
}
```

## Important Notes

### Prerequisites
- OpenAI OpenCode CLI must be installed separately and available in system PATH
- IntelliJ IDEA 2024.2+ (sinceBuild = "242")

### Platform-Specific Behavior
- Windows users must configure shell type in settings for proper environment variable handling
- macOS/Linux use standard shell (`export` syntax)

### File Path Insertion
When OpenCode terminal is active, the Launch OpenCode action switches to "Insert File Path" mode:
- Sends `relative/path/to/file.kt:startLine-endLine ` to terminal
- Supports line range selection in editor
- Falls back to file path only if no selection

### HTTP Callback Flow
1. Plugin starts `HttpTriggerService` on random port
2. Port passed to OpenCode CLI via `--notify` flag
3. OpenCode CLI sends completion notification to HTTP endpoint
4. Plugin shows balloon notification and opens modified files

### MCP Server Flow
1. Plugin starts `McpServer` on random port at IDE startup
2. Creates discovery file in `opencode/ide/` with port and auth token
3. OpenCode CLI connects via SSE and JSON-RPC
4. Plugin receives tool calls (openDiff, closeDiff)
5. Plugin sends notifications to CLI via SSE

### Disposal Pattern
Services implementing `Disposable` must clean up resources:
- Stop HTTP servers
- Close SSE connections
- Shutdown thread pools

## Testing Strategy

Tests are in `src/test/kotlin/com/github/eisermann/opencodelauncher/`:
- `cli/OpenCodeArgsBuilderTest.kt`: CLI argument construction logic
- Uses JUnit 4 framework
- Integration tests via `testFramework(Platform)` in Gradle

## Publishing

Plugin is published to JetBrains Marketplace. Required environment variables:
- `PUBLISH_TOKEN`: Marketplace API token
- `CERTIFICATE_CHAIN`: Plugin signing certificate
- `PRIVATE_KEY`: Plugin signing private key
- `PRIVATE_KEY_PASSWORD`: Private key password
