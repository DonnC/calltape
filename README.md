# 📼 CallTape

> **Turn any Android POS terminal into an offline, real-time call transcript ticker tape machine.**

**CallTape** is an open-source Android application built with Kotlin and Jetpack Compose. It operates as an offline call recorder and real-time speech-to-text engine, pushing live conversation transcripts directly to hardware thermal printers (or a virtual log stream) as you speak.

---

## 🌟 Features

* **Dialer Bypass & Dual-SIM Support:** Built-in dialer using `TelecomManager` to initiate calls even on headless or vendor-stripped Android POS ROMs.
* **100% Offline Speech-to-Text:** Powered by [Vosk](https://alphacephei.com/vosk/), running completely on-device with zero cloud dependencies or API costs.
* **Real-time Speaker Diarization:** Uses Cosine Similarity on speech x-vectors to dynamically separate dialogue (`Caller 1` vs `Caller 2`).
* **Ticker-Tape Thermal Printing:** Decoupled `PosPrinter` interface capable of printing dialogue line-by-line during an active call.
* **In-Memory Call History:** Session-based call records with phone numbers, timestamps, durations, and full transcript re-printing.
* **Hardware Agnostic:** Defaults to an in-memory `MockPrinterImpl` for local testing on standard emulators and phones.

---

## 🏗️ Architecture Overview

```text
[ SIM / TelecomManager ] ──► [ AudioRecord Stream ]
                                      │
                                      ▼
                           [ Vosk STT Engine ]
                                      │
                       ┌──────────────┴──────────────┐
                       ▼                             ▼
              [ Speaker Vector ]           [ Finalized Text ]
                       │                             │
                       └──────────────┬──────────────┘
                                      ▼
                        [ Dynamic Speaker Tagging ]
                                      │
                                      ▼
                            [ PosPrinter Interface ]
                                   │       │
             ┌─────────────────────┘       └─────────────────────┐
             ▼                                                   ▼
  [ Vendor SDK (e.g. Morefun) ]                       [ MockPrinterImpl ]
   (Physical Thermal Roll)                             (Logcat & In-Memory)

```

---

## 🛠️ Getting Started

### 1. Clone the Repository

```bash
git clone [calltape](https://github.com/DonnC/calltape.git)
cd calltape

```

### 2. Download Vosk Models

Because speech models are large binary files, they are omitted from this repository via `.gitignore`.

1. Download the lightweight English model: [`vosk-model-small-en-us-0.15.zip`](https://alphacephei.com/vosk/models)
2. Download the speaker identification model: [`vosk-model-spk-0.4.zip`](https://alphacephei.com/vosk/models)
3. Extract both ZIP archives.
4. Place the extracted folders inside your Android project under `app/src/main/assets/`:

```text
app/src/main/assets/
├── model-en-us/       <-- Extracted speech model
└── spk-model/         <-- Extracted speaker model

```

Rename the models to match the folder structure below
```text
app/
└── src/
    └── main/
        ├── assets/
        │   ├── model-en-us/
        │   │   ├── am/
        │   │   ├── conf/
        │   │   ├── graph/
        │   │   └── ivector/
        │   └── spk-model/
        │       ├── subsegment_mean
        │       ├── transform.mat
        │       └── final.raw
        ├── java/
        └── res/
```

### 3. Build & Run

Open the project in **Android Studio** and run it on an emulator or physical device.

By default, the app uses `MockPrinterImpl`. Watch your `Logcat` filter for `CallTape-MockPrinter` to see real-time thermal roll outputs as you speak!

---

## 🔌 Implementing Custom POS Hardware Printers

To add physical printing support for your specific POS hardware (Morefun, Sunmi, Pax, Nexgo, etc.), implement the `PosPrinter` interface and replace the active implementation:

```kotlin
class MyCustomPosPrinter(private val vendorSdk: VendorPrinterManager) : PosPrinter {

    override fun printLine(text: String) {
        vendorSdk.printText("$text\n")
    }

    override fun printFullTranscript(transcript: String) {
        vendorSdk.printText("=== CALL TRANSCRIPT ===\n")
        vendorSdk.printText(transcript)
        cutPaper()
    }

    override fun cutPaper() {
        vendorSdk.paperCut()
    }
}

```

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

