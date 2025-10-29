# Halo Chat

<p align="center">
  <img src="app/src/main/res/mipmap/ic_launcher.xml" alt="Halo Chat Logo" width="120" height="120">
</p>

<p align="center">
  <strong>Advanced AI Assistant in Your Pocket</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#installation">Installation</a> •
  <a href="#usage">Usage</a> •
  <a href="#development">Development</a> •
  <a href="#contributing">Contributing</a> •
  <a href="#license">License</a>
</p>

## Overview

Halo Chat is a cutting-edge Android application that brings the power of Large Language Models (LLMs) directly to your mobile device. With on-device inference capabilities, Halo Chat provides intelligent assistance while maintaining your privacy and working offline when needed.

## Features

- **On-Device AI**: Run powerful language models directly on your device without constant cloud connectivity
- **Intelligent Conversations**: Engage in natural, context-aware conversations with the AI assistant
- **Privacy-Focused**: Your data stays on your device, ensuring maximum privacy
- **Offline Capability**: Continue using core features even without an internet connection
- **Low Latency**: Experience fast response times with optimized C++ inference engine
- **Customizable Experience**: Tailor the AI assistant to your preferences and needs

## Architecture

Halo Chat is built with a modern Android architecture:

- **Kotlin & Jetpack Compose**: For a reactive and declarative UI
- **MVVM Pattern**: Clean separation of concerns with ViewModel components
- **Room Database**: Efficient local storage for conversations and settings
- **C++ Native Layer**: High-performance LLM inference with JNI bridge
- **Coroutines & Flow**: Asynchronous programming for smooth user experience

## Installation

### Requirements
- Android 8.0 (API level 26) or higher
- At least 4GB of RAM
- 2GB of free storage space

### Download
1. Download the latest APK from the [Releases](https://github.com/snowey7809/halochat/releases) page

## Usage

### Getting Started
1. Launch the app and complete the initial setup
2. Choose your preferred AI model based on your device capabilities
3. Start a new conversation by tapping the "+" button
4. Type your message or use voice input to interact with Halo Chat

### Tips for Best Experience
- For complex queries, provide clear and specific information
- Enable "Advanced Mode" in settings for more detailed responses
- Use the built-in prompt templates for specialized assistance

## Development

### Setup Development Environment
1. Clone the repository
   ```bash
   git clone https://github.com/snowey7809/halochat.git
   cd halochat
   ```

2. Open the project in Android Studio

3. Install required dependencies
   ```bash
   ./gradlew build
   ```

### Project Structure
- `app/src/main/java` - Kotlin source files
- `app/src/main/cpp` - C++ native code for LLM inference
- `app/src/main/res` - UI resources and layouts

## Contributing

We welcome contributions to Halo Chat! Whether it's bug reports, feature requests, or code contributions, please feel free to reach out.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

The MIT License is a permissive open source license that allows you to:
- Use the code commercially
- Modify the code
- Distribute the code
- Use and modify the code privately

The only requirement is that the original copyright and license notice must be included in any copy of the software/source.

---

<p align="center">
  Made with ❤️ by the Halo Chat Team
</p>