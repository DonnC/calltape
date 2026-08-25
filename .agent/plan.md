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
**Total Duration:** 29m 38s

### Task_1_Infrastructure: Initialize project structure, add dependencies (Compose, Lifecycle, Coroutines, Vosk, JNA, Morefun SDK), and configure AndroidManifest with necessary permissions (CALL_PHONE, READ_PHONE_STATE, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE). Define CallRecord data model and in-memory repository.
- **Status:** COMPLETED
- **Updates:** - Updated build.gradle.kts with Vosk, JNA, and Compose dependencies.
- **Acceptance Criteria:**
  - Dependencies successfully sync in build.gradle.kts
  - Permissions added to AndroidManifest.xml
  - CallRepository and CallRecord model implemented
- **Duration:** 2m 12s

### Task_2_TranscriptionAndHardware: Implement CallTranscriptionService as a foreground service. Integrate Vosk STT for offline transcription and TelephonyCallback for call state detection. Implement speaker diarization and the MorefunPrinterImpl for real-time thermal printing.
- **Status:** COMPLETED
- **Updates:** - Implemented PosPrinter interface and MorefunPrinterImpl.
- **Acceptance Criteria:**
  - Foreground service starts/stops based on call state
  - Vosk engine transcribes audio streams
  - Printer interface pushes data to thermal printer
  - Speaker diarization logic implemented
- **Duration:** 10m 59s

### Task_3_UserInterface: Develop the DialerScreen with a dial pad and SIM selection, and the HistoryScreen to display call records. Implement Jetpack Navigation 3 for screen transitions and connect UI to the data layer via ViewModels.
- **Status:** COMPLETED
- **Updates:** - Developed CallViewModel to bridge Data and UI.
- **Acceptance Criteria:**
  - DialerScreen allows number input and call initiation
  - HistoryScreen displays call logs from repository
  - Navigation between screens is seamless
  - UI follows Material 3 guidelines
- **Duration:** 4m 48s

### Task_4_PackageAndMockPrinter: Refactor the project to use the package 'zw.co.donnclab.calltape' throughout (Gradle, Manifest, and Source files). Implement the MockPrinterImpl using a MutableStateFlow to track and expose printed lines for testing.
- **Status:** COMPLETED
- **Updates:** - Consolidated project structure under 'zw.co.donnclab.calltape'.
- **Acceptance Criteria:**
  - Package renamed to 'zw.co.donnclab.calltape' in build.gradle.kts and AndroidManifest.xml
  - All source files and imports updated to the new package
  - MockPrinterImpl implemented and capturing print data in a StateFlow
  - Project builds successfully
- **Duration:** 6m 22s

### Task_5_UIOverhaulAndVerify: Overhaul the UI for a modern POS look, replacing text-based buttons with intuitive icons for actions like printing and clearing history. Perform final end-to-end verification, ensuring stability and alignment with requirements. Instruct critic_agent to verify app stability and requirements alignment.
- **Status:** COMPLETED
- **Updates:** - Redesigned HistoryScreen and DialerScreen with a modern, professional POS aesthetic.
- Replaced text-based buttons with intuitive icons (Print, Delete).
- Introduced a professional POS-inspired color palette and typography.
- Updated all UI components to use zw.co.donnclab.calltape package.
- Verified that @Previews are working and reflect the new design.
- **Acceptance Criteria:**
  - UI modernized with icons replacing text buttons
  - Build pass
  - App does not crash
  - All existing tests pass
  - Critic agent verifies application stability and alignment with POS requirements
- **Duration:** 5m 17s

