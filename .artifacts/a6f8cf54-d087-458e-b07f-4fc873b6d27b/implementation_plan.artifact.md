# CallTape Application Refinement Plan

This plan outlines the steps to refine the CallTape application based on user feedback, including printer implementation, package reorganization, UI enhancements, and code quality improvements.

## User Review Required

> [!IMPORTANT]
> The package structure will be significantly altered. `MainActivity` and all theme files will move from `zw.co.donnclab.calltape` to `com.calltape`.

## Proposed Changes

### Hardware Component

#### [NEW] [MockPrinterImpl](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/hardware/MockPrinterImpl.kt)
Implement a mock printer for testing and development, allowing for virtual paper roll monitoring via `StateFlow`.

#### [MODIFY] [CallViewModel](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/ui/CallViewModel.kt)
Switch from `MorefunPrinterImpl` to `MockPrinterImpl`.

---

### Package Reorganization

#### [MODIFY] [MainActivity](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/zw/co/donnclab/calltape/MainActivity.kt) -> [MainActivity](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/MainActivity.kt)
Move `MainActivity` to `com.calltape` and update package declaration.

#### [MODIFY] Theme Files
Move `Color.kt`, `Theme.kt`, and `Type.kt` from `zw.co.donnclab.calltape.ui.theme` to `com.calltape.ui.theme`.

#### [MODIFY] [CallViewModel](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/ui/CallViewModel.kt) -> [CallViewModel](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/viewmodel/CallViewModel.kt)
Move `CallViewModel` to `com.calltape.viewmodel`.

---

### UI Enhancements

#### [MODIFY] [HistoryScreen](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/ui/HistoryScreen.kt)
- Redesign the call record cards with a "Modern POS" aesthetic.
- Replace the "Print Transcript" button with a printer icon.
- Improve typography and spacing.

#### [MODIFY] [DialerScreen](file:///C:/Users/DEVELOPER/Documents/Personal/Personal/Projects/apps/calltape/app/src/main/java/com/calltape/ui/DialerScreen.kt)
- Refine the dial pad to look more professional.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure everything compiles correctly after the move.
- Run unit tests if any (none specified yet, but will check).

### Manual Verification
- Verify the new UI in `@Preview` annotations.
- Check logs for "CallTape-MockPrinter" output when printing.
