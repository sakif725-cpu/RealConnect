# RealConnect 📞🤖
> **Next-Generation AI-Enhanced Real-Time VoIP Audio Calling for Android**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![SDK](https://img.shields.io/badge/API-24%2B%20(Android%207.0%2B)-brightgreen.svg)](https://android.com)
[![WebRTC](https://img.shields.io/badge/WebRTC-v125.6422.06-blue.svg)](https://webrtc.org/)
[![Firebase](https://img.shields.io/badge/Firebase-Realtime%20DB-orange.svg?logo=firebase)](https://firebase.google.com/)

**RealConnect** is a native Android communication platform combining low-latency, peer-to-peer (P2P) **WebRTC audio calling** with an active **AI-driven security guard** for real-time spam filtering, synthetic bot voice detection, and speaker identity verification.

---

## ✨ Features

* 🎙️ **Peer-to-Peer Voice Calling:** Ultra-low latency voice communication powered by the official WebRTC Android SDK.
* ⚡ **Realtime Cloud Signaling:** Seamless SDP offer/answer exchange and ICE candidate trickle via Firebase Realtime Database.
* 🛡️ **AI Pre-Call Spam Detection:** Scans caller IDs against spam intelligence before answering, showing real-time warning indicators.
* 🤖 **AI In-Call Bot & Deepfake Detection:** Live voice analysis to detect automated robocallers and AI-generated synthetic voices.
* 👤 **Biometric Speaker Verification:** Validates caller voice identity against the registered contact profile during active calls.
* 📇 **Offline-First Contact Management:** Fast local contacts storage, real-time search, and CRUD operations powered by Android Jetpack Room DB.
* 🎨 **Modern Material 3 UI:** Dark-themed UI with in-call controls (Mute, Speakerphone, DTMF dial pad, AI Mode toggle).

---

## 🏗️ Architecture & Flow

```mermaid
flowchart TD
    subgraph Client ["Android App (RealConnect)"]
        UI["Calling Screen / Dialpad"]
        WebRTC["WebRTC Audio Engine"]
        Room["Jetpack Room DB (Contacts)"]
        AiClient["AI Service (Retrofit/OkHttp)"]
    end

    subgraph Firebase ["Firebase Cloud"]
        Signal["Realtime DB (Signaling Nodes)"]
    end

    subgraph AIBackend ["AI Microservice"]
        SpamAPI["/spam-check"]
        BotAPI["/voice-analysis"]
        VerifyAPI["/verify-speaker"]
    end

    UI -->|Dial / Receive| WebRTC
    WebRTC <-->|SDP Offer/Answer & ICE Trickle| Signal
    WebRTC <==|Direct P2P Audio Stream| Peer["Remote Peer Device"]
    AiClient -->|Pre-call Check| SpamAPI
    AiClient -->|In-call Audio Sample| BotAPI
    AiClient -->|Voice Biometrics| VerifyAPI
```

---

## 🛠️ Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Platform** | Java 11, Android SDK (minSdk: 24, targetSdk: 35, compileSdk: 35) |
| **Build System** | Gradle (Kotlin DSL `build.gradle.kts`), Version Catalogs (`libs.versions.toml`) |
| **VoIP / Media Engine** | WebRTC Android SDK (`io.github.webrtc-sdk:android:125.6422.06`) |
| **Signaling Server** | Firebase Realtime Database (`com.google.firebase:firebase-database`) |
| **Networking & REST** | Retrofit 2.9.0, Gson Converter, OkHttp Logging Interceptor |
| **Local Storage** | Jetpack Room SQLite Database (`2.6.1`), SharedPreferences, SAF API |
| **UI Components** | Google Material Design 3, ConstraintLayout, Edge-to-Edge Navigation |

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio** (Koala / Ladybug or newer)
* **JDK 11** or newer
* **Android Device or Emulator** running Android 7.0 (API 24) or higher

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/sakif725-cpu/RealConnect.git
   cd RealConnect
   ```

2. **Open in Android Studio:**
   * Open Android Studio -> Select **Open** -> Choose the `RealConnect` directory.
   * Allow Gradle to sync dependencies automatically.

3. **Firebase Setup:**
   * Ensure `google-services.json` is located in the `app/` folder.
   * Enable **Realtime Database** in your Firebase Console.

4. **Run the App:**
   * Connect an Android device or launch an emulator.
   * Click **Run** (`Shift + F10`) in Android Studio.

---

## 🤖 AI Backend Integration

The app communicates with an AI backend via REST endpoints defined in `app/src/main/java/com/realconnect/app/AiApiService.java`.

To point the app to your AI service:
1. Open `app/src/main/java/com/realconnect/app/AiService.java`.
2. Update `BASE_URL`:
   ```java
   private static final String BASE_URL = "https://your-ai-backend.com/";
   ```

### Required Endpoints

| Endpoint | Method | Input | Output | Description |
| :--- | :--- | :--- | :--- | :--- |
| `/spam-check` | `GET` | `?number=+123456789` | `{"isSpam": bool, "reason": str}` | Pre-call spam classification |
| `/voice-analysis` | `POST` | `{"audioBase64": str}` | `{"isBot": bool, "confidence": float}` | Synthetic / AI bot voice detection |
| `/verify-speaker` | `POST` | `{"phoneNumber": str, "audioBase64": str}` | `{"verified": bool, "status": str}` | Voice biometric verification |
