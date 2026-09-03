# Photo Helper Redesign Plan

## Discussion Categories

| # | Category | Status | Key Decision Needed |
|---|---|---|---|
| 1 | **Theme & Color System** | **Decided** | Modern Balance: Mango accent, Charcoal/Soft Cream interchange, Jarvis gradient Orb |
| 2 | **Typography** | **Decided** | Quicksand single-family, 7-token type scale, 14sp minimum |
| 3 | **Iconography** | **Decided** | Material Icons Extended, Rounded style |
| 4 | **The Helper Orb** | **Decided** | 72dp Orb, Jarvis glow, control strip landscape, scene-aware overlays |
| 5 | **Landing & Onboarding** | **Decided** | Near-zero onboarding: blur landing → permissions → done. Style Profile optional in settings |
| 6 | **Camera Chrome & Overlays** | **Decided** | 4 buttons only, frosted glass backing, portrait top+bottom / landscape control strip |
| 7 | **Coaching & Decision Cards** | **Decided** | 9 internal phases → 3 visible states, single card treatment |
| 8 | **Settings & Guide** | **Decided** | Minimal settings, visual guide (3-4 cards), no API/LLM info |
| 9 | **Motion & Animation** | **Decided** | Full animation inventory, reduced-motion fallbacks, 3-pulse budget |
| 10 | **Accessibility Fixes** | **Decided** | Contrast rules, focus rings, touch spacing, color+icon states |
| 11 | **Architecture Refactor** | **Decided** | 9-file split, CaptureScreenActions interface, VisibleCoachingState |
| 12 | **Smart Features (ARCore)** | **Decided (Phase 3)** | ARCore stillness + AI framing = auto-capture, audio cues |

---

## Audit Summary (5.2/10 overall)

| Category | Score | Notes |
|---|---|---|
| Architecture | 8.0 | Clean MVI/UDF, proper state hoisting, good hardware abstraction |
| Accessibility | 7.5 | Extensive semantics, live regions, traversal ordering, 48dp targets, walking-guidance guard |
| Interaction | 5.5 | Functional but not delightful |
| Color system | 4.0 | ~12 hardcoded hex values bypass theme, 4 different black alphas, no token system |
| Typography | 4.0 | Raw Material 3 defaults, no display face, no personality, ad-hoc fontWeight usage |
| Information density | 3.5 | Guide sheet ~120 lines of text, settings dumps everything, onboarding has developer jargon |
| Simplicity | 3.5 | 2076-line single file, 33 callbacks, 9 coaching phases visible to user |
| Visual identity | 3.0 | Text glyphs for icons, no brand signature, no memorable visual element |

## Product Vision

### Target Audience
- **Elderly** — simplicity, large touch targets, zero cognitive overload
- **Boyfriends / reluctant photographers** — foolproof, direct instructions to get flattering angles

### Core Philosophy: "Zero Interface"
The camera feed is the primary canvas. Chrome and settings get out of the way. Every element must justify its screen presence. Progressive disclosure everywhere.

### UX Decisions

**Landing / "Mirror" Effect**
- Live camera feed behind dynamic Gaussian blur
- Large, pulsing "Tap to Start" button
- No text walls, no onboarding steps visible

**Streamlined Onboarding**
- Removed all developer jargon (API keys, LLM contracts, Qwen response contracts)
- Replaced with simplified 3-step guide
- Style Profile: single under-200-word preference input (e.g. "Bright, flattering full-body shots") passed as system prompt to the VLM

**The Helper Orb**
- Minimalist glowing ring at bottom of screen
- Replaces the current 3-button capture bar (shutter/mic/enhance)
- Dynamic AI state communication:
  - White ring = Idle
  - Blue pulse = Listening / voice interaction
  - Amber spin = Processing frame
  - Green glow = Composition aligned / ready
- Single tap = capture, long press = voice, double tap = enhance (TBD)

**Zero-Shake Capture**
- No haptic vibration (destabilizes device during capture)
- Audio cue + silent auto-capture replaces manual shutter tapping
- Triggers when AI confirms good framing AND ARCore confirms zero phone movement

### Technical Architecture: "Brain + Reflexes"

**The Reflexes (ARCore / Device Sensors) — Local, 60 FPS, $0**
- Device tilt tracking
- Height relative to floor
- Stillness detection (zero phone movement)
- Runs continuously, no API calls

**The Brain (Vision AI) — Cloud, per-call**
- Evaluates overall composition, subject framing, lighting
- Provides personalized stylistic feedback via Style Profile
- Called sparingly, only when needed

**Smart Auto-Capture trigger**
- (AI confirms good framing) + (ARCore confirms zero movement) = auto-capture
- User hears audio confirmation, photo is taken without touching the phone

### Cost Structure
| Component | Cost | Notes |
|---|---|---|
| ARCore, CameraX, Device Sensors | $0 | No runtime fees, no API limits, zero latency |
| On-Device ML (MediaPipe / ML Kit) | $0 | Bundled, runs locally |
| Cloud VLMs (Qwen, Gemini, OpenAI) | Pay-per-token | Reserved for rich stylistic feedback only |

## Foundations (Systems Thinking)

These are the root-cause decisions that every design category builds on.

### Tagline
**"The photo your moment deserved."**
Poetic, outcome-focused. Not "helps you save better memories" (too functional). The app delivers photos that match the weight of the moment.

### Who It's For
- **Primary:** Everyday people who want better photos but don't know photography — elderly, boyfriends/partners asked to take the photo, tourists, group-photo-takers
- **JTBD:** "I'm in a moment worth capturing and I want the photo to actually look good without knowing how cameras work"
- **Customization for primary audience:** Adjustable button size, text size, light/dark mode. Auto-capture on/off. Voice on/off (instructions appear on-screen when voice is off)

### Brand Personality
**Companion** — friendly, encouraging, efficient. Always ready to help. Not robotic, not chatty. The tone should feel like a photographer friend who gives you quick, confident directions.

### Interaction Philosophy

**Activation:** Tap the Helper Orb to start an AI interaction. Nothing runs until tapped — prevents wasted API tokens and respects user control.

**Instruction length:** 5 words max per instruction (e.g. "move left", "zoom in", "tilt up slightly"). Short enough to read mid-pose.

**Confirmation:** Either voice ("ok", "yes") or button tap — user's choice. Same for undo.

**Signal to start:** A button the photo subject can press (or the photographer taps the Orb) to tell the AI "I'm in position, start coaching."

### AI Intrusiveness Levels

| Domain | Level | Behavior | Example |
|---|---|---|---|
| **Camera settings** (brightness, zoom, white balance, focus) | **Level 4 — Auto-adjust** | AI detects the issue and applies the fix immediately. User sees a brief green flash confirmation. Can undo via voice or button. | Face is dark → AI bumps exposure +1 EV, green flash, done |
| **Positioning** (subject placement, body angle, composition) | **Level 3 — Guide only** | AI shows overlay guidance (grid lines, face circles, arrows, text). Never moves the subject automatically. User follows guidance at their own pace. | "Step left" + arrow overlay showing where to stand |

**Why this split:** Camera settings are invisible to the subject and instantly reversible — auto-adjusting them feels helpful, not intrusive. Positioning involves a real person moving their body — the AI should guide, not command. Level 4 everywhere would feel controlling; Level 3 everywhere would feel slow for simple fixes.

### Three AI Response Types

1. **Auto-adjustments** (Level 4) — Camera setting changes applied silently with a green flash confirmation. No card, no overlay text. Undo available.
2. **Positioning guidance** (Level 3) — Visual overlays on the camera feed: grid lines, face target circles, directional arrows. Paired with 5-word text instruction. No auto-action.
3. **Planning responses** — When the user taps the Orb for a voice conversation (e.g. "make this look cinematic"), the AI plans multiple steps. Shows as Orb text + composition overlay. User confirms or adjusts before execution starts.

### Coaching & Decision Cards
- Coaching stays — it's core to the product
- Decision cards must be a **single visual treatment** — understood without doubt, no second-guessing
- Current 9 coaching phases are too many for the user to track — simplify the visible phases
- Composition guidance uses **visual overlays** (rule-of-thirds grid, face circles, position arrows) instead of text-heavy explanation cards

---

## Architecture Refactor

### Current Problems
- `CaptureScreen.kt` is 2076 lines, single file
- `CaptureScreen` composable takes 33 callback parameters
- Text glyphs for all icons (`↻`, `⚙︎`, `?`, `⚡︎×`, `☼`, `✨`, `■`, `Mic`, `↶`)
- `PhotoHelperTheme.kt` defines dark scheme but composables bypass it for half the palette
- `Typography()` has zero customization
- No light mode

### Planned Changes
- Group 33 callbacks into `CaptureScreenActions` interface
- Extract composables into separate files:
  - `CaptureBar.kt` (→ becomes Helper Orb)
  - `SettingsSheet.kt`
  - `CoachingCards.kt`
  - `LiveIndicator.kt`
  - `PreviewOverlay.kt`
  - `ReviewScreen.kt`
  - `OnboardingFlow.kt`
  - `GuideSheet.kt`
- Replace text glyphs with `material-icons-extended` vector icons
- Centralize all hardcoded colors into theme tokens:
  - Amber accent: `Color(0xFFFFD54F)` (used 4 times)
  - Blue accent: `Color(0xFF8DCDFF)`
  - Success green: `Color(0xFF173D2A)` / `Color(0xFFC4F2D5)`
  - Warning text: `Color(0xFFFFDDB0)`
  - Live red: `Color(0xFFFF4D4D)`
  - Overlay system: 4 named alpha levels instead of magic numbers
- Define custom Typography with display face and intentional type scale
- Add light mode support

## Implementation Phases

### Phase 1: Foundation (unblocks everything) ✅ COMPLETE
- [x] Extract files from `CaptureScreen.kt` (2076 → ~280 lines + 7 new files)
- [x] Create `CaptureScreenActions` interface (replaces 33 callback params)
- [x] Centralize theme tokens (colors, overlays, typography) in `PhotoHelperTheme.kt`
- [x] Add `material-icons-extended` + `ui-text-google-fonts` dependencies
- [x] Replace text glyphs with Material Icons Rounded (✨→AutoAwesome, ■/Mic→Stop/Mic, 👁→Visibility, ↶→Undo, ✓→Check, ⓘ→Info, flash/settings/flip→Material icons)
- [x] Add `VisibleCoachingState` enum (IDLE, WORKING, ACTION) mapping 9 internal phases → 3 visible states
- [x] Add Quicksand font via Compose Downloadable Fonts (runtime download, no bundled TTFs)
- [x] Add dark + light color schemes (Charcoal/Cream interchange)
- [x] Fix `collectAsState()` → `collectAsStateWithLifecycle()` in `MainActivity.kt`

### Phase 2: Visual Redesign ✅ COMPLETE

Phase 1 delivered the foundation: theme tokens, file extraction, CaptureScreenActions interface,
Material Icons, Quicksand font, dark+light color schemes, VisibleCoachingState enum. Phase 2
builds the actual redesigned UI on top of that foundation.

**Pre-requisites (all done in Phase 1):**
- `PhotoHelperTheme.kt` — brand palette (Mango, Charcoal, SoftCream, Coral, Sage), JarvisGradient
  brush, OverlayColors (scrim/frostedGlass), Quicksand typography, dark+light schemes
- `CaptureScreenActions` interface in `CaptureScreenActions.kt`
- `VisibleCoachingState` enum in `CaptureUiState.kt` (IDLE, WORKING, ACTION)
- Files extracted: CameraChrome.kt, CaptureBarComponents.kt, CoachingCard.kt,
  CompositionOverlays.kt, CaptureReviewScreen.kt, SettingsSheet.kt, GuideSheet.kt

**Key design references** — all detailed in the Category Decisions section below in this file:
- Section 4 (The Helper Orb) — sizes, states, gradient, glow, interactions, mirror bar
- Section 5 (Landing & Onboarding) — blur landing, permission flow, first-use hint
- Section 6 (Camera Chrome & Overlays) — 4 buttons only, frosted glass, layout
- Section 7 (Coaching & Decision Cards) — 3 visible states, single card treatment
- Section 8 (Settings & Guide) — grouped settings, visual guide cards
- Section 9 (Motion & Animation) — animation inventory with durations and reduced-motion fallbacks
- Section 10 (Accessibility) — contrast, focus rings, touch spacing

#### 2a. Helper Orb — replaces CaptureBar
**Files:** Create `HelperOrb.kt`, create `MirrorBar.kt`, delete/gut `CaptureBarComponents.kt`
**What it does:** Single 72dp ring (56dp landscape) replaces shutter/mic/enhance buttons.
Ring color = Jarvis gradient (Coral→Mango→Sage) driven by ViewModel confidence float (0f–1f).
Outer glow aura matches ring color, blurred 14dp. 3-pulse budget on state arrival, then static.

States and interactions:
| State | Ring Color | Icon Inside | Glow |
|---|---|---|---|
| Idle | Cream (dark) / Charcoal (light) | ● shutter dot | None |
| Listening | Coral | Icons.Rounded.Mic | Coral glow |
| Processing | Mango | CircularProgressIndicator | Mango glow |
| Decided | Sage | Icons.Rounded.Check | Pulsing sage (3 pulses) |

Gestures: single tap = capture (idle) or confirm (decided), long press = voice input,
double tap = auto-enhance (TBD).

The Orb reads `VisibleCoachingState` from `CaptureUiState` and maps:
- IDLE → Idle state
- WORKING → Listening/Processing (use `CoachingPhase` to pick coral vs mango)
- ACTION → Decided state

**ViewModel change needed:** Expose a `confidence: StateFlow<Float>` (0f–1f) for the gradient
interpolation. For now, derive it from CoachingPhase (IDLE=0, LISTENING=0.2, INTERPRETING=0.4,
REQUESTING=0.5, RECOMMENDATION=0.8, APPLYING=0.9, GUIDING=1.0, VERIFYING=0.6, ERROR=0.1).

**Mirror Bar** (MirrorBar.kt): Frosted glass pill (charcoal @ 55%, 14dp blur, 1dp cream @ 10%
border). Floats 10dp above Orb (portrait) or inside viewfinder (landscape). Shows ≤5 word
instruction text. Slide 8dp + fade in (250ms). Only visible during WORKING and ACTION states.

- [x] Create `HelperOrb.kt` with ring, gradient, glow, tap/long-press gestures
- [x] Create `MirrorBar.kt` with frosted pill, slide animation, instruction text
- [x] Add `confidence: StateFlow<Float>` to `CaptureViewModel`
- [x] Wire Orb into `CaptureScreen.kt` replacing `CaptureBar` call sites
- [x] Portrait layout: Orb bottom-center, MirrorBar above it
- [x] Landscape layout: Orb in 72dp vertical control strip (right side) with flash/flip/settings
- [x] Reduced motion: static color snap, instant mirror bar, no pulse

#### 2b. Gaussian blur landing screen
**Files:** Create `LandingScreen.kt`, modify `CaptureScreen.kt` (replace `Onboarding` composable)
**What it does:** Live camera feed behind dynamic Gaussian blur. Large pulsing "Tap to Start"
(Quicksand displayLarge, mango accent, 2.5s pulse loop). No text walls.

Flow: Open app → blur landing → tap → system permission dialogs → camera with first-use Orb hint.

Implementation: Use `Modifier.blur(radius = 25.dp)` on the camera preview composable, overlay
the CTA button. On tap, call `actions.onOnboardingContinue()` which triggers permission request.

- [x] Create `LandingScreen.kt` with blurred preview + pulsing CTA
- [x] Replace current `Onboarding` composable (2 text-heavy steps) with `LandingScreen`
- [x] Add first-use Orb hint (mango pulse + "tap me" in mirror bar, shown once via SharedPreferences)
- [x] Reduced motion: static mango ring, no CTA pulse

