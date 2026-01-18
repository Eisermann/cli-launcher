# CLI Link - JetBrains Plugin Base

This repository provides a **cross-platform** IntelliJ Platform plugin base that keeps popular AI CLIs one click away inside JetBrains IDEs.

It is designed to work consistently on **macOS, Windows, and Linux**, and to support the same workflow across the following CLI implementations:
- **OpenAI Codex CLI**
- **Google Gemini CLI**
- **OpenCode CLI**

If you are looking for a ready-to-install plugin, use one of the implementation branches (links below) or download a ZIP from GitHub Releases.

## 🔌 Implementations

Each implementation is maintained on its own branch:

- Codex Link: https://github.com/Eisermann/cli-link/tree/codex-link
- Gemini Link: https://github.com/Eisermann/cli-link/tree/gemini-link
- OpenCode Link: https://github.com/Eisermann/cli-link/tree/opencode-link

Downloads (plugin ZIPs):
- https://github.com/Eisermann/cli-link/releases

## ✨ Common Features

All plugins built on this base share the same core behavior:

- One-click launch from the toolbar or **Tools** menu
- Integrated terminal that opens a dedicated tab in the project root
- Completion notifications after the CLI finishes a run
- Automatic file opening for files updated by the CLI
- Built-in MCP server pairing with guided setup for IntelliJ's MCP server (IDE 2025.2+)
- Flexible settings for launch mode, models, and notification behavior

## 📋 Prerequisites

- JetBrains IDE based on IntelliJ Platform (typically **2024.2+**)
- The target CLI installed separately and available on your `PATH`:
  - Codex CLI: https://github.com/openai/codex
  - Gemini CLI: https://github.com/google/gemini-cli
  - OpenCode CLI: https://github.com/anomalyco/opencode

Windows note:
- On Windows, you may need to select the terminal shell in the plugin settings to ensure proper command execution.

## 🚀 Usage

1. Click the plugin button in the main toolbar, or use **Tools** → **Launch ...**.
2. The plugin opens an IDE terminal tab and runs the CLI in your project root.
3. When the CLI finishes, the plugin can notify you and optionally open changed files.

## 🔧 Development

This repo is a workspace of IntelliJ Platform plugins. Build from within the target implementation module/branch.

Example (build a distributable plugin ZIP):

```bash
./gradlew buildPlugin
```

To run the plugin in a sandbox IDE:

```bash
./gradlew runIde
```

## 🙏 Credits

This plugin base and its derivatives are based on the Codex Launcher by x0x0b:
- https://github.com/x0x0b/codex-launcher

## 📄 License

See `LICENSE` in the selected implementation branch/module.
