# Package Refactoring and MockPrinterImpl Implementation Plan

Refactor the project to use the `zw.co.donnclab.calltape` package structure and implement the `MockPrinterImpl` for virtual printing.

## User Review Required

> [!IMPORTANT]
> The refactoring involves moving all source files and updating their package declarations. Some files in the new package structure were found to be empty (0 bytes), so I will be copying the correct content from the old `com.calltape` package.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/build.gradle.kts)
- Ensure `namespace` and `applicationId` are set to `zw.co.donnclab.calltape` (Already done, will double-check).

### Source Code Refactoring

#### [NEW] Move and update files to `zw.co.donnclab.calltape`
- Move `MainActivity.kt` and update package/imports.
- Move `data/*.kt` to `zw.co.donnclab.calltape.data` and update package/imports.
- Move `hardware/*.kt` to `zw.co.donnclab.calltape.hardware` and update package/imports.
- Move `service/*.kt` to `zw.co.donnclab.calltape.service` and update package/imports.
- Move `ui/*.kt` to `zw.co.donnclab.calltape.ui` and update package/imports.
- Move `viewmodel/*.kt` to `zw.co.donnclab.calltape.viewmodel` and update package/imports.

#### [DELETE] Old package directories
- Delete `app/src/main/java/com/calltape`
- Delete `app/src/main/java/zw/co/donnclab.calltape` (incorrect structure)

### Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/AndroidManifest.xml)
- Update `MainActivity` and `CallTranscriptionService` class names to use the new package.

### Hardware Implementation

#### [MODIFY] [MockPrinterImpl.kt](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/zw/co/donnclab/calltape/hardware/MockPrinterImpl.kt)
- Implement `MockPrinterImpl` with `virtualPaperRoll` StateFlow and logging.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify the project builds successfully.

### Manual Verification
- Verify the package structure in the file system.
- Verify `AndroidManifest.xml` references the correct classes.
