package com.bolin.photohelper.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bolin.photohelper.ui.ThemeMode
import com.bolin.photohelper.visual.VisualProvider
import com.bolin.photohelper.visual.MAX_API_KEY_CHARACTERS

/**
 * Settings, grouped so the first thing a user meets is a choice they understand.
 * Anything that only a developer would recognise lives behind Advanced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: CaptureUiState,
    apiKeyInput: String,
    onDismiss: () -> Unit,
    onSpokenGuidanceChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onTechnicalDetailChanged: (Boolean) -> Unit,
    onVisualAiEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onStyleProfileChanged: (String) -> Unit,
    onVisualProviderChanged: (VisualProvider) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestKey: () -> Unit,
    onClearKey: () -> Unit,
    onEnableMicrophone: () -> Unit,
    onOpenVisualAiPolicy: () -> Unit,
    onOpenMlKitPolicy: () -> Unit,
    onAutoCaptureEnabledChanged: (Boolean) -> Unit,
) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CaptureTestTags.SETTINGS)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )

            SettingsGroup("Sound & Vibration")
            ToggleRow("Read instructions aloud", state.settings.spokenGuidance, onSpokenGuidanceChanged)
            ToggleRow("Vibration feedback", state.settings.haptics, onHapticsChanged)
            if (state.microphonePermission == PermissionState.DENIED) {
                Text("Microphone access is off.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onEnableMicrophone, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Enable microphone")
                }
            }

            SettingsGroup("Smart Features")
            ToggleRow("Auto-capture when steady", state.settings.autoCaptureEnabled, onAutoCaptureEnabledChanged)

            SettingsGroup("Theme")
            ThemeModeChooser(state.settings.themeMode, onThemeModeChanged)

            Spacer(Modifier.size(16.dp))
            HorizontalDivider()
            Spacer(Modifier.size(4.dp))
            TextButton(
                onClick = { advancedExpanded = !advancedExpanded },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics { stateDescription = if (advancedExpanded) "Expanded" else "Collapsed" },
            ) {
                Text("Advanced options")
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (advancedExpanded) {
                StyleProfileField(state.settings.styleProfile, onStyleProfileChanged)
                Spacer(Modifier.size(8.dp))
                ToggleRow("Show camera measurements", state.settings.technicalDetail, onTechnicalDetailChanged)
                ToggleRow(
                    label = "AI interpretation",
                    checked = state.settings.visualAiEnabled,
                    onCheckedChange = onVisualAiEnabledChanged,
                    enabled = state.settings.keyConfigured && !state.settings.testingKey,
                )
                SettingsGroup("Model")
                VisualProviderChooser(state.settings.visualProvider, onVisualProviderChanged)
                QwenKeySetup(
                    settings = state.settings,
                    apiKeyInput = apiKeyInput,
                    onApiKeyChanged = onApiKeyChanged,
                    onTestKey = onTestKey,
                    onClearKey = onClearKey,
                    onOpenVisualAiPolicy = onOpenVisualAiPolicy,
                )
                TextButton(onClick = onOpenMlKitPolicy, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("ML Kit data disclosure")
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 56.dp)) {
                Text("Close")
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Spacer(Modifier.size(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * Which model interprets the scene. Both arms send the same prompts and images, so
 * this is a like-for-like comparison rather than two different apps.
 */
@Composable
private fun VisualProviderChooser(selected: VisualProvider, onSelect: (VisualProvider) -> Unit) {
    val options = listOf(
        VisualProvider.QWEN to "Qwen (Alibaba Cloud)",
        VisualProvider.CLAUDE to "Claude (Anthropic)",
    )
    Column {
        options.forEach { (provider, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = provider == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(provider) },
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = provider == selected, onClick = null)
                Spacer(Modifier.size(12.dp))
                Text(label)
            }
        }
    }
    Text(
        "Each provider needs its own API key. Paste the key for whichever is selected.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ThemeModeChooser(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to "Match my phone",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark",
    )
    Column {
        options.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = mode == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(mode) },
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == selected, onClick = null)
                Spacer(Modifier.size(12.dp))
                Text(label)
            }
        }
    }
}

/**
 * Free text describing the look the user is after. Optional, and never a technical
 * parameter - "moody and cinematic" is the kind of answer this wants.
 */
@Composable
private fun StyleProfileField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_STYLE_PROFILE_CHARACTERS)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Describe your photo style (optional)") },
        placeholder = { Text("bright and airy · moody and cinematic · vintage film") },
        supportingText = { Text("Photo Helper leans this way when it improves a shot.") },
        minLines = 2,
        maxLines = 4,
    )
}

@Composable
private fun QwenKeySetup(
    settings: SettingsUiState,
    apiKeyInput: String,
    onApiKeyChanged: (String) -> Unit,
    onTestKey: () -> Unit,
    onClearKey: () -> Unit,
    onOpenVisualAiPolicy: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = { onApiKeyChanged(it.take(MAX_API_KEY_CHARACTERS)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Alibaba Cloud Model Studio (Bailian) API key") },
        placeholder = {
            Text(if (settings.keyConfigured) "Saved key is hidden" else "Enter your API key")
        },
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
        ),
        singleLine = true,
        enabled = !settings.testingKey,
        trailingIcon = {
            TextButton(
                onClick = { keyVisible = !keyVisible },
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (keyVisible) "Hide API key" else "Show API key"
                },
            ) {
                Icon(
                    imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                )
            }
        },
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = {
                clipboard.getText()?.text?.let { onApiKeyChanged(it.take(MAX_API_KEY_CHARACTERS)) }
            },
            enabled = !settings.testingKey,
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("Paste") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            settings.keyStatus,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.testingKey) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onTestKey,
            enabled = !settings.testingKey && apiKeyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Test, save & enable") }
        OutlinedButton(
            onClick = onClearKey,
            enabled = !settings.testingKey && (apiKeyInput.isNotBlank() || settings.keyConfigured),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Clear key") }
    }
    Text(
        "When AI interpretation is on, each spoken request and one reduced frame go to Alibaba Cloud " +
            "Model Studio in China (Beijing). Audio is never sent and the preview is never streamed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onOpenVisualAiPolicy, modifier = Modifier.heightIn(min = 56.dp)) {
        Text("Alibaba Cloud privacy notice")
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
