package com.routy.app.shell

import com.routy.app.logic.recording.RecordingPhase

/**
 * Pure back-handling for the atlas shell. No Compose, no Android imports —
 * so [com.routy.app.shell.SheetBackTest] can run as a plain JVM unit test.
 *
 * Mirrors docs/UNIFIED-REDESIGN.md §7.3. `pass` means the BackHandler must NOT
 * consume the event: the system continues (activity to background, GPS/services
 * keep running). GPS is never stopped by back.
 */
enum class ShellMode { EXPLORE, PLAN, GO, RECORD }

sealed interface SheetStage {
    data object Hidden : SheetStage
    data object Peek : SheetStage
    data object Half : SheetStage
    data object Expanded : SheetStage
    data object Profile : SheetStage
    data object Stats : SheetStage
    data object Settings : SheetStage
    data class Web(val path: String) : SheetStage
    data object EditMenu : SheetStage
    data object Node : SheetStage
    data object Segment : SheetStage
}

data class ChromeState(
    val mode: ShellMode = ShellMode.EXPLORE,
    val stage: SheetStage = SheetStage.Hidden,
    val tool: String? = null,
)

sealed interface BackResult {
    data object WebHistory : BackResult
    data class Apply(val stage: SheetStage, val mode: ShellMode = ShellMode.EXPLORE, val clearTool: Boolean = false) : BackResult
    data class ClearMapMode(val mode: ShellMode = ShellMode.EXPLORE) : BackResult
    data object ConfirmLeaveRoute : BackResult
    data object ConfirmStopFreeRoam : BackResult
    data object Stay : BackResult
    data object Pass : BackResult
}

/** Android's MapMode lives in the map view-model; pass it as a plain string to keep this pure. */
fun onHardwareBack(
    state: ChromeState,
    webCanGoBack: Boolean,
    routeActive: Boolean,
    freeRoamActive: Boolean,
    recordingPhase: RecordingPhase,
    mapMode: String,
): BackResult {
    val stage = state.stage

    // 1. web sheet with browser history -> go back in the web view
    if (stage is SheetStage.Web && webCanGoBack) return BackResult.WebHistory
    // 2. web sheet -> profile
    if (stage is SheetStage.Web) return BackResult.Apply(SheetStage.Profile)
    // 3. stats/settings -> profile
    if (stage is SheetStage.Stats || stage is SheetStage.Settings) return BackResult.Apply(SheetStage.Profile)
    // 4. profile -> peek of the current mode
    if (stage is SheetStage.Profile) return BackResult.Apply(SheetStage.Peek, state.mode)
    // 5. map edit mode -> back to View, stay in Explore
    if (mapMode != "View") return BackResult.ClearMapMode()
    // 6. node/segment -> half
    if (stage is SheetStage.Node || stage is SheetStage.Segment) return BackResult.Apply(SheetStage.Half, state.mode)
    // 7. editMenu -> peek
    if (stage is SheetStage.EditMenu) return BackResult.Apply(SheetStage.Peek, state.mode)

    // 13. recording / paused / confirm above peek -> shrink straight to peek (never stop GPS, never discard)
    if (state.mode == ShellMode.RECORD) {
        val liveRecord =
            recordingPhase == RecordingPhase.RECORDING ||
                recordingPhase == RecordingPhase.PAUSED ||
                recordingPhase == RecordingPhase.CONFIRM
        if (liveRecord && (stage is SheetStage.Expanded || stage is SheetStage.Half)) {
            return BackResult.Apply(SheetStage.Peek, state.mode)
        }
        if (liveRecord && stage is SheetStage.Peek) return BackResult.Pass
    }

    // 5b. generic step-down (also non-live record IDLE)
    if (stage is SheetStage.Expanded) return BackResult.Apply(SheetStage.Half, state.mode)
    if (stage is SheetStage.Half) return BackResult.Apply(SheetStage.Peek, state.mode)
    // 9. peek in PLAN without live route -> explore
    if (stage is SheetStage.Peek && state.mode == ShellMode.PLAN && !routeActive && !freeRoamActive) {
        return BackResult.Apply(SheetStage.Hidden, ShellMode.EXPLORE)
    }
    // 10. peek in GO, nothing live -> explore
    if (stage is SheetStage.Peek && state.mode == ShellMode.GO && !routeActive && !freeRoamActive) {
        return BackResult.Apply(SheetStage.Hidden, ShellMode.EXPLORE)
    }
    // 11. active route anywhere -> confirm leave
    if (routeActive) return BackResult.ConfirmLeaveRoute
    // 12. active free-roam anywhere -> confirm stop
    if (freeRoamActive) return BackResult.ConfirmStopFreeRoam

    // 14. peek (incl. running recording and CONFIRM) -> pass. Never consume.
    if (stage is SheetStage.Peek) return BackResult.Pass
    return BackResult.Stay
}
