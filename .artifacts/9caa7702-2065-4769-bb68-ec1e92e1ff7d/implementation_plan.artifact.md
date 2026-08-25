# Package Refactoring Plan

The goal is to refactor the package structure from `com.calltape` to `zw.co.donnclab.calltape`.

## Proposed Changes

### [MODIFY] [build.gradle.kts](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/build.gradle.kts)
- Ensure `namespace` and `applicationId` are correct (already seems to be, but will double-check).

### [MODIFY] [AndroidManifest.xml](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/AndroidManifest.xml)
- Update `MainActivity` and `CallTranscriptionService` paths.

### [NEW] Package structure under `zw.co.donnclab.calltape`
- Move all source files from `com.calltape` to `zw.co.donnclab.calltape`.

### [DELETE] Old package structure `com.calltape`
- Remove empty directories after moving files.

## Detailed Refactoring Steps

1.  **Source Code Relocation**:
    - Move files from `app/src/main/java/com/calltape/` to `app/src/main/java/zw/co/donnclab/calltape/`.
    - Maintain sub-package structure: `data`, `hardware`, `service`, `ui`, `ui/theme`, `viewmodel`.

2.  **Package Declaration Update**:
    - Update `package com.calltape...` to `package zw.co.donnclab.calltape...` in all moved files.

3.  **Import Statement Update**:
    - Search for all occurrences of `import com.calltape` and replace with `import zw.co.donnclab.calltape`.
    - Also update any R file imports if they are hardcoded (though usually they follow the namespace).

4.  **Manifest Update**:
    - Update `<activity android:name="com.calltape.MainActivity" ...>` to `<activity android:name="zw.co.donnclab.calltape.MainActivity" ...>`.
    - Update `<service android:name="com.calltape.service.CallTranscriptionService" ...>` to `<service android:name="zw.co.donnclab.calltape.service.CallTranscriptionService" ...>`.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure it compiles.
- Run `./gradlew test` and `./gradlew connectedAndroidTest` to ensure tests still pass.

### Manual Verification
- Check that no files remain in `app/src/main/java/com/calltape`.
