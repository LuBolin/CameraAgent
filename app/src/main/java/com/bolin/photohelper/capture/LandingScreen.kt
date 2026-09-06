package com.bolin.photohelper.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolin.photohelper.R
import com.bolin.photohelper.ui.Charcoal
import com.bolin.photohelper.ui.LocalOverlayColors
import com.bolin.photohelper.ui.Mango
import com.bolin.photohelper.ui.SoftCream

private const val USE_FIXED_BACKGROUND = true

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
        // Background: fixed image or live camera preview
        if (USE_FIXED_BACKGROUND) {
            Image(
                painter = painterResource(R.drawable.landing_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(12.dp),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().blur(25.dp), content = preview)
        }

        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Charcoal.copy(alpha = 0.55f),
                            Charcoal.copy(alpha = 0.35f),
                            Charcoal.copy(alpha = 0.65f),
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
            // Logo text with sparkle accents
            Text(
                text = buildAnnotatedString {
                    append("Photo\nHelper")
                    withStyle(SpanStyle(color = Mango, fontSize = 20.sp)) {
                        append("✧")
                    }
                },
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    lineHeight = 56.sp,
                ),
                color = SoftCream,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(16.dp))
            Text(
                "The photo your moment deserved.",
                style = MaterialTheme.typography.bodyLarge,
                color = SoftCream.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))

            // Orb ring — static Mango glow
            val orbSize = 100.dp
            Surface(
                onClick = onStart,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(orbSize)
                    .drawBehind {
                        val center = this.center
                        val outerRadius = size.minDimension / 2f
                        // Soft outer glow ring
                        drawCircle(
                            color = Mango.copy(alpha = 0.15f),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = 14.dp.toPx()),
                        )
                        // Main ring
                        drawCircle(
                            color = Mango,
                            radius = outerRadius - 7.dp.toPx(),
                            center = center,
                            style = Stroke(width = 4.dp.toPx()),
                        )
                        // Shutter dot
                        drawCircle(
                            color = SoftCream,
                            radius = outerRadius * 0.38f,
                            center = center,
                        )
                    }
                    .semantics { contentDescription = "Tap to start using Photo Helper" },
            ) {}

            Spacer(Modifier.size(12.dp))
            Text(
                "Tap to start",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftCream.copy(alpha = 0.7f),
            )
            Spacer(Modifier.size(28.dp))
            OutlinedButton(
                onClick = onGuideOpen,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, SoftCream.copy(alpha = 0.4f)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = SoftCream.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Photo guide",
                        color = SoftCream.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
