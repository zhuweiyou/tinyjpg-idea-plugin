# TinyJPG Idea Plugin

Right-click images or directories in Project View to compress JPEG, PNG, and WebP images with one click, powered by [TinyJPG](https://tinyjpg.com) official compression API.

## Features

- Supports JPG, JPEG, PNG, WebP formats
- Auto-recursively scan directories
- Auto-skips images larger than 5MB
- Atomic file replacement to prevent corruption on write failure
- Processes up to 2 images concurrently
- Dedicated console output panel showing compression progress and results

## Usage

1. Right-click on image files or directories in Project View
2. Select **TinyJPG Compress**
3. Monitor progress in the **TinyJPG Console** tool window (bottom)
4. Compressed files are atomically replaced in place

## Installation

- Download from [JetBrains Marketplace](https://plugins.jetbrains.com/) (coming soon)
- Or build from source and install from disk

## Build

```bash
./gradlew buildPlugin
```

Artifact: `build/distributions/tinyjpg-idea-plugin-*.zip`

## Development

```bash
# Run a test IDE instance with the plugin
./gradlew runIde
```

## Publishing

```bash
./gradlew publishPlugin
```

Requires `publishToken` in `~/.gradle/gradle.properties`.

## License

MIT