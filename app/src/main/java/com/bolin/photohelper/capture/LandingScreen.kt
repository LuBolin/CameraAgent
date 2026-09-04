package com.bolin.photohelper.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.ui.Charcoal
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.Mango
import com.bolin.photohelper.ui.SoftCream

@Composable
fun LandingScreen(
    preview: @Composable BoxScope.() -> Unit,
    onStart: () -> Unit,
    onGuideOpen: () -> Unit,
) {
    val overlays = LocalOverlayColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CaptureTestTags.LANDING),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize().blur(25.dp), content = preview)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Charcoal.copy(alpha = 0.88f),
                            Charcoal.copy(alpha = 0.75f),
                            Charcoal.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .safeDrawingPadding(),
        ) {
            Text(
                "Photo Helper",
                style = MaterialTheme.typography.displayMedium,
                color = overlays.onOverlay,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "I help you take better photos.",
                style = MaterialTheme.typography.bodyLarge,
                color = overlays.onOverlay,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(32.dp))
            Surface(
                onClick = onStart,
                color = overlays.mirrorBar,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(2.dp, Mango, RoundedCornerShape(50))
                    .semantics { contentDescription = "Tap to start using Photo Helper" },
            ) {
                Text(
                    "Tap to Start",
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.displayLarge,
                    color = overlays.onOverlay,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(20.dp))
            OutlinedButton(
                onClick = onGuideOpen,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, SoftCream.copy(alpha = 0.5f)),
            ) {
                Text(
                    "How it works",
                    color = overlays.onOverlay,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
