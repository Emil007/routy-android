package com.routy.app.shell

import com.routy.app.logic.recording.RecordingPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SheetBackTest {

    private fun state(
        mode: ShellMode = ShellMode.EXPLORE,
        stage: SheetStage = SheetStage.Hidden,
    ) = ChromeState(mode = mode, stage = stage)

    @Test
    fun `web history wins over closing the sheet`() {
        val r = onHardwareBack(state(ShellMode.EXPLORE, SheetStage.Web("/admin")), true, false, false, RecordingPhase.IDLE, "View")
        assertEquals(BackResult.WebHistory, r)
    }

    @Test
    fun `closing web without history goes to profile`() {
        val r = onHardwareBack(state(ShellMode.EXPLORE, SheetStage.Web("/admin")), false, false, false, RecordingPhase.IDLE, "View")
        assertEquals(BackResult.Apply(SheetStage.Profile, ShellMode.EXPLORE), r)
    }

    @Test
    fun `stats and settings collapse to profile`() {
        val r = onHardwareBack(state(ShellMode.EXPLORE, SheetStage.Stats), false, false, false, RecordingPhase.IDLE, "View")
        assertTrue(r is BackResult.Apply && r.stage == SheetStage.Profile)
    }

    @Test
    fun `profile collapses to peek keeping the mode`() {
        val r = onHardwareBack(state(ShellMode.GO, SheetStage.Profile), false, false, false, RecordingPhase.IDLE, "View")
        assertTrue(r is BackResult.Apply && r.stage == SheetStage.Peek && r.mode == ShellMode.GO)
    }

    @Test
    fun `map edit mode clears to view in explore`() {
        val r = onHardwareBack(state(ShellMode.EXPLORE, SheetStage.Half), false, false, false, RecordingPhase.IDLE, "Draw")
        assertTrue(r is BackResult.ClearMapMode)
    }

    @Test
    fun `expanded and half shrink stepwise`() {
        val rExp = onHardwareBack(state(ShellMode.PLAN, SheetStage.Expanded), false, false, false, RecordingPhase.IDLE, "View")
        assertTrue(rExp is BackResult.Apply && rExp.stage == SheetStage.Half)
        val rHalf = onHardwareBack(state(ShellMode.PLAN, SheetStage.Half), false, false, false, RecordingPhase.IDLE, "View")
        assertTrue(rHalf is BackResult.Apply && rHalf.stage == SheetStage.Peek)
    }

    @Test
    fun `peek plan without live route collapses to explore`() {
        val r = onHardwareBack(state(ShellMode.PLAN, SheetStage.Peek), false, false, false, RecordingPhase.IDLE, "View")
        assertTrue(r is BackResult.Apply && r.stage == SheetStage.Hidden && r.mode == ShellMode.EXPLORE)
    }

    @Test
    fun `active route always confirms, state unchanged`() {
        val r = onHardwareBack(state(ShellMode.GO, SheetStage.Peek), false, true, false, RecordingPhase.IDLE, "View")
        assertEquals(BackResult.ConfirmLeaveRoute, r)
    }

    @Test
    fun `free roam confirms stop`() {
        val r = onHardwareBack(state(ShellMode.GO, SheetStage.Peek), false, false, true, RecordingPhase.IDLE, "View")
        assertEquals(BackResult.ConfirmStopFreeRoam, r)
    }

    @Test
    fun `recording above peek shrinks to peek, never stops gps`() {
        val r = onHardwareBack(state(ShellMode.RECORD, SheetStage.Half), false, false, false, RecordingPhase.RECORDING, "View")
        assertTrue(r is BackResult.Apply && r.stage == SheetStage.Peek && r.mode == ShellMode.RECORD)
    }

    @Test
    fun `recording at peek passes to the system`() {
        val r = onHardwareBack(state(ShellMode.RECORD, SheetStage.Peek), false, false, false, RecordingPhase.RECORDING, "View")
        assertEquals(BackResult.Pass, r)
    }

    @Test
    fun `confirm above peek shrinks, does not discard`() {
        val r = onHardwareBack(state(ShellMode.RECORD, SheetStage.Expanded), false, false, false, RecordingPhase.CONFIRM, "View")
        assertTrue(r is BackResult.Apply && r.stage == SheetStage.Peek)
    }

    @Test
    fun `confirm at peek passes to the system`() {
        val r = onHardwareBack(state(ShellMode.RECORD, SheetStage.Peek), false, false, false, RecordingPhase.CONFIRM, "View")
        assertEquals(BackResult.Pass, r)
    }

    @Test
    fun `record at peek stays with the system`() {
        val r = onHardwareBack(state(ShellMode.RECORD, SheetStage.Peek), false, false, false, RecordingPhase.IDLE, "View")
        assertEquals(BackResult.Pass, r)
    }
}
