package com.bolin.photohelper.capture

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bolin.photohelper.ui.PhotoHelperTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompositionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cannotMoveOnlyAppearsForCurrentPhysicalMovement() {
        val guidance = ActiveGuidance("Aim right", com.bolin.photohelper.coach.VerificationTarget.Level(), 0,
            correction = com.bolin.photohelper.coach.Correction.LEFT)
        val state = mutableStateOf(CaptureUiState(onboardingStep = 2, cameraPermission = PermissionState.GRANTED,
            cameraPhase = CameraPhase.READY, compositionEnabled = true,
            coachingPhase = CoachingPhase.GUIDING, activeGuidance = guidance))
        var blocked = false
        val actions = object : CaptureScreenActions by TestActions() {
            override fun onCannotMoveFurther() { blocked = true }
        }
        compose.setContent { PhotoHelperTheme { TestCaptureScreen(state.value, actions = actions) } }
        compose.onNodeWithText("Can't move further").assertIsDisplayed().performClick()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, "composition-movement.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { assertEquals(true, blocked) }
        for (correction in listOf(com.bolin.photohelper.coach.Correction.HOLD,
            com.bolin.photohelper.coach.Correction.RECOVER, com.bolin.photohelper.coach.Correction.SMALLER)) {
            compose.runOnIdle { state.value = state.value.copy(activeGuidance = guidance.copy(correction = correction)) }
            compose.onNodeWithText("Can't move further").assertDoesNotExist()
        }
        compose.runOnIdle { state.value = state.value.copy(activeGuidance = guidance.copy(paused = true)) }
        compose.onNodeWithText("Can't move further").assertDoesNotExist()
    }

    @Test fun selectedPeopleCanBeChangedInThePreview() {
        val faces = listOf(FaceObservation(1, .15f, .2f, .35f, .45f), FaceObservation(2, .6f, .2f, .8f, .45f))
        val observation = FrameObservation(1, 0, .5f, 0f, 0f, faces = faces, sourceWidth = 400, sourceHeight = 800)
        val state = mutableStateOf(CaptureUiState(onboardingStep = 2, cameraPermission = PermissionState.GRANTED,
            cameraPhase = CameraPhase.READY, compositionSelection = faces))
        var confirmed = false
        val actions = object : CaptureScreenActions by TestActions() {
            override fun onToggleCompositionFace(index: Int) {
                state.value = state.value.copy(compositionSelectedIndices = state.value.compositionSelectedIndices + index)
            }
            override fun onConfirmCompositionSelection() { confirmed = true }
        }
        compose.setContent { PhotoHelperTheme {
            TestCaptureScreen(state.value, actions = actions, liveObservation = MutableStateFlow(observation))
        } }
        compose.onNodeWithText("Who are you framing?").assertIsDisplayed()
        compose.onNodeWithText("Use selection").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Person 1, not selected").performClick()
        compose.onNodeWithText("Use selection").assertIsEnabled()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, "composition-selection.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Use selection").performClick()
        compose.runOnIdle { assertEquals(true, confirmed) }
    }
}
