# TinyJPG Compressor Changelog

## [1.0.1](https://github.com/zhuweiyou/tinyjpg-idea-plugin/releases/tag/v1.0.1)

### Fixed

- Replaced the deprecated `ToolWindowManager.registerToolWindow` overload with the supported builder API.
- Removed the duplicate percent sign from compression ratio output in the console.
- Aligned the JVM toolchain and GitHub Actions JDK with Java 21 to support the IntelliJ Platform's inline API.

## [1.0.0](https://github.com/zhuweiyou/tinyjpg-idea-plugin/releases/tag/v1.0.0)

### Added

- Initial release of TinyJPG Compressor for IntelliJ IDEA.
- Right-click compression for JPEG, PNG, and WebP images in the Project view.
- Recursive image collection from selected directories, skipping files larger than 5 MB.
- Concurrent compression of up to two images with atomic file replacement.
- A dedicated console for progress, size changes, and errors, plus completion notifications.
