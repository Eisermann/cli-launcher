# OpenCode Link - IntelliJ Plugin

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/eisermann/cli-link/releases)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-orange.svg)](https://www.jetbrains.com/idea/)

<img width="800" alt="The screenshot of OpenCode Link." src="https://github.com/user-attachments/assets/4ee3fbd8-e384-4672-94c6-e4e9041a8e0d" />

OpenCode Link is an **unofficial** IntelliJ IDEA plugin that keeps the OpenCode CLI one click away inside the IDE.

> **Credits:** This project was built upon a fork of [Codex Launcher](https://github.com/x0x0b/codex-launcher) by [x0x0b](https://github.com/x0x0b).

> **Important:** Install the [OpenCode CLI](https://github.com/anomalyco/opencode) separately before using this plugin.

> **For Windows users:** Please select your terminal shell in the plugin settings to ensure proper functionality. Go to _Settings (→ Other Settings) → OpenCode Link_.

## ✨ Features

- **One-click launch** from the toolbar or Tools menu
- **Integrated terminal** that opens a dedicated "OpenCode" tab in the project root
- **Completion notifications** after OpenCode CLI finishes processing the current run
- **Automatic file opening** for files updated by OpenCode
- **Built-in MCP server pairing** with guided setup for IntelliJ's MCP server (2025.2+)
- **Flexible configuration** for launch modes, models, and notifications

## 🛠️ Installation

### Prerequisites
- IntelliJ IDEA 2024.2 or later (or other compatible JetBrains IDEs)
- OpenCode CLI installed and available in your system PATH

### Installation
TBC

## 🚀 Usage

### Quick Start
1. Click the **Launch OpenCode** button in the main toolbar.
2. Or choose **Tools** → **Launch OpenCode**.
3. The integrated terminal opens a new "OpenCode" tab and runs `opencode` automatically.

### Configuration
Open **Settings (→ Other Settings) → OpenCode Link** to pick the launch mode, model, notification behavior, and auto-open options.

## 🔧 Development

### Building from Source
```bash
git clone https://github.com/eisermann/cli-link.git
git checkout opencode-link
./gradlew buildPlugin
```

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
