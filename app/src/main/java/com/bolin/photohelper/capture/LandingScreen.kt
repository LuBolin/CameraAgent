package com.bolin.photohelper.capture

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.ui.Charcoal
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.LocalReducedMotion
import com.bolin.photohelper.ui.Mango

/**
 * What the app opens on: the live camera behind a heavy Gaussian blur, one pulsing
 * call to action, and nothing else. Tapping anywhere runs the system permission
 * prompts and drops the user straight into the camera - there are no onboarding steps.
 */
@Composable
fun LandingScreen(
    preview: @Composable BoxScope.() -> Unit,
    onStart: () -> Unit,
    onGuideOpen: () -> Unit,
) {
    val overlays = LocalOverlayColors.current
    val reducedMotion = LocalReducedMotion.current
    val pulse by rememberInfiniteTransition(label = "landing_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (reducedMotion) 1f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "landing_pulse_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CaptureTestTags.LANDING)
            .clickable(
                role = Role.Button,
                onClickLabel = "Start Photo Helper",
                onClick = onStart,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The feed is only bound once the camera permission exists, so before the
        // first tap this is simply a dark ground - the gradient below makes that
        // read as an intentional surface either way.
        Box(modifier = Modifier.fillMaxSize().blur(25.dp), content = preview)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Charcoal.copy(alpha = 0.82f),
                            Charcoal.copy(alpha = 0.62f),
                            Charcoal.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                "Photo Helper",
                style = MaterialTheme.typography.headlineMedium,
                color = overlays.onOverlay,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "The photo your moment deserved.",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = overlays.onOverlayDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(20.dp))
            Surface(
                color = overlays.mirrorBar,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .scale(pulse)
                    .border(2.dp, Mango, RoundedCornerShape(50))
                    .semantics { contentDescription = "Tap to start" },
            ) {
                Text(
                    "Tap to Start",
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.displayLarge,
                    color = overlays.onOverlay,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(16.dp))
            Text(
                "Say what you want. The camera does the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = overlays.onOverlayDim,
                textAlign = TextAlign.Center,
            )
        }

        TextButton(
            onClick = onGuideOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 16.dp)
                .heightIn(min = 48.dp),
        ) {
            Text("How it works", color = overlays.onOverlayDim, style = MaterialTheme.typography.labelLarge)
        }
    }
}
