# Project Plan

CallTape: A custom Android POS terminal app acting as a dialer, offline call recorder, and real-time speech-to-text transcriber pushing dialogue to a thermal printer interface.

Key Features:
- Project Setup: Compose, Coroutines, Vosk STT, JNA.
- Data: In-memory Singleton repository with MutableStateFlow for CallRecord (id, phoneNumber, timestamp, duration, transcript, simSlot).
- Printer: PosPrinter interface and MorefunPrinterImpl (TODOs).
- Service: CallTranscriptionService (Foreground, microphone) using TelephonyCallback.
  - Starts Vosk on OFFHOOK, stops and saves on IDLE.
  - RecognitionListener for STT and speaker diarization (Cosine Similarity on 'spk' vector, threshold 0.65).
  - Real-time printing of finalized sentences.
- UI: Jetpack Compose, Material 3.
  - HistoryScreen: List of calls, print action, clear history, Dialer FAB.
  - DialerScreen: Input field, SIM selection using TelecomManager, ACTION_CALL.
  - ViewModels and Previews.
- Permissions: CALL_PHONE, READ_PHONE_STATE, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE.

## Project Brief

# Project Brief: CallTape

CallTape is a specialized Android application designed for POS terminals, functioning as an integrated dialer, offline call recorder, and real-time speech-to-text transcriber that outputs dialogue directly to a thermal printer.

## Features

1.  **Integrated Smart Dialer**: A dedicated dialer interface supporting real-time call initiation and dual-SIM selection via `TelecomManager`, optimized for POS workflows.
2.  **Offline Call Transcription Service**: A privacy-focused foreground service that utilizes the Vosk STT engine to transcribe active calls in real-time without requiring an internet connection.
3.  **Automated Speaker Diarization**: Real-time participant identification using voice vector comparison (Cosine Similarity) to accurately attribute speech to the correct speaker in the transcript.
4.  **Direct Thermal Printing**: Real-time dialogue output to the POS terminal's thermal printer interface, providing immediate physical records of transcribed conversations.
5.  **Call History Registry**: A searchable list of all handled calls, providing quick access to metadata (SIM slot, duration, timestamp) and full transcripts.

## High-Level Tech Stack

*   **Kotlin**: Primary programming language for modern, safe Android development.
*   **Jetpack Compose (Material 3)**: Declarative UI framework for building the Dialer and History screens.
*   **Jetpack Navigation 3**: State-driven navigation architecture for seamless transitions between app states.
*   **Compose Material Adaptive**: Implementation of responsive layouts ensuring compatibility across various POS device display sizes.
*   **Vosk STT & JNA**: Offline speech recognition engine with Java Native Access for low-latency audio processing.
*   **Kotlin Coroutines & Flow**: Asynchronous programming models for managing concurrent audio streams, transcription events, and printer queues.
*   **Morefun YSDK (AIDL/JAR)**: Hardware-specific integration for controlling integrated thermal printers on Morefun POS terminals. Only put an IPrinter interface, i will only add the morefun sdk when we are done.

## Implementation Steps
**Total Duration:** 35m 18s

### Task_1_Infrastructure: Initialize project structure, add dependencies, and configure AndroidManifest.
- **Status:** COMPLETED
- **Duration:** 2m 12s

### Task_2_TranscriptionAndHardware: Implement CallTranscriptionService and Printer interfaces.
- **Status:** COMPLETED
- **Duration:** 10m 59s

### Task_3_UserInterface: Develop Dialer and History screens.
- **Status:** COMPLETED
- **Duration:** 4m 48s

### Task_4_PackageAndMockPrinter: Refactor to zw.co.donnclab.calltape and implement MockPrinter.
- **Status:** COMPLETED
- **Duration:** 6m 22s

### Task_5_UIOverhaulAndVerify: Modernize UI and verify.
- **Status:** COMPLETED
- **Duration:** 5m 17s

### Task_6_RefinementsAndAlignment: Add SIM selection row to Dialer, integrate SIM slot in CallRecord, and fix Android 16KB page alignment.
- **Status:** COMPLETED
- **Duration:** 3m 16s

### Task_7_StabilityAndPermissions: Implement runtime permission handling and ensure CallTranscriptionService is started as a foreground service.
- **Status:** COMPLETED
- **Updates:** - Implemented comprehensive runtime permission handling in MainActivity.
- Added a PermissionRequiredScreen to ensure users grant Phone and Audio permissions before app usage.
- Integrated Foreground Service start logic in MainActivity.
- Added crash protection and permission checks in DialerScreen to handle missing Telephony permissions gracefully.
- Verified build stability.
- **Acceptance Criteria:**
  - Runtime permissions (Phone, Audio) requested on startup
  - CallTranscriptionService starts successfully
  - DialerScreen does not crash due to missing permissions
- **Duration:** 2m 24s

