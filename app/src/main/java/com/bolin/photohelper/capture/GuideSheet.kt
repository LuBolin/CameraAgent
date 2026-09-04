package com.bolin.photohelper.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class GuideCard(
    val icon: ImageVector,
    val gesture: String,
    val detail: String,
)

private val GUIDE_CARDS = listOf(
    GuideCard(
        icon = Icons.Rounded.RadioButtonChecked,
        gesture = "Tap the ring",
        detail = "Takes the photo. When the ring turns green, tap to confirm.",
    ),
    GuideCard(
        icon = Icons.Rounded.Mic,
        gesture = "Tap the mic",
        detail = "Say what you want — \"too dark\", \"focus on the red mug\", \"take a photo in five seconds\". Tap again to send.",
    ),
    GuideCard(
        icon = Icons.Rounded.AutoAwesome,
        gesture = "Hold the ring",
        detail = "Photo Helper looks at the scene and improves it on its own.",
    ),
    GuideCard(
        icon = Icons.Rounded.Check,
        gesture = "Watch the colour",
        detail = "Orange means it is working. Green means it is ready — tap once more to confirm.",
    ),
)

/**
 * The whole how-to: four cards, one Orb gesture each. Everything the old guide said
 * about EV, white balance presets, and provider contracts now lives in the code that
 * enforces it, not in front of the user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "How it works",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Two controls: the ring and a mic.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GUIDE_CARDS.forEach { GuideCardRow(it) }
            Text(
                "Nothing is saved over your original photo, and Reset puts the camera back the way it was.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text("Close")
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GuideCardRow(card: GuideCard) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.gesture, style = MaterialTheme.typography.titleSmall)
                Text(card.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
