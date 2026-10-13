# TinyJPG Compressor

Compress JPEG, PNG, and WebP images directly from the IntelliJ IDEA Project view, powered by [TinyJPG](https://tinyjpg.com).

[JetBrains Marketplace](https://plugins.jetbrains.com/plugin/33239-tinyjpg-compressor) · [GitHub Releases](https://github.com/zhuweiyou/tinyjpg-idea-plugin/releases) · [Changelog](CHANGELOG.md)

## Installation

### JetBrains Marketplace

Install from the [TinyJPG Compressor plugin page](https://plugins.jetbrains.com/plugin/33239-tinyjpg-compressor), or from the IDE:

1. Open **Settings / Preferences → Plugins → Marketplace**.
2. Search for **TinyJPG Compressor**.
3. Click **Install** and restart the IDE if prompted.

### GitHub Releases

1. Open [GitHub Releases](https://github.com/zhuweiyou/tinyjpg-idea-plugin/releases).
2. Download the plugin ZIP from the release's **Assets** section, not the source code archive.
3. Open **Settings / Preferences → Plugins → ⚙ → Install Plugin from Disk…**.
4. Select the downloaded ZIP without extracting it, and restart the IDE if prompted.

End users do not need to install a separate JDK; the plugin runs on the IDE's bundled runtime.

## Usage

1. Select image files or directories in the **Project** view.
2. Right-click and choose **TinyJPG Compress**.
3. Monitor progress and results in the **TinyJPG Console** tool window at the bottom of the IDE.
4. Check the completion notification for the number of successful and failed files.

Images are uploaded to TinyJPG for compression, and successful results replace the original files in place. Keep a backup or commit your images to version control before compressing them.

## Features

- Supports `.jpg`, `.jpeg`, `.png`, and `.webp` images.
- Recursively collects images from selected directories.
- Skips unsupported files and images larger than 5 MB.
- Processes up to two images concurrently.
- Displays progress, size changes, and errors in a dedicated console.
- Uses atomic file replacement when saving compressed images.

## Development

### Requirements

- JDK 21.
- Internet access to download Gradle, the IntelliJ Platform SDK, and build dependencies.

The project uses the Gradle wrapper and builds against IntelliJ IDEA 2025.3.3. A separate Gradle installation is not required.

### Build the plugin

macOS / Linux:

```bash
./gradlew buildPlugin
```

Windows PowerShell:

```powershell
.\gradlew.bat buildPlugin
```

The plugin ZIP is generated in `build/distributions/`. Install it using **Install Plugin from Disk…** as described above.

### Run a development IDE

macOS / Linux:

```bash
./gradlew runIde
```

Windows PowerShell:

```powershell
.\gradlew.bat runIde
```

## Release and Publishing

### GitHub Releases

The [release workflow](https://github.com/zhuweiyou/tinyjpg-idea-plugin/actions/workflows/release.yml) is triggered manually:

1. Update the project version in [gradle.properties](gradle.properties) and the [Changelog](CHANGELOG.md), then push the changes.
2. Run the workflow on the intended branch, providing a matching tag such as `v1.0.1` and a release description.
3. The workflow builds the plugin, creates and pushes the tag, and publishes a GitHub Release with the plugin ZIP.

Pushing a commit alone does not trigger this workflow. The workflow does not publish to JetBrains Marketplace.

### JetBrains Marketplace

To publish to Marketplace, configure `publishToken` in your user-level `~/.gradle/gradle.properties`, or set the `PUBLISH_TOKEN` environment variable. Never commit the token to the repository.

```bash
./gradlew publishPlugin
```

On Windows, use `.\gradlew.bat publishPlugin`.

## License

MIT