#### 2c. Simplified onboarding
**Files:** `LandingScreen.kt` (part of 2b), `UserPreferences.kt` (first-use flag)
**What it does:** Near-zero onboarding. Blur landing → permissions → done. No jargon, no API key
setup in onboarding. Remove current step 1 ("Photo Helper is designed to use an image-capable
LLM...") and step 2 developer text.

- [x] Remove all developer jargon from user-facing onboarding text
- [x] Add `hasSeenFirstUse` flag to UserPreferences (SharedPreferences)
- [x] First-use Orb hint appears once, then never again

#### 2d. Style Profile input
**Files:** `SettingsSheet.kt` (add section), `UserPreferences.kt` (persist), `CaptureViewModel.kt`
**What it does:** Optional text field in Settings where user describes their aesthetic preference
(e.g. "bright, flattering full-body shots" or "moody, cinematic"). Under 200 words. Passed as
system prompt context to Qwen when set.

- [x] Add Style Profile text field to Settings (under new "Style" group)
- [x] Persist in UserPreferences
- [x] Pass as system prompt prefix in `BailianVisualClient` when non-empty
- [x] Show hint text: "Describe your photo style (optional)"

#### 2e. Glassmorphic overlays on camera feed
**Files:** `CameraChrome.kt`, `PhotoHelperTheme.kt` (OverlayColors already defined)
**What it does:** Replace current solid-color overlay buttons with frosted glass treatment.
All 4 persistent buttons (flash, flip, Orb, settings) get: charcoal @ 45% + blur backdrop +
1dp cream @ 8% border. Use `LocalOverlayColors.current.frostedGlass` (already defined in theme).

Phase 1 already set `frostedGlass = Color.White.copy(alpha = 0.15f)` in OverlayColors. This
task refines the visual treatment and ensures consistent application.

- [x] Apply frosted glass treatment to all overlay buttons consistently
- [x] Reduce persistent buttons to 4 only: flash, flip, Orb, settings
- [x] Remove live indicator text (AI state shown via Orb color)
- [x] Remove transcript overlay (MirrorBar replaces it)
- [x] Remove coaching phase labels (Orb gradient replaces them)

#### 2f. New coaching card design
**Files:** `CoachingCard.kt` (rewrite)
**What it does:** Single frosted glass card treatment for all decision types. Same visual as
MirrorBar (charcoal @ 55%, blur). One headline + one action row (confirm/dismiss). No separate
card types competing for attention. Voice confirmation ("yes"/"no") also accepted.

The `VisibleCoachingState` enum (Phase 1) drives visibility:
- IDLE → no card
- WORKING → no card (MirrorBar shows status)
- ACTION → card appears only if explicit user confirmation needed

- [x] Redesign `DecisionCard` with frosted glass treatment
- [x] Unify all card types (Recommend, Clarify, Advisory) into single visual
- [x] Cards only appear for ACTION state when confirmation needed
- [ ] Voice confirmation support (already in coach engine, wire to card dismiss) — **not done**: the coach engine has no yes/no confirmation path to wire to; needs engine work first

**Instrumented tests:** rewritten for the redesign (66 pass). Restored three behaviours Phase 1's file split had dropped: tap-to-focus on the preview, the tappable focus marker, and selfie mirroring of the model's focus cell. Open question: the Orb's double-tap gesture delays every shutter press by ~300ms — see the notes below.

#### 2g. Light mode
**Files:** `PhotoHelperTheme.kt` (already has light scheme), test across all screens
**What it does:** Phase 1 defined the light color scheme (Cream bg, Charcoal text) but most
composables still hardcode `Color.White`, `Color.Black`, etc. This task replaces all remaining
hardcoded colors with theme tokens so light mode actually works.

- [x] Audit all composables for hardcoded `Color.White`/`Color.Black` usage
- [x] Replace with `MaterialTheme.colorScheme.*` or `LocalOverlayColors` tokens
- [x] Camera viewfinder stays dark regardless of mode
- [x] Overlay buttons use theme-aware frosted glass
- [x] Test light mode on emulator/device

#### 2h. Progressive disclosure in settings
**Files:** `SettingsSheet.kt` (restructure)
**What it does:** Group settings into sections per the design:
| Group | Items |
|---|---|
| Camera | Auto-capture on/off |
| Interaction | Voice on/off, Button size, Text size |
| Appearance | Light/dark mode |
| Style | Style Profile |
| Help | How-to guide, About, Feedback |

Remove: API key setup from prominent position (move to "Advanced" or hide),
LLM contract text, capability dump, 120-line guide sheet.

Replace guide sheet with 3-4 visual cards showing Orb interactions.

- [x] Restructure settings into grouped sections with headers
- [x] Move API key to "Advanced" section (collapsed by default)
- [x] Remove developer-facing text (LLM contract, model info paragraphs)
- [x] Replace `GuideSheet` with visual how-to cards (3-4 cards, one Orb interaction each)

### Phase 3: Smart Features

Phase 2 delivers the visual redesign: Helper Orb, blur landing, glassmorphic overlays, light mode.
Phase 3 adds the sensor-driven intelligence layer on top: ARCore spatial tracking, smart
auto-capture, and audio feedback. This is the "reflexes" half of the "Brain + Reflexes" architecture.
It opens with 3a, a control-scheme revision that makes the AI reachable from a visible button
instead of two undiscoverable gestures — independent of ARCore, and worth shipping first.

**Pre-requisites (from Phase 2):**
- Helper Orb with Jarvis gradient and `confidence: StateFlow<Float>` in ViewModel
- MirrorBar for instruction display
- VisibleCoachingState driving Orb states (IDLE/WORKING/ACTION)
- Style Profile persisted in UserPreferences and wired to BailianVisualClient

**Key design references:**
- Section 12 (Smart Features / ARCore) in Category Decisions below
- Section 4 (Helper Orb) — Orb "decided" state triggers auto-capture
- Section 9 (Motion & Animation) — audio cue timing

#### 3a. Control scheme revision — visible mic, no double tap

**Files:** `HelperOrb.kt`, `CaptureScreen.kt`, `CameraChrome.kt`, `GuideSheet.kt`,
`CaptureScreenTest.kt`

**Why:** Phase 2 shipped voice on a long press and auto-enhance on a double tap. Both are
invisible — nothing on screen advertises either, and a one-time mirror-bar hint is not
discoverability. That makes the app's headline feature unreachable for anyone who does not
already know the gesture, which is the opposite of the intended audience. It also breaks the
`gesture-alternative` rule: critical actions must have a visible control, never a gesture alone.

Double tap has a second cost. Compose cannot resolve a single tap while a double-tap listener
is attached — it has to wait out the ~300ms window first — so every shutter press is late by
that much. Removing the double tap makes the shutter fire on finger-up.

**New control map:**

| Control | Action | Visible? |
|---|---|---|
| Orb, single tap | Take the photo (or confirm a decision / finish listening) | Yes — it is the shutter |
| Orb, long press | Auto-enhance: the AI inspects the scene and fixes it itself | No — shortcut only |
| Mic button | Voice input: tap to start, tap again to send | Yes |

The mic sits **beside the Orb**, not in the top bar: voice is a primary action and must be
thumb-reachable in portrait. It is tap-to-start / tap-to-stop rather than hold-to-talk —
holding a button steady while speaking is hard with tremor or arthritis, and the review
screen's mic already behaves this way.

**Keeping four persistent controls:** the mic makes five, so flash comes out of the top bar.
Voice already covers it — `SET_FLASH` (off/on/torch) and `SET_CAMERA` (front/rear/toggle) are
both in the command contract — and a mic earns that space far more than flash does. Final set:
mic, Orb, flip, settings.

**Known risk:** on most phones a held shutter means burst or video. Someone holding the Orb
expecting a burst gets an AI adjustment instead. Non-destructive, and the mirror bar reports
what happened, but it is a surprise worth watching in testing.

- [x] Add a mic button beside the Orb (portrait) and into the control strip (landscape)
- [x] Remap Orb long press from `onMicrophone` to `onAutoEnhance`
- [x] Remove `onDoubleTap` from the Orb so a single tap fires immediately
- [x] Move flash out of the top bar; keep four persistent controls
- [x] Drop the `settleDoubleTapWindow()` helper from the instrumented tests once the wait is gone
- [x] Rewrite guide card 2 and 3 for the new mapping (mic button, hold to auto-enhance)
- [x] Update `CaptureScreenTest` gesture tests and `chromeShowsOnlyFourPersistentControls`

**Follow-on (optional):** route a spoken "make it nicer" to `makeItNicer()`. Today voice goes
down the general command-plan path, which uses a different system prompt, so auto-enhance is
reachable only from the gesture. Wiring the intent would make the long press a pure shortcut
rather than the sole route, and would open the door to 3g.

#### 3b. ARCore dependency + session setup
**Files:** `app/build.gradle.kts` (add dependency), create `arcore/ArSession.kt`
**What it does:** Add `com.google.ar:core:1.40+` dependency. Create an AR session manager that
attaches to the CameraX lifecycle. ARCore shares the camera with CameraX via shared camera access
(Camera2 interop). The AR session provides real-time 6DoF pose data.

Important: ARCore availability varies by device. Must check `ArCoreApk.getInstance().checkAvailability()`
and gracefully degrade — auto-capture simply won't work on unsupported devices, but manual capture
and all other features remain functional.

- [x] Add `com.google.ar:core:1.40+` to `app/build.gradle.kts`
- [x] Create `arcore/ArSession.kt` — lifecycle-aware AR session manager
- [x] Add ARCore availability check with graceful fallback
- [x] Wire AR session into `CaptureViewModel` (null when unavailable)

#### 3c. Device tilt / height / stillness tracking
**Files:** `arcore/SpatialTracker.kt` (new), `CaptureViewModel.kt`
**What it does:** Reads ARCore pose data at 60fps to derive:
- **Tilt:** pitch/roll of device relative to gravity (used for "hold level" guidance)
- **Height:** camera Y position relative to detected floor plane (for framing)
- **Stillness:** movement magnitude over a 500ms sliding window. Stillness = magnitude < threshold

Expose as `spatialState: StateFlow<SpatialState>` from ViewModel where:
```kotlin
data class SpatialState(
    val tiltDegrees: Float,       // 0 = level
    val heightMeters: Float?,     // null if no floor plane detected
    val isStill: Boolean,         // true if < threshold for 500ms
    val stillnessDuration: Long,  // ms of continuous stillness
)
```

- [x] Create `arcore/SpatialTracker.kt` with pose → tilt/height/stillness logic
- [x] Define `SpatialState` data class in `SpatialTracker.kt`
- [x] Expose `spatialState: StateFlow<SpatialState>` from `CaptureViewModel`
- [x] Connect AR frame updates → spatialTracker → _spatialState in ViewModel init

#### 3d. Zero-Shake auto-capture trigger
**Files:** `CaptureViewModel.kt`, `HelperOrb.kt`
**What it does:** When BOTH conditions are met simultaneously:
1. AI has reached decided/sage state (VisibleCoachingState.ACTION + recommendation applied)
2. ARCore confirms phone stillness for 500ms continuous

...then auto-capture fires. The Orb flashes sage, audio cue plays, photo is taken.

User can disable auto-capture in Settings (toggle in "Camera" group). When disabled, the Orb
still shows decided state but waits for manual tap.

- [x] Add auto-capture logic in `CaptureViewModel` combining coaching state + stillness
- [x] Add `autoCaptureEnabled` toggle to `UserPreferences` and `SettingsSheet`
- [x] Orb sage flash animation on auto-capture trigger
- [ ] Respect orientation lock during active AI session (deferred — requires Activity-level config)

#### 3e. Audio cue system
**Files:** Create `AudioCuePlayer.kt`, `CaptureViewModel.kt`
**What it does:** Subtle audio feedback at key moments. Uses `SoundPool` for low-latency playback.

| Event | Sound | Notes |
|---|---|---|
| Auto-capture fires | Camera click | Classic shutter sound, short |
| AI reaches decided | Soft chime | Single tone, not intrusive |
| Manual capture | Camera click | Same as auto-capture |

No audio during WORKING state (would be annoying during processing). No haptics during capture
(destabilizes phone — see design decision in Section 12).

Audio respects system media volume. Sound assets bundled as `.ogg` in `res/raw/`.

- [x] Create `AudioCuePlayer.kt` wrapping `SoundPool`
- [x] Bundle shutter click and chime audio assets in `res/raw/` (silent stubs — replace with real sounds)
- [x] Play shutter on capture (auto and manual)
- [x] Play chime on decided state arrival
- [x] Respect system volume (USAGE_ASSISTANCE_SONIFICATION follows system volume automatically)

#### 3f. Smart Auto-Capture integration
**Files:** `CaptureViewModel.kt` (orchestration)
**What it does:** Full integration of all Phase 3 components into a cohesive flow:

1. User activates Orb → AI analyzes scene (Orb goes coral→mango)
2. AI reaches recommendation → Orb goes sage, MirrorBar shows instruction
3. For Level 4 (camera settings): auto-applied, green flash, done
4. For Level 3 (positioning): overlays guide user, AI re-evaluates when stillness detected
5. When AI confirms good framing AND phone is still for 500ms → auto-capture
6. Audio chime on decided, audio click on capture
7. Goes to review screen

The "Smart Auto-Capture" is the culmination — it's not a separate feature but the orchestration
of ARCore stillness + AI confidence + auto-capture trigger + audio cues.

- [x] End-to-end orchestration: apply/completeWork → chime → arm readyForAutoCapture → stillness → capture
- [x] Stillness re-evaluation via spatialState collector (re-arms on each new still detection)
- [ ] Orientation lock during active AI session (deferred — requires Activity-level config)
- [x] Graceful degradation path (arSession=null → auto-capture skipped, all else works)

### Phase 4: Polish & Ship

Phase 3 delivered the sensor intelligence layer. Phase 4 is the final stretch: UI polish from
the design mockup review, brand identity in the launcher, and the remaining deferred items that
block a release. No new features — just finishing what's started and making it tight.

**Pre-requisites (from Phases 1–3):**
- Full theme system, Quicksand typography, dark+light, Helper Orb, MirrorBar
- `autoApplyRecommendations = true` already auto-applies `ApplySettings` in the ViewModel
- ARCore smart-capture wired with audio cues
- `DecisionSurface` (frosted card) and `CoachingControls` (review card) in CoachingCard.kt

#### 4a. Landing screen polish

**Files:** `LandingScreen.kt`

**What it does:** Two changes from the mockup review:
1. **Bigger app name** — "Photo Helper" is currently `titleMedium` (18sp). Bump to
   `headlineMedium` (22sp) or larger (~28sp) so it reads as the brand, not a subtitle.
2. **Add tagline** — "The photo your moment deserved." in italic, `bodyMedium` or `bodySmall`,
   `onOverlayDim` color, positioned between the app name and the "Tap to Start" CTA.

The subtitle "Say what you want. The camera does the rest." stays below the CTA as the functional
explanation. The tagline above the CTA is the emotional hook.

- [x] Increase "Photo Helper" text style from `titleMedium` to `headlineMedium` or custom 28sp
- [x] Add tagline "The photo your moment deserved." between name and CTA
- [x] Verify landing screen looks balanced on Pixel 7 emulator

#### 4b. Center the Orb in portrait

**Files:** `CaptureScreen.kt` (portrait layout, lines 283–299)

**What it does:** The Orb is currently in a `Row` with `MicrophoneButton`, centered as a group.
Because the mic takes space on the left, the Orb sits slightly right of center. The Orb should
be dead-center — it's the primary control and the brand signature.

**Change:** Replace the `Row` with a `Box`. The Orb stays centered via `Alignment.Center`. The
mic button gets absolute positioning to the left of the Orb (e.g. `Modifier.align(Alignment.CenterStart)`
with appropriate padding, or `offset`). The visual result: Orb on the axis, mic clearly to one side.

- [x] Replace portrait `Row(MicrophoneButton, HelperOrb)` with `Box`-based layout
- [x] Orb centered via `Alignment.Center`
- [x] Mic positioned to the left of the Orb without pushing it off-axis
- [x] Verify touch targets don't overlap (mic 44dp + 16dp gap + Orb 72dp)
- [x] Landscape control strip layout unchanged (mic already stacked vertically)

#### 4c. Suppress decision card flash for auto-applied settings

**Files:** `CaptureViewModel.kt`, `CoachingCard.kt`

**What it does:** The ViewModel already auto-applies `ApplySettings` recommendations
(`autoApplyRecommendations = true`, line 83). But the current flow briefly sets
`decision = recommendation` in state (line 1330–1334) before `applyRecommendation()` nulls it
(line 385). This can cause a one-frame flash of the decision card on the camera screen.

**Fix options (pick one):**
- **Option A:** For `ApplySettings` when auto-applying, skip setting `decision` in
  `_uiState` entirely — go straight to `CoachingPhase.APPLYING` with `decision = null`.
  The mirror bar already shows status text for APPLYING ("Applying…"), and the sage flash
  fires on completion. The review screen's `CoachingControls` is unaffected because it
  reads `decision` independently.
- **Option B:** Have `DecisionSurface` filter out `ApplySettings` recommendations so the
  camera-screen card never renders for them, even if the state briefly holds the decision.

After the apply completes, the mirror bar should show the action description (e.g.
"Brightened +0.7 EV") as a transient message rather than the generic "Applying…".

- [x] Eliminate one-frame decision card flash for `ApplySettings` auto-apply
- [x] Mirror bar shows descriptive result text after auto-apply (not just "Applying…")
- [x] `GuidePosition` and `Clarify` decisions still show their cards as before
- [x] Review screen `CoachingControls` still shows full explanation cards (not affected)

#### 4d. Brand app icon — Jarvis gradient ring

**Files:** `res/drawable/ic_launcher.xml`, create `res/drawable/ic_launcher_foreground.xml`,
create `res/drawable/ic_launcher_background.xml`, create `res/mipmap-anydpi-v26/ic_launcher.xml`

**What it does:** The current launcher icon is the pre-redesign blue camera on dark background
(`#8DCDFF` on `#111315`). Replace with the Jarvis gradient ring — the Orb as the app icon,
matching the brand identity established in Phase 2.

**Icon design:**
- **Foreground:** Ring shape (the Orb outline) with gradient Coral→Mango→Sage. Centered in the
  adaptive icon safe zone (66dp inner circle of the 108dp canvas).
- **Background:** Charcoal `#36454F` solid fill.
- For adaptive icon (API 26+): separate foreground/background layers.
- For legacy: single `ic_launcher.xml` vector drawable combining both.

Note: Vector drawable gradients require API 24+ (`<gradient>` in VectorDrawable). Since
`minSdk` is 31, this is fine.

- [x] Design foreground layer: ring with Jarvis gradient
- [x] Create `ic_launcher_background.xml` (solid Charcoal)
- [x] Create `ic_launcher_foreground.xml` (gradient ring)
- [x] Create adaptive icon XML in `res/mipmap-anydpi-v26/`
- [x] Update `ic_launcher.xml` legacy fallback
- [x] Update `AndroidManifest.xml` if icon/roundIcon attributes need changing
- [ ] Verify icon renders correctly in launcher on emulator — **not done**, see Ship Readiness

#### 4e. Verify audio assets

**Files:** `res/raw/cue_shutter.wav` (7 KB), `res/raw/cue_chime.wav` (40 KB)

**What it does:** Audio files exist and are non-empty, but were initially described as stubs.
Need to verify on a real device that:
- Shutter click sounds like a camera shutter (short, crisp)
- Chime sounds like a soft notification (single tone, not intrusive)
- Volume levels are appropriate at USAGE_ASSISTANCE_SONIFICATION
- No clipping or artifacts

If the current files don't sound right, source or synthesize replacements. Both should be
short (shutter < 200ms, chime < 500ms) and .ogg or .wav format.

- [ ] Test audio on real device (emulator audio is unreliable)
- [ ] Replace with better assets if current ones sound wrong
- [ ] Verify both play at appropriate volume relative to system media

#### 4f. Orientation lock during active AI session

**Files:** `MainActivity.kt`, `CaptureViewModel.kt`

**What it does:** When the AI is active (LISTENING, INTERPRETING, REQUESTING, RECOMMENDATION,
APPLYING, GUIDING, VERIFYING), lock the screen orientation to whatever it was when the session
started. This prevents spatial confusion — if the AI says "step left" and the user rotates,
the arrow would flip and the instruction becomes wrong.

**Implementation:** Expose a `shouldLockOrientation: StateFlow<Boolean>` from the ViewModel
(true when coaching phase is not IDLE). In `MainActivity`, collect it and call
`requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED` / `SCREEN_ORIENTATION_UNSPECIFIED`.

- [x] Add `shouldLockOrientation` derived state in ViewModel
- [x] Collect in `MainActivity` and set `requestedOrientation`
- [ ] Verify orientation unlocks cleanly when returning to IDLE — **needs a device**, the emulator does not rotate reliably under test
- [ ] Test: start voice → rotate phone → confirm orientation stays locked — **needs a device**

#### 4g. Voice confirmation for decision cards (blocked)

**Files:** Coach engine (`DefaultCoachEngine.kt`), `CaptureViewModel.kt`

**Status:** Deferred from Phase 2f. The coach engine currently has no path to interpret a
spoken "yes" or "no" as card confirmation or dismissal. The voice input always goes through
the full command interpretation pipeline.

**What's needed:** A lightweight check in the voice-input path: if a decision card is showing
and the transcript is a simple affirmative/negative ("yes", "ok", "do it", "no", "cancel",
"never mind"), route it to confirm/dismiss instead of the command pipeline.

This is not just UI wiring — it requires changes to the coach engine's input routing. It is
blocked until someone works on the engine, and is not a ship blocker since tap confirmation
works fine.

- [ ] Add affirmative/negative detection in voice input path
- [ ] Route to `onApplyRecommendation()` or `onDismissDecision()` accordingly
- [ ] Only activate when a decision card is visible

#### 4h. Button size / text size accessibility settings

**Files:** `SettingsSheet.kt`, `UserPreferences.kt`, `PhotoHelperTheme.kt`

**What it does:** The settings design (Section 8) specifies "Button size (S/M/L)" and
"Text size (adjustable)" under the Interaction group. Neither is implemented. Currently
the Orb is fixed at 72dp and text follows the Quicksand type scale.

**Scope question:** Android already provides system-level font scaling
(`Configuration.fontScale`) and display size settings. The app's 14sp minimum and 72dp Orb
already work well with the system accessibility settings. Custom in-app controls may be
redundant — or they may be valuable for elderly users who don't know about system settings.

Decision: defer to system accessibility settings for now. Revisit if user testing shows
elderly users can't find or use the system font size controls.

- [x] Decide: custom in-app size controls vs. rely on system accessibility — **decided: rely on the system.** Verified at 2x scale: the mirror bar wraps, the Orb stays centred and clear of the bottom edge, chrome is unaffected.
- [ ] If custom: add Orb size preference (56/72/88dp) and font scale multiplier
- [x] If system: verify app renders correctly at system font scales 1.0–2.0x

#### 4i. Real-device ARCore testing

**What it does:** The smart-capture flow (ARCore stillness + AI confidence → auto-capture)
cannot be verified on the emulator. ARCore requires a physical device with a supported
camera and IMU. The emulator gracefully degrades (`arSession = null`), so all other features
work, but the headline Phase 3 feature is untested.

**Test plan:**
1. Install on an ARCore-supported device (Pixel 5+, most Samsung Galaxy S/A series)
2. Activate AI via long-press Orb → verify spatial tracking starts
3. Hold phone still for 500ms after AI reaches decided → verify auto-capture fires
4. Move phone during decided state → verify auto-capture does NOT fire
5. Disable auto-capture in settings → verify manual tap still required
6. Test on a device without ARCore → verify graceful degradation (no crash, manual capture works)

- [ ] Test on physical ARCore-capable device
- [ ] Verify stillness detection threshold feels right (too sensitive? not enough?)
- [ ] Verify audio cues play correctly on device speakers
- [ ] Verify graceful degradation on non-ARCore device

---

### Ship Readiness Summary

| Item | Status | Blocker? |
|---|---|---|
| 4a. Landing polish (tagline, bigger name) | Done, verified on emulator | No |
| 4b. Center Orb in portrait | Done, verified on emulator | No |
| 4c. Suppress card flash for auto-apply | Done, unit tests updated | No |
| 4d. Brand app icon | Code done; launcher render unverified | No |
| 4e. Verify audio assets | Not done | Needs device |
| 4f. Orientation lock | Code done; rotation unverified | Needs device |
| 4g. Voice confirmation | Blocked | Coach engine work needed |
| 4h. Button/text size settings | Decided: use system scaling, verified at 2x | No |
| 4i. Real-device ARCore testing | Not done | Needs device |

**Also fixed during 4:** a crash in `CaptureReview` (`requireNotNull(state.review)` could fire
because `AnimatedContent` keeps the outgoing slot composing after the caller's null-check —
the capture is now passed in as a parameter), and `TestActions` was missing
`onAutoCaptureEnabledChanged`, which broke the instrumented source set.

**Verified:** 211 unit tests, lint, and 65 instrumented tests all pass.

**Ship blockers:** 4a–4d are code changes that can be done now. 4e, 4f, 4i need a physical
device. 4g is blocked on engine work and not a ship requirement. 4h is a design decision.

---

---

### Phase 5: From Shipped to Exceptional (discussion draft)

Phases 1–4 took the app from the 5.2/10 audit to something coherent and shippable. This phase is
the argument for what stands between "good" and 10/10. Nothing here is committed — it is written
to be argued with.

**The honest framing.** The original audit scored craft: colour, type, density, identity. Phases
1–4 largely answered those. But a 10/10 *app* is not a 10/10 *design audit*. Three of the items
below (5a, 5b, 5c) are the difference between "we believe it works" and "we know it does", and
they matter more than any remaining visual polish.

#### Re-scoring after Phases 1–4

| Category | Audit | Now (est.) | What still caps it |
|---|---|---|---|
| Architecture | 8.0 | 8.5 | No previews, no screenshot tests — every visual change needs a device |
| Accessibility | 7.5 | 8.5 | Never tested with real TalkBack; all copy is English-only |
| Interaction | 5.5 | 8.0 | No press feedback on the Orb; AI latency is unmeasured and unmasked |
| Color system | 4.0 | 8.5 | Contrast verified by calculation, not on a real panel |
| Typography | 4.0 | 8.0 | 5a fixed: Quicksand is bundled and verified rendering. Remaining cap: no type specimen or preview coverage |
| Information density | 3.5 | 8.0 | Verified by reading, not by watching anyone use it |
| Simplicity | 3.5 | 8.0 | 9 coaching phases still exist internally; 3 are shown |
| Visual identity | 3.0 | 8.5 | Icon and Orb landed; no motion signature, no splash |

#### 5a. The brand typeface does not load (defect, not polish)

**Evidence:** `PhotoHelperTheme.kt` builds its `GoogleFont.Provider` with
`certificates = emptyList()`. The Play Services font provider authenticates callers against a
certificate array (`R.array.com_google_android_gms_fonts_certs`); with an empty list the request
is rejected and Compose silently falls back to the system default. There is no `font_certs` array
anywhere in `app/src/main/res/`. Every emulator screenshot through Phases 2–4 renders in Roboto,
not Quicksand.

So the entire typography workstream — the type scale, the weights, the "personality" the
`frontend-design` audit asked for — is currently invisible. Typography is still a 4.0.

**Fix:** bundle Quicksand as a real font resource (`res/font/quicksand_*.ttf`, `FontFamily` from
`Font(R.font.…)`). Bundling also removes a runtime network dependency and a Play-Services
requirement, which matters for the offline story in 5d. Downloadable fonts are the wrong trade
for a brand face this app is built around.

- [x] Bundle Quicksand weights in `res/font/` and switch `QuicksandFontFamily` to local resources
- [x] Verify on device that headings and body actually render in Quicksand — confirmed in light and dark
- [x] Remove the `ui-text-google-fonts` dependency if nothing else needs it

#### 5b. Previews and screenshot regression tests

`ui-ux-pro-max` Compose rule 37 (no `@Preview` composables) was flagged in the original audit and
is still open: `grep -c "@Preview"` over `app/src/main` returns **0**. Every visual change in this
project has required a build, an install, and a screenshot — which is why an emulator that ANRs
twice costs an hour.

The 65 instrumented tests assert semantics and layout bounds. None of them would catch the Orb
rendering the wrong colour, the mirror bar overlapping the Orb, or a theme token regressing.

- [ ] Add `@Preview` composables for Orb states, MirrorBar, DecisionSurface, LandingScreen, guide cards
- [ ] Previews for light + dark and for fontScale 1.0 / 2.0
- [ ] Add screenshot regression tests (Roborazzi or Paparazzi — runs on JVM, no emulator)
- [ ] Wire screenshot tests into the same command as `testDebugUnitTest`

#### 5c. Real-device validation

Everything sensor-shaped is unverified: ARCore stillness, audio cues, haptics, real autofocus,
real speech recognition, and the launcher icon. The emulator degrades all of them. This is the
largest single gap between believing the app works and knowing it.

Carries over 4e, 4f, 4i, plus:

- [ ] One pass with TalkBack actually enabled, start to finish
- [ ] Verify contrast on a real OLED panel (emulator colour is not trustworthy)
- [ ] Confirm the adaptive icon masks correctly on a launcher that uses circles and squircles

#### 5d. A designed degraded mode

Today, with no network or no key, the AI silently falls back to local coaching and posts a
transient message. That is a reasonable failure path but not a designed one, and the user is
never told the headline feature is unavailable — the key-setup banner was dropped in Phase 1 and
never replaced.

For an app whose pitch is "say what you want", the offline experience deserves a deliberate
answer: what is still true, what is not, and how the Orb should say so.

- [ ] Decide what the Orb looks like when the AI is unreachable (a fourth idle treatment?)
- [ ] Replace the dropped key-setup signal with something that fits Zero Interface
- [ ] Make local-only coaching an honest, named mode rather than a silent fallback

#### 5e. Perceived performance

`ui-ux-pro-max` asks for feedback within ~100ms of a tap and input latency under ~100ms. The
voice path is a network round trip to Qwen with only a colour change to cover it, and the Orb has
**no press feedback at all** — no ripple, no state layer, no haptic.

- [ ] Press state on the Orb (ring brightening or scale) plus a haptic tick on down
- [ ] Measure voice → plan → applied end to end; publish the number
- [ ] Mask the wait: progressive mirror-bar copy, or optimistic application with undo

#### 5f. Before/after is the missing product core

The app coaches you to a better photo and then throws the evidence away. There is no history, no
comparison, no "here is what changed". For a coaching product, the before/after *is* the proof of
value, and it is the single most demo-able thing the app could have.

This is the one item here that is a feature rather than a fix, and the one most likely to move a
judge or a user from "neat" to "I want this".

- [ ] Keep the pre-adjustment frame alongside the capture
- [ ] A before/after view in review (slider or tap-to-toggle)
- [ ] Name the change that was made ("Brightened +0.7 EV") against the comparison
- [ ] Decide whether a session history is in scope or out

#### 5g. Localisation

`res/values/strings.xml` contains exactly one string (`app_name`). Roughly 215 user-facing
literals live in Kotlin. The stated audience is elderly users and family members — precisely the
audience most likely to need a language other than English.

- [ ] Extract user-facing strings to `strings.xml`
- [ ] Confirm layouts survive longer translations (German/Finnish are the usual stress test)
- [ ] Decide the launch locale set

#### 5h. Thumbs up / thumbs down, feeding a learned profile

Replaces the earlier sketch of "a style profile that learns" with a concrete design.

**The signal.** After a change is applied — or on the review screen after a capture — offer a
thumbs up and a thumbs down. Up means the agent did well, down means it did badly. Nothing else
to fill in, because anything heavier will not get used.

Each vote is stored with what it is a verdict *on*: the recommendation kind, the axis and
direction (`EXPOSURE_DARKER`, `WHITE_BALANCE_WARMER`, `ZOOM_IN`…), the strength, and the frame
measurements at the time. A vote with no context cannot teach anything later.

**The learned profile.** A rolling summary of those votes, capped at **200 words**, written in
the user's terms rather than as statistics — "prefers warmer skin tones; dislikes zooming in on
group shots; usually accepts brightening indoors". This is separate from the hand-written Style
Profile (`MAX_STYLE_PROFILE_CHARACTERS = 400`), which stays as the user's own words. Two fields:
one the user writes, one the app learns.

**Where it enters the loop.** `commandSystemPrompt` already fences the hand-written style
profile as preference data. The learned profile joins it under the same fence and the same rule:
it may bias an aesthetic judgement, never the JSON shape, the action set, or authorisation for a
change the user did not request. That fencing is not optional — the learned text is derived from
user behaviour and must not become an instruction channel.

**Compaction when it outgrows the cap.** Votes accumulate forever; 200 words does not. When the
profile would exceed the cap, it is rewritten — the model is asked to fold the newest votes into
the existing summary and return a shorter one, keeping durable preferences and dropping
one-offs. Compaction is itself a model call, so it should be rare: batch it, run it when the
camera is idle, never on the capture path.

**What has to be decided before building:**
- Do votes on a *photo* and votes on a *change* mean the same thing? A thumbs down after
  capture might be about the change, the framing, or the moment. Voting on the change, right
  after it lands, is the less ambiguous signal
- How many votes before the profile is worth consulting at all? Below some floor it is noise
- Does a thumbs down trigger an immediate undo, or only inform later shots? Undo is the more
  useful reading of "you did badly" in the moment
- Compaction is lossy and invisible. The user should be able to read the learned profile, and
  clear it, from Settings

- [ ] Thumbs up / down control, on the change and/or in review (decide which)
- [ ] Persist votes with their context (action, axis, direction, strength, frame metrics)
- [ ] Derive a 200-word learned profile; fence it alongside the style profile in the prompt
- [ ] Compact when over the cap, off the capture path
- [ ] Make it readable and clearable in Settings
- [ ] Decide the open questions above before any of it ships

**Tension worth naming.** ADR 0001 keeps diagnosis and authority on-device, and ADR 0002
specifies "no remembered consent". A learned profile is remembered state derived from the user,
which is exactly what those decisions avoided. It is a reasonable thing to change — both ADRs
are already marked superseded — but it should be a written decision, not a quiet drift.

#### 5i. Motion signature

`frontend-design` asks for "the single unique element this page will be remembered by". The Orb is
that element visually, but its motion is currently a 3-pulse glow. There is no signature
transition — no shared-element move from landing to camera, no choreographed sweep when a decision
lands.

- [ ] One memorable transition, done well (landing CTA → Orb is the obvious candidate)
- [ ] A splash screen using the icon's gradient ring
- [ ] Keep the reduced-motion path honest for all of it

#### 5j. Landscape is an afterthought

Reported from device screenshots. Two separate problems:

**Camera.** The viewfinder is letterboxed — a dead black band on the left, the 72dp control strip
on the right, and the preview floating between them. The mirror bar is not visible at all in the
capture, so the app's only instruction channel disappears in landscape. The four controls are
stacked in a narrow rail that reads as a toolbar rather than the deliberate chrome it is in
portrait.

**Review.** Worse. The photo is squeezed into the top half and the cream panel eats the bottom
45% for one line of text and three buttons. On a landscape phone that is the wrong split — the
photo should dominate and the controls should overlay it, as they already do on the camera screen.

- [ ] Make the preview fill the landscape viewfinder (or letterbox deliberately, not accidentally)
- [ ] Ensure the mirror bar is visible and legible in landscape
- [ ] Re-split the landscape review: photo dominant, controls as overlay rather than a half-height panel
- [ ] Add landscape to the preview/screenshot matrix from 5b so this stops regressing silently

#### 5k. The post-capture moment is undesigned

"When the photo is taken, then what happens" is a fair question, and the honest answer today is:
a full-screen review appears with the saved photo, a CAPTURED chip, "Original remains saved", and
Retake / Done / mic. It works, but it is the least designed screen in the app — it still looks
like the pre-redesign UI while everything around it moved on.

It is also the moment that matters most. The capture is the payoff; right now the payoff is a
form. This overlaps heavily with 5f (before/after), and the two should probably be designed as
one screen rather than two features.

- [ ] Decide what the post-capture moment should *feel* like before adding controls to it
- [ ] Apply the frosted-overlay language from the camera screen instead of the cream panel
- [ ] Fold 5f (before/after) into this screen rather than bolting it on
- [ ] "Original remains saved" is reassurance copy for a fear the user may not have — re-examine it

#### 5l. Claude as a second provider (done)

`ClaudeVisualClient` implements the same two calls as `BailianVisualClient`, selected by
`VisualProvider` under Settings → Advanced → Model. Both arms share the prompts
(`visualPrompt`, `commandSystemPrompt`), the images, the focus-grid guide, and the response
parsers (`parseVisualHint`, `parseCommandContent`) — only the wire format differs, so an A/B
measures the model rather than two differently worded asks.

Uses the official Anthropic Java SDK (`com.anthropic:anthropic-java`), model
`claude-haiku-4-5` — the cheapest vision-capable tier at $1/$5 per MTok against Opus 5 at
$5/$25. The work is bounded (two images in, one small JSON object out against a strict
schema), so the extra capability was not buying anything. No `thinking` or `effort` parameter:
both are 4.6+ features and `effort` is rejected outright on 4.5-era models.

Keys come from a gitignored `.env` at the repo root (see `.env.example`), read at build time
into `BuildConfig` and used as the fallback when nothing has been pasted into settings.

Wire-format differences from the OpenAI-compatible Qwen path: images are `image` blocks with a
base64 `source` rather than `image_url` data URLs; `max_tokens` not `max_completion_tokens`;
no `response_format` (the JSON contract lives in the shared prompt); and a response can carry
`stop_reason = refusal`, which has no Qwen analogue.

**Open items:**
- [ ] Run both arms against the same scenes and compare decisions — no A/B has been run yet
- [ ] Debug APK grew 106.8 MB → 148.3 MB (+41.4 MB, mostly dex). Check the release build with
      R8 before drawing conclusions; if it stays large, consider raw HTTP for this one client
- [ ] Refusal fallbacks are not wired. `stop_reason = refusal` is handled and surfaced as a
      failure, but the server-side `fallbacks` parameter is not set — the Java builder methods
      for it are not in the bundled SDK docs
- [ ] Structured outputs (`output_config.format`) deliberately not used — per-provider schemas
      would diverge from the Qwen arm and break the comparison. Revisit once a provider is chosen
- [ ] Settings copy and the privacy notice still name Alibaba Cloud unconditionally
- [ ] `MAX_RESPONSE_CONTENT_BYTES` (512) is shared by both arms; confirm Claude's replies fit
- [ ] The `.env` key is compiled into the APK and is extractable — fine for a dev build on your
      own device, not for anything shipped

#### 5m. Grid reading works; Claude's output framing did not (fixed)

The focus grid had never been seen in the app. Three separate causes, all now addressed:

**1. The AI never ran.** `visualAiEnabled` defaulted to `false` even when a key existed — it
only became true via "Test, save & enable", which the `.env` path never calls. And the provider
defaulted to Qwen regardless of which key was present. So a key in `.env` looked like it did
nothing. Now: a key that exists defaults the feature on, and with exactly one provider keyed in
`.env` the app starts on that provider.

**2. The grid is not user-facing, by design.** `createFocusGridGuide` draws the labelled grid
onto a *second image sent to the model* — it is never rendered on screen. What the user can see
is `FocusCellOutline` (the mango cell rectangle) plus the focus reticle, and only when a
`FocusAt` recommendation exists. If the expectation was to see the grid itself, that is a
product decision, not a bug — see 5k.

**3. Claude read the grid correctly but framed the answer wrongly.** Verified live against
`claude-haiku-4-5` with `scripts/claude-live-smoke.py`, which mirrors the app's guide renderer
and sends the verbatim OBJECT_FOCUS prompt:

| Case | Raw response | Cell |
|---|---|---|
| neutral-portrait, "the person's face" | ` ```json {...} ``` ` | column 3, row 3 |
| close-face, "the face" | ` ```json {...} ``` ` | column 3, row 2 |
| cold-blue-scene, "the brightest object" | prose preamble, then JSON | column 4, row 3 |

So the prompt **is** strong enough to make the model use the grid — every case returned a
plausible, in-range cell. What failed was output discipline: two responses were wrapped in
markdown fences and one led with a sentence, and the strict parsers reject all three.

Root cause: DashScope enforces JSON server-side via `response_format: {"type":"json_object"}`.
The Claude client set no equivalent constraint, so Claude was being asked for JSON by prompt
alone while Qwen was being *made* to produce it. That was an unfair handicap in the A/B, not a
prompt-quality difference.

Fix: `ClaudeVisualClient.extractJsonObject` pulls the first balanced top-level JSON object out
of the reply, ignoring fences and surrounding prose. Provider-specific normalisation lives in
the provider client, so the shared parsers stay strict. **3/3 after the fix.**

- [ ] Replace extraction with a real constraint — `output_config.format` with a JSON schema is
      Claude's equivalent of DashScope's JSON mode. Needs one schema per response shape
      (TARGET/CLARIFY, PLAN/CLARIFY, ASSESSMENT/UNSURE); extraction is the interim measure
- [ ] The "brightest object" case burned 270 output tokens on reasoning before answering, vs 27
      for the face cases. A schema should collapse that
- [x] Only OBJECT_FOCUS has been exercised live — all three paths now covered, see 5n

#### 5n. All three Claude paths verified live (done)

`ClaudeLiveSmokeTest` (androidTest) drives `ClaudeVisualClient` itself — real prompts, real
grid guide, real parsers — so unlike `scripts/claude-live-smoke.py` this is evidence about the
app rather than about a Python replica. Against `claude-haiku-4-5`:

| Path | Result |
|---|---|
| `interpret` OBJECT_FOCUS, "the bright square" | `FocusCell(row=2, column=4, rows=8, columns=6)` |
| `plan` "make it brighter" | `Planned(Adjust(EXPOSURE_BRIGHTER))` |
| `plan` autoEnhance on a dark frame | `Planned(Adjust(EXPOSURE_BRIGHTER), Adjust(ZOOM_IN))` |

The answers are correct, not merely parseable:

- The fixture is 480×640 with a bright square spanning x 300–420, y 120–240. `FocusGrid.forImage`
  gives 6×8, so cells are 80×80 and the square's centre (360, 180) lands in **column 4, row 2** —
  exactly what came back. The model is reading the printed labels, not guessing.
- Auto-enhance chose ZOOM_IN because the square occupies ~4.7% of the frame, and the prompt says
  a clear subject below about 25% with incidental empty space is ZOOM_IN. It followed the rule.

**These tests cost money on every run**, so they are opt-in twice: a key must be present *and*
`liveApi=true` must be passed. The default `connectedDebugAndroidTest` reports 68 tests with
these 3 skipped.

    ./gradlew.bat connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.bolin.photohelper.visual.ClaudeLiveSmokeTest \
      -Pandroid.testInstrumentationRunnerArguments.liveApi=true

**Still open:**
- [ ] No Qwen key available, so the two providers have not actually been compared. Every Claude
      result above is absolute, not relative — "Claude works" is established, "Claude is better
      or worse than Qwen here" is not
- [ ] One sample per path on a synthetic fixture. Real scenes, and repeat runs for variance,
      are what would justify a provider decision
- [ ] Latency was not measured; the whole point of the cheap tier is that it should be quick

#### 5o. Settings verification and bounded retry (done)

Two attempts, both visible, then an honest concession.

**What was actually wrong.** `coach.verify` had exactly one call site, inside `verifyActiveWork`,
which returns early unless `activeGuidance != null` — and that is only set for physical movement
guidance. So **settings changes were never verified**: `CoachingPhase.VERIFYING` was assigned
zero times, and the fully implemented, unit-tested `VerificationTarget.Exposure` logic was
unreachable from the app. A brightness change that did nothing still reported success.

**Now.** After a settings change is applied the agent holds at `VERIFYING`, waits for a settled
comparable frame, and asks the engine whether the measurement actually moved the intended way:

- `Satisfied` / `Progress` → done, still showing what changed ("Darken by 0.7 EV")
- `Incomparable` → done, but says *why* it could not be proven rather than implying success
- `Unchanged` → one more attempt, announced in the mirror bar as "That did not take. Trying
  something else…", with the failed change already in `recentChanges` for the model to read

**What stops it.** The budget is "different approaches remaining", not a flat count:
- `exhaustedReason` checks the camera's own limits first — an EV or zoom axis already pinned is
  not retried, because the capabilities already answer the question without spending a call
- retry only runs on the model path; the local engine is deterministic, so a second run returns
  the same plan and the miss concedes immediately
- hard ceiling of two visible attempts, well inside `VISUAL_CALLS_PER_MINUTE = 6`

**Found while building:** the coach already refuses to plan a pinned axis at all
("Minimum brightness reached."), so no call is spent in that case. `exhaustedReason` is the
second line of defence for an axis that pins mid-loop.

Covered by three unit tests: local miss concedes without repeating itself, a second miss
concedes rather than looping, and a pinned axis never reaches the loop.

- [ ] Not yet exercised live — the retry path needs a model that can actually produce a second
      plan, so it wants a run against Claude with a scene where the first attempt genuinely fails
- [ ] Cancel is reachable during the loop via back, but still not visible on screen (see 5e)

---

### Phase 5 — recommended order

1. **5a** first. It is a defect, it is cheap, and it silently invalidates a whole phase of work.
2. **5b** next, because it makes everything after it faster to verify.
3. **5c** before any further polish — there is no point refining what has never run on hardware.
4. **5f** if the goal is a product people want; **5d + 5g** if the goal is an app people can ship.
5. **5j** (landscape) is a correctness problem, not polish — it is broken today, on a device.
6. 5e, 5h, 5i, 5k are genuine polish and can follow in any order — though 5k and 5f should be
   designed together, not separately.

**Open questions for discussion:**
- Is 10/10 the audit rubric, or the product? They point at different work (5b/5c vs. 5f).
- Is before/after (5f) in scope, or is this a camera app that ends at the shutter?
- Does localisation matter for the actual first users, or is English enough for now?
- Is there a real device available for 5c, and which one?

## Gap Analysis

| Vision | Current Codebase | Status |
|---|---|---|
| `CaptureScreenActions` interface | ✅ Done (Phase 1) | Complete |
| Extracted component files | ✅ Done (Phase 1) — 7 files | Complete |
| Material Icons | ✅ Done (Phase 1) | Complete |
| Theme tokens | ✅ Done (Phase 1) — full token system | Complete |
| Quicksand typography | ✅ Done (Phase 1) — Downloadable Fonts | Complete |
| Dark + light color schemes | ✅ Done (Phase 1) — defined in theme | Complete |
| VisibleCoachingState | ✅ Done (Phase 1) — IDLE/WORKING/ACTION | Complete |
| collectAsStateWithLifecycle | ✅ Done (Phase 1) | Complete |
| Helper Orb | `CaptureBar` with 3 buttons | Phase 2 redesign |
| Gaussian blur landing | `Onboarding` with text steps | Phase 2 new screen |
| Style Profile | Not present | Phase 2 new feature |
| Glassmorphic overlays | Solid-color overlay buttons | Phase 2 visual update |
| Light mode working | Schemes defined but hardcoded colors remain | Phase 2 audit |
| Simplified onboarding | Developer-jargon onboarding | Phase 2 rewrite |
| Progressive settings | Flat settings dump | Phase 2 restructure |
| ARCore spatial tracking | Not in dependencies | Phase 3 new dependency |
| Zero-Shake / Auto-Capture | Manual shutter only | Phase 3 new feature |
| Smart Auto-Capture | Not present | Phase 3 orchestration |
| Audio cue system | Not present | Phase 3 new feature |

## Category Decisions

### 1. Theme & Color System — "Modern Balance"

**Palette:**
| Token | Hex | Role |
|---|---|---|
| `brandAccent` | `#FFB347` | Soft mango — accent, brand, active states |
| `charcoal` | `#36454F` | Dark neutral |
| `softCream` | `#FFF5EB` | Warm light neutral |
| `success` | `#8FBF9C` | Muted sage green — confirmations, "decided" state |
| `error` | `#E8887A` | Muted coral — errors, "uncertain" state |
| `overlayLight` | `charcoal @ 35%` | Lightest scrim |
| `overlayMedium` | `charcoal @ 55%` | Medium scrim |
| `overlayHeavy` | `charcoal @ 75%` | Heavy scrim |
| `overlayOpaque` | `charcoal @ 92%` | Near-solid backdrop |

**Mode interchange:**
- Dark mode: Charcoal background, Soft Cream for AI elements
- Light mode: Soft Cream background, Charcoal for AI elements
- Mango accent stays constant in both modes
- Camera viewfinder always dark regardless of mode

**Jarvis gradient (AI confidence):**
The Helper Orb ring color is a smooth continuous gradient tied to AI progress:
- `Coral #E8887A` (0%) → `Mango #FFB347` (50%) → `Sage #8FBF9C` (100%)
- Not discrete steps — fluid interpolation every frame
- Implemented as `Animatable<Color>` driven by a ViewModel confidence float (0f–1f)
- Applies to Orb ring and can extend to decision card buttons

**Orb color states:**
| State | Color |
|---|---|
| Idle | Soft Cream (dark mode) / Charcoal (light mode) |
| Active — uncertain | Coral end of gradient |
| Active — working | Mango mid-gradient |
| Active — decided | Sage end of gradient |

**Replaces:** All 12 hardcoded hex values, 4 magic alpha numbers, the competing amber/blue scheme.

### 2. Typography — Quicksand

**Family:** Quicksand (Google Fonts, bundled in `res/font/`). Single family for everything.

**Type scale:**
| Token | Use | Size | Weight |
|---|---|---|---|
| `displayLarge` | Landing CTA | 32sp | Medium (500) |
| `headlineMedium` | Card titles, section headers | 22sp | SemiBold (600) |
| `bodyLarge` | Camera overlay instructions | 18sp | Medium (500) |
| `bodyMedium` | Card descriptions, settings | 16sp | Regular (400) |
| `bodySmall` | Secondary info | 14sp | Regular (400) |
| `labelLarge` | Buttons, Orb text | 16sp | SemiBold (600) |
| `labelSmall` | Subtle indicators | 12sp | Medium (500) |

**Rules:**
- No manual `fontWeight` — every weight comes from named tokens
- Minimum 14sp for any user-facing text (elderly accessibility)
- 5-word camera instructions at 18sp Medium — readable at arm's length
- Line height: 1.4x body, 1.2x headings

**Replaces:** Raw `Typography()` defaults, ~15 ad-hoc `fontWeight = FontWeight.SemiBold` calls.

### 3. Iconography — Material Icons Extended (Rounded)

**Library:** `androidx.compose.material:material-icons-extended`
**Style:** Rounded — pairs with Quicksand's rounded letterforms and the warm, soft brand.

**Glyph replacements:**
| Current glyph | Replacement icon |
|---|---|
| `↻` | `Icons.Rounded.Refresh` |
| `⚙︎` | `Icons.Rounded.Settings` |
| `?` | `Icons.Rounded.HelpOutline` |
| `⚡︎×` | `Icons.Rounded.FlashOff` |
| `☼` | `Icons.Rounded.WbSunny` |
| `✨` | `Icons.Rounded.AutoAwesome` |
| `■` | `Icons.Rounded.Stop` |
| `Mic` | `Icons.Rounded.Mic` |
| `↶` | `Icons.Rounded.Undo` |

**Replaces:** All text glyph icons throughout `CaptureScreen.kt`.

### 4. The Helper Orb

**Size:** 72dp portrait (64dp in landscape control strip). Ring stroke 3.5dp.

**Position:**
- Portrait: Bottom-center, 24dp from edge
- Landscape: Inside a 72dp vertical control strip on the right side. Strip holds flash, flip, Orb, settings — stacked vertically

**Jarvis Gradient + Glow:**
- Ring color: smooth continuous gradient Coral → Mango → Sage tied to AI confidence (0f–1f)
- Outer glow: colored aura radiates from Orb, matches ring color, blurred 14dp
- Sage state: gentle pulse (opacity 0.35→0.55, glow 12→18dp, 2s ease-in-out loop)
- Ring also gets inner glow (inset box-shadow) for depth

**Orb States:**
| State | Ring + Glow | Icon |
|---|---|---|
| Idle | Cream/Charcoal (per mode), no glow | ● (shutter dot) |
| Listening | Coral, coral glow | 🎤 |
| Processing | Mango, mango glow | ◐ (spinner) |
| Decided | Sage, pulsing sage glow | ✓ |

**Interaction:** Single tap = activate AI (idle) or capture (decided). Long press = voice input.
> Revised in Phase 3a: long press becomes auto-enhance and voice moves to a visible mic button beside the Orb. Double tap is removed.

**Mirror Bar:**
- Frosted glass pill: charcoal @ 55%, 14dp blur, 1dp cream @ 10% border
- Portrait: floats 10dp above Orb
- Landscape: floats inside viewfinder, anchored to left edge of control strip
- Appears: slide 8dp + fade in (250ms ease-out). Disappears: fade out (150ms)
- Text: Quicksand Medium 16sp, soft cream, ≤5 words
- **Only location for text instructions** — viewfinder shows spatial guides only, never text

**Instruction Placement (Option B — context-dependent):**
| Instruction Type | Mirror Bar | Viewfinder |
|---|---|---|
| Positioning ("step left") | Shows text | Directional arrow only |
| Camera settings ("brightening") | Brief text | Nothing — auto-applied (L4), green flash |
| Composition | Shows text | Scene-appropriate guides |
| Planning ("adjusting three things") | Shows text | Overlays appear per step |

**Scene-Aware Composition Overlays:**
Auto-detected by AI with manual override available.

| Scene Type | Detection | Overlays |
|---|---|---|
| Single portrait | 1 face | Face circle, rule-of-thirds, directional arrow |
| Group portrait | 2+ faces | Multiple face circles, spacing guide, group frame |
| Scenery / landscape | No faces, wide | Rule-of-thirds, horizon level, golden ratio |
| Architecture | Straight lines | Vertical/horizontal alignment, symmetry line |
| Close-up / detail | Small subject | Center focus ring, macro frame |
| Action / moving | Subject motion | Lead space indicator |

**Replaces:** Current 3-button `CaptureBar` (shutter/mic/enhance), text-based coaching phases.

### 5. Landing & Onboarding

**Philosophy:** Near-zero onboarding. The Orb teaches itself through interaction.

**Flow:**
1. Open app → Gaussian blur over live camera feed, "Tap to Start" (Quicksand displayLarge, mango accent)
2. Tap → Android system permission dialogs (camera + mic)
3. Permissions granted → Straight into camera. Orb shows a subtle first-use hint (e.g. gentle mango pulse + "tap me" in mirror bar, once)
4. That's it — no steps, no slides, no tutorial

**API Key:** Not user-facing. Bundled/backend-provided. Users pay for the service, we provide the tokens. The entire API key setup flow is removed.

**Style Profile:**
- Optional. Lives in settings, not onboarding
- Aesthetic preferences (gothic, vintage, minimalist, moody, bright & airy) — not technical photo parameters
- Passed as system prompt context to the VLM when set
- Mentioned in the "how to use" guide (also in settings) but never forced

**How-to Guide:**
- Accessible from landing screen (subtle "?" or "How it works" link on the blur screen) AND in settings
- Explains Orb interactions, voice commands, composition guidance
- For users who want to learn more, not a gate to using the app

**Orientation Lock:**
- When AI is active (listening/processing/decided), orientation is locked to whatever it was when the session started
- Prevents spatial confusion ("step left" stays consistent)
- Unlocks when AI returns to idle

**Replaces:** Current multi-step onboarding with API key setup, developer jargon, LLM contract explanation.

### 6. Camera Chrome & Overlays

**Total buttons on screen: 4** — Flash, Flip, Orb, Settings. Nothing else persistent.
> Revised in Phase 3a: flash moves out (voice controls it) and a mic button takes its place, keeping the count at four.

**Portrait layout:**
- Top-left: Flash + Flip (frosted glass circle backing, 44dp, 10dp gap)
- Top-right: Settings (frosted glass circle backing, 44dp)
- Bottom-center: Mirror bar (when active) + Orb (72dp)

**Landscape layout:**
- Right-side control strip (72dp wide, frosted dark backing):
  - Flash, Flip, separator, Orb (56dp), separator, Settings — stacked vertically
- Mirror bar: centered in viewfinder when active
- Viewfinder completely clean except for composition overlays

**Frosted glass backing on all icons:** Charcoal @ 45% + 10dp blur + 1dp cream @ 8% border. Consistent with mirror bar treatment.

**What's NOT on-screen:**
- No live indicator text (AI state shown via Orb color)
- No transcript overlay (mirror bar replaces it)
- No coaching phase labels (Orb gradient replaces them)
- No decision card clutter (simplified single visual treatment)
- Guide/help: landing screen only, then in settings

### Design Review Fixes (from skill audit)

**Contrast rules:**
- Mango `#FFB347` and Coral `#E8887A` are accent/indicator colors ONLY — never used as text on charcoal (both fail 4.5:1)
- All text uses Soft Cream on Charcoal (dark mode) or Charcoal on Soft Cream (light mode) — both pass 4.5:1
- Sage `#8FBF9C` used for overlay lines/shapes, not text

**Reduced motion (`prefers-reduced-motion`):**
- Default: full Jarvis gradient animation, glow pulse, slide-in mirror bar
- Reduced motion ON: static color states (no lerp), instant mirror bar appear, no pulse
- 99% of users see full experience. Fallback for users who explicitly opted into reduced motion.

**Orb pulse budget:**
- On reaching decided state: 3 gentle pulses, then settles to static sage glow
- New instruction / state change: another 3-pulse burst
- Avoids continuous GPU draw while keeping the vibe at key moments

**Long-press discoverability:**
- Idle Orb shows a subtle mic icon inside the ring (Material Rounded `Mic`)
- First-use: mirror bar shows "hold to talk" once, then never again

**Focus rings:**
- All frosted glass buttons get 2dp mango focus ring on keyboard/D-pad focus
- Uses Compose `Modifier.focusable()` + `onFocusChanged` with custom border

**labelSmall bump:**
- 12sp → 14sp to maintain elderly-friendly minimum across all tokens

### 7. Coaching & Decision Cards

**Visible states: 3** (down from 9 coaching phases)

| Visible State | Orb | Mirror Bar | Overlays | Card |
|---|---|---|---|---|
| **Idle** | Neutral (cream/charcoal) | Hidden | None | None |
| **Working** | Gradient coral→mango | Status text ("analyzing scene", "listening…") | None yet | None |
| **Action** | Sage glow (3 pulses) | Instruction (≤5 words) | Scene-appropriate guides | Only if confirmation needed |

**Internal phase mapping:**
| Internal Phase | Visible State |
|---|---|
| IDLE | Idle |
| LISTENING | Working (coral end) |
| INTERPRETING | Working (coral→mango) |
| REQUESTING_VISUAL_INTERPRETATION | Working (mango) |
| RECOMMENDATION | Action |
| APPLYING | Action (green flash for L4 auto-adjustments) |
| GUIDING | Action (overlays active) |
| VERIFYING | Working (brief, re-checking) |
| TRANSIENT_ERROR | Working (coral, mirror bar shows error text) |

**Decision cards — single visual treatment:**
- Only appears when the AI needs explicit user confirmation (e.g. multi-step plan)
- Frosted glass card (same treatment as mirror bar: charcoal @ 55%, 14dp blur)
- One headline (what the AI wants to do), one action row (confirm / dismiss)
- Voice confirmation also accepted ("yes" / "no")
- No separate card types — one design for everything

**What's removed:**
- No visible phase labels or progress indicators
- No text-heavy explanation cards
- No multiple card types competing for attention
- The Orb gradient IS the progress indicator

### 8. Settings & Guide

**Settings screen (grouped):**

| Group | Items |
|---|---|
| **Camera** | Auto-capture on/off |
| **Interaction** | Voice on/off, Button size (S/M/L), Text size (adjustable) |
| **Appearance** | Light/dark mode |
| **Style** | Style Profile (optional — "gothic", "vintage", "bright & airy", etc.) |
| **Help** | How-to guide, About, Feedback |

**What's removed:**
- API key setup (backend-provided)
- LLM contract / model info
- Capability dump
- 120-line guide sheet

**How-to guide:** 3-4 visual cards, each showing one Orb interaction with a short label. Not a wall of text.

### 9. Motion & Animation

**Animation inventory:**
| Element | Animation | Duration | Reduced Motion |
|---|---|---|---|
| Orb gradient | Smooth color lerp (coral→mango→sage) | Tied to AI confidence, ~2-5s | Instant color snap |
| Orb glow | 3 pulses on state arrival, then static | 2s per pulse | Static glow, no pulse |
| Mirror bar appear | Slide 8dp up + fade in | 250ms ease-out | Instant appear |
| Mirror bar dismiss | Fade out | 150ms ease-in | Instant disappear |
| Green flash (L4 auto-adjust) | Brief sage glow burst on Orb | 400ms | Static sage color |
| Composition overlays | Fade in | 350ms ease-out | Instant appear |
| Landing CTA pulse | Mango ring pulse | 2.5s loop | Static ring, no pulse |
| Frosted buttons hover/press | Opacity shift | 200ms | Same (not motion) |

**Rules:**
- All animations use `Animatable<>` / `animate*AsState` Compose APIs — no manual frame logic
- Animation logic lives in UI layer, never in ViewModel
- `prefers-reduced-motion` checked via `LocalReducedMotion` — all animations respect it
- Exit animations ~60-70% of enter duration
- Maximum 2 animated elements per view at once

### 10. Accessibility

**Already strong (7.5 → 8.5):**
- Live regions, semantic descriptions, traversal ordering — all kept from current codebase
- 48dp+ touch targets on all elements (Orb is 72dp)

**Fixes in this redesign:**
| Fix | Detail |
|---|---|
| Contrast | Mango/coral never as text — accent/indicator only. Text is always cream-on-charcoal or charcoal-on-cream (both pass 4.5:1) |
| Focus rings | 2dp mango ring on all frosted buttons via `Modifier.focusable()` + custom border |
| Reduced motion | Full fallback: static colors, instant transitions, no pulse |
| Long-press discovery | Mic icon inside idle Orb + one-time "hold to talk" hint in mirror bar |
| Min text size | 14sp minimum across all tokens (labelSmall bumped from 12sp) |
| Touch spacing | 10dp gap between top-bar icons (up from 4dp) |
| Color-only info | Orb states use color + icon (mic/spinner/checkmark), never color alone |

### 11. Architecture Refactor

**File split plan:**

| New File | Extracted From | Contents |
|---|---|---|
| `HelperOrb.kt` | `CaptureBar` | Orb ring, glow, gradient, tap/long-press |
| `MirrorBar.kt` | New | Frosted pill, slide animation, text |
| `CompositionOverlays.kt` | New | Grid, face circles, arrows, scene-type logic |
| `SettingsSheet.kt` | `CaptureScreen` | Settings groups, Style Profile |
| `CoachingCard.kt` | `CoachingControls` + `DecisionCard` | Single decision card treatment |
| `CaptureReview.kt` | `CaptureScreen` | Photo review + save/retake |
| `LandingScreen.kt` | `Onboarding` | Gaussian blur, CTA, "How it works" |
| `CameraChrome.kt` | `PreviewPane` | Top bar icons, flash/flip/settings |
| `PhotoHelperTheme.kt` | Existing (rewrite) | Full token system, Quicksand typography, light+dark |

**CaptureScreenActions interface:**
```kotlin
interface CaptureScreenActions {
    fun onActivateOrb()
    fun onCapture()
    fun onStartVoice()
    fun onStopVoice()
    fun onConfirmAction()
    fun onDismissAction()
    fun onUndo()
    fun onToggleFlash()
    fun onFlipCamera()
    fun onOpenSettings()
    fun onFocusAt(x: Float, y: Float)
}
```
Replaces 33 individual callback parameters.

**Other changes:**
- `collectAsState()` → `collectAsStateWithLifecycle()` in MainActivity
- Confidence float (0f-1f) exposed from ViewModel for Orb gradient
- `VisibleCoachingState` enum (IDLE, WORKING, ACTION) maps from internal 9 phases
- `@Preview` composables added for each extracted file

### 12. Smart Features (ARCore) — Phase 3

**ARCore integration:**
- Dependency: `com.google.ar:core:1.40+`
- Tracks: device tilt, height relative to floor, stillness (zero movement)
- Runs locally at 60fps, $0 cost

**Smart Auto-Capture:**
- Trigger: (AI confirms good framing via sage state) + (ARCore confirms phone stillness for 500ms)
- Audio cue plays on capture (no haptics — destabilizes phone)
- User can disable auto-capture in settings

**Audio cue system:**
- Subtle camera click sound on capture
- Soft chime when AI reaches decided state
- No audio during working state (would be annoying)

**Deferred — not blocking Phase 1 or 2.**

---

## Detailed Skill Findings

### frontend-design (Anthropic) — Aesthetic Direction

**What's missing:**
- "Typography carries the personality of the page" — the app uses `Typography()` with zero customization. No display face, no intentional pairing, no character. The type scale is raw Material 3 defaults with manual `fontWeight = FontWeight.SemiBold` sprinkled ad-hoc across 7+ text style combinations in the guide sheet alone.
- "The single unique element this page will be remembered by" — there is none. No brand mark, no visual signature, no memorable design element. The Helper Orb addresses this.
- "Spend your boldness in one place; keep everything around it quiet" — currently nothing is bold and nothing is quiet. The capture bar, overlays, coaching cards, and preview chrome all compete for attention equally.
- "Structure is information" — the guide sheet uses flat text for everything. Topics like "Brightness", "Focus", "Zoom" have no visual differentiation from their descriptions.
- "Words are design material" — onboarding step 2 leads with "Photo Helper is designed to use an image-capable LLM. Its prompts and strict response contracts are tuned for Qwen3.7 Flash..." This is developer documentation, not user-facing copy. Should describe outcomes, not implementation.

**What's good:**
- The app already has a real subject (camera control via voice) and a clear single job. The `frontend-design` brief is naturally satisfied at the concept level.
- Active voice is used well in controls: "Take photo", "Apply", "Reset", "Cancel".

### web-design-guidelines (Vercel) — 100+ UI Best Practices

**Violations found:**

| # | Category | Issue | Severity | Location |
|---|---|---|---|---|
| 7 | Animation | Excessive motion — coaching has 9 visible phases, each with its own animation/transition | High | `CoachingProgress`, `AnimatedContent` blocks |
| 9 | Animation | No reduced-motion check — `AnimatedContent`, `AnimatedVisibility`, `Animatable` used without `prefers-reduced-motion` | High | Throughout `CaptureScreen.kt` |
| 11 | Animation | Hover vs. Tap — `pointerInput/detectTapGestures` is correct for mobile, but the focus target has a tap handler inside a `Canvas` which has no touch feedback | High | `FocusTarget` composable |
| 22 | Touch | Touch target sizing — most buttons have `heightIn(min = 48.dp)` which is correct for Android. However, `OverlayIconAction` is `size(48.dp)` which is borderline when the visual hit area (the circle) is smaller than the touch area | Medium | `OverlayIconAction` |
| 23 | Touch | Touch spacing — the top-bar row packs flash, flip, guide, and settings icons with only `spacedBy(4.dp)` gap, well below the 8dp minimum | Medium | `PreviewPane` top row |
| 28 | Interaction | Focus states — no visible keyboard/D-pad focus indicators on custom composables. Material buttons have them, but `Canvas`-based targets (focus reticle) and `Surface`-based buttons (overlay actions) don't show focus rings | High | `OverlayIconAction`, `FocusTarget` |
| 30 | Interaction | Active states — no press/active feedback on overlay buttons. `OverlayIconAction` wraps a `TextButton` in a `Surface` but the surface swallows the ripple | Medium | `OverlayIconAction` |
| 33 | Interaction | Error feedback — `TransientMessage` uses color alone (red/green surface) without an icon for error vs. success. The text prefix `✓` / `Warning:` partially addresses this but only in text content | Medium | `TransientMessage` |
| 36 | Accessibility | Color contrast — `Color.White` text on `Color.Black.copy(alpha = 0.42f)` in `LiveIndicator` and `TranscriptOverlay` is ~2.6:1, well below 4.5:1 minimum | High | `LiveIndicator`, `TranscriptOverlay` |
| 36 | Accessibility | Color contrast — amber warning `Color(0xFFFFDDB0)` on `surfaceVariant` (`0xFF282C30`) is ~4.2:1, borderline | Medium | `RecommendationContent` |
| 37 | Accessibility | Color-only information — the API key banner uses `Color(0xFFFFD54F)` yellow surface as the sole indicator that setup is needed. No icon accompanies it | Medium | `PreviewPane` key banner |
| 40 | Accessibility | ARIA/semantics — `OverlayIconAction` puts the semantic description on the inner `TextButton` via `Modifier.semantics`, but the outer `Surface` is also clickable-looking, creating a confusing accessibility tree | Low | `OverlayIconAction` |

**What's good:**
- Touch targets are mostly 48dp+ (Android standard)
- `liveRegion` usage is extensive and correct
- `contentDescription` on all interactive elements
- `heading()` semantics on section titles
- `traversalIndex` for logical screen reader ordering

### ui-ux-pro-max — Jetpack Compose Guidelines (52 rules)

**Violations by severity:**

**High severity:**
| # | Rule | Issue |
|---|---|---|
| 2 | Small composables | `CaptureScreen.kt` is 2076 lines. `CaptureContent` alone is ~100 lines of parameter passing. `PreviewPane` is another ~140 lines. Single-responsibility principle violated. |
| 15 | Lifecycle-aware collect | `collectAsState()` used in `MainActivity.kt:93` — should be `collectAsStateWithLifecycle()` to stop collection when the app is backgrounded |
| 22 | Design system / centralized theme | 12+ hardcoded hex colors bypass `MaterialTheme.colorScheme`. Overlay alphas (0.42, 0.58, 0.68, 0.94) are magic numbers. |
| 25 | Deep layout nesting | `CaptureContent` → `Box` → `Box` → `Box` → `PreviewPane` → `Box` → `Row` — 5+ levels deep in places |
| 29 | Nested scroll containers | `CoachingControls` sits inside a `Column(verticalScroll)` which is inside another height-constrained `Column(heightIn(max=300))` with its own `verticalScroll` — nested scrollable |
| 33 | Extract reusable patterns | `ScrimLabel`, `OverlayAction`, `OverlayIconAction` are good extractions but `GuideTopic` pattern is repeated, settings toggle pattern is manual, and card patterns are not shared |

**Medium severity:**
| # | Rule | Issue |
|---|---|---|
| 23 | Dark mode support | Dark-only. No light mode defined. Theme-based color switching not possible. |
| 27 | Box misuse | Multiple `Box` composables used as layout containers where `Column` or direct `Modifier` would suffice (e.g., wrapping single children) |
| 30 | fillMaxSize overuse | `fillMaxSize()` applied to 8+ nested containers — preview, overlay layers, focus area all fill max, relying on Z-stacking rather than explicit constraints |
| 34 | Hardcoded text styles | `fontWeight = FontWeight.SemiBold` applied manually ~15 times instead of defining named typography styles |
| 37 | No previews | Zero `@Preview` composables — makes iteration and visual testing impossible without running on device |

**What's good (passes):**
| # | Rule | Status |
|---|---|---|
| 1 | Pure UI composables | `CaptureScreen` only renders UI, delegates to ViewModel via callbacks |
| 3 | Stateless by default | State hoisted to `CaptureUiState`, composables are stateless |
| 4 | Single source of truth | `CaptureUiState` from `CaptureViewModel` |
| 9 | LaunchedEffect keys | Correct key usage throughout |
| 10 | rememberUpdatedState | Used correctly in `MainActivity` for `latestState`, `latestCanFlipCamera` |
| 11 | DisposableEffect cleanup | Proper `onDispose` for camera binding, display listener, lifecycle observer |
| 12 | Unidirectional data flow | Clean event → VM → state → UI cycle |
| 13 | No business logic in UI | All logic in `CaptureViewModel` |
| 36 | testTag usage | Comprehensive `CaptureTestTags` object with tags on all key composables |
| 40-41 | Accessibility semantics | Extensive and correct usage |
| 42 | Compose animation APIs | `AnimatedVisibility`, `AnimatedContent`, `Animatable` used properly |
| 49 | AndroidView interop | `CameraPreview` correctly uses `AndroidView` with `DisposableEffect` for lifecycle |

### ui-ux-pro-max — Style Recommendation

Based on the app's nature (camera, voice-first, minimal chrome), the best-fit styles from the 79-style catalog:

1. **Zero Interface (#18)** — "Minimal visible UI, voice-first, gesture-based, AI-driven, invisible controls, predictive, context-aware, ambient." Perfect match for the vision. Score: highest relevance.
2. **Glassmorphism (#3)** — "Frosted glass, transparent, blurred background, layered." Good for overlays on camera feed. Compose has `Modifier.blur()` for this.
3. **Dark Mode OLED (#7)** — "Deep black, high contrast, eye-friendly, OLED." Natural fit since camera apps are inherently dark. The current theme already uses `0xFF111315` surface.
4. **Micro-interactions (#16)** — "Small animations, gesture-based, tactile feedback." Needed for the Helper Orb state transitions and overall polish.

### brand-guidelines

Brand identity defined for Photo Helper:
- [x] App icon / logo concept — Jarvis gradient ring (Coral→Mango→Sage), the Orb itself as the icon
- [x] Primary brand color — Mango `#FFB347` (warm, friendly, approachable)
- [x] Decide: warm — Mango-led palette with Charcoal/SoftCream interchange
- [x] Define the Helper Orb as the brand signature element — it IS the brand mark

### canvas-design (Anthropic)

Available for mockup generation. Not yet used. Can create:
- Helper Orb state visualizations
- Landing screen mockup
- Onboarding flow mockup
- Before/after comparison of current vs. redesigned UI

## Design Skills Available
- `frontend-design` — aesthetic direction, typography, spacing, motion
- `canvas-design` — mockups and visual exploration
- `brand-guidelines` — consistent visual identity
- `web-design-guidelines` — 100+ a11y, animation, dark mode, touch rules
- `ui-ux-pro-max` — 192 palettes, 74 font pairings, Jetpack Compose stack data
