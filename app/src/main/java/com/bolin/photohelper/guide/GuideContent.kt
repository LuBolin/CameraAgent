package com.bolin.photohelper.guide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class LessonTag { ACTIVE, PASSIVE }

enum class ModuleStatus { COMPLETE, IN_PROGRESS, LOCKED, COMING_SOON }

data class Tip(
    val title: String,
    val body: String,
    val isAppAssist: Boolean = false,
)

data class GuideLesson(
    val id: String,
    val title: String,
    val tag: LessonTag,
    val description: String,
    val tips: List<Tip>,
    val exercise: GuidedExercise? = null,
)

data class GuideModule(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val lessons: List<GuideLesson>,
    val comingSoon: Boolean = false,
)

val GUIDE_MODULES: List<GuideModule> = listOf(
    // ── Module 1: Getting Started ──────────────────────────────────
    GuideModule(
        id = "getting_started",
        title = "Getting started",
        icon = Icons.Rounded.Camera,
        lessons = listOf(
            GuideLesson(
                id = "1.1",
                title = "The zoom trap",
                tag = LessonTag.ACTIVE,
                description = "Digital zoom crops and blows up pixels, making photos blurry and grainy. Learn to use optical zoom and your feet instead.",
                tips = listOf(
                    Tip("Optical zoom", "Use the dedicated lens buttons (0.5x, 1x, 2x, 3x) instead of pinching. These use actual glass to get closer without losing quality."),
                    Tip("Zoom with your feet", "Physically moving closer preserves the highest possible image quality. It's the simplest pro tip there is."),
                    Tip("Your app can help", "When you voice-command \"zoom in\", the coach warns if the zoom level exceeds the optical range and suggests stepping closer.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Take a photo by moving closer — no zooming!",
                    hint = "Walk toward your subject instead of pinching to zoom.",
                    successMessage = "Sharp photo without digital zoom!",
                    type = ExerciseType.TAKE_PHOTO,
                ),
            ),
            GuideLesson(
                id = "1.2",
                title = "Clean your lens",
                tag = LessonTag.PASSIVE,
                description = "Phones live in pockets and bags. Fingerprints and lint on the lens cause foggy, low-contrast photos.",
                tips = listOf(
                    Tip("Build the habit", "Wipe the lens with a soft microfiber cloth before every session."),
                    Tip("Be gentle", "Avoid paper towels or rough fabric — they can scratch the lens coating over time."),
                ),
            ),
            GuideLesson(
                id = "1.3",
                title = "Light is everything",
                tag = LessonTag.ACTIVE,
                description = "Smartphones struggle in poor lighting. The built-in flash creates harsh, flat light and washed-out faces.",
                tips = listOf(
                    Tip("Seek natural light", "Position subjects near windows or move outdoors for soft, diffused light."),
                    Tip("Golden hour", "The period just after sunrise or before sunset gives warm, magical light that flatters textures and faces."),
                    Tip("Your app can help", "The exposure analysis detects dark scenes and suggests finding better light instead of defaulting to flash.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Find good natural light and take a photo.",
                    hint = "Move near a window or step outside — no flash!",
                    successMessage = "Great use of natural light!",
                    type = ExerciseType.TAKE_PHOTO,
                ),
            ),
        ),
    ),
    // ── Module 2: Steady Hands ─────────────────────────────────────
    GuideModule(
        id = "steady_hands",
        title = "Steady hands",
        icon = Icons.Rounded.FrontHand,
        lessons = listOf(
            GuideLesson(
                id = "2.1",
                title = "The stable triangle",
                tag = LessonTag.PASSIVE,
                description = "For those managing arthritis or shaky hands, physical stability is the foundation of a sharp photo.",
                tips = listOf(
                    Tip("Both hands", "Grip the phone firmly with both hands."),
                    Tip("Elbows in", "Tuck both elbows tightly against your ribs. This creates a stable triangle anchored to your core."),
                    Tip("Never at arm's length", "Holding the phone far from your body amplifies every small movement."),
                ),
                exercise = GuidedExercise(
                    instruction = "Hold your phone steady with both hands.",
                    hint = "Tuck your elbows against your ribs.",
                    successMessage = "Rock solid! You held perfectly still.",
                    type = ExerciseType.HOLD_STEADY,
                ),
            ),
            GuideLesson(
                id = "2.2",
                title = "Use your surroundings",
                tag = LessonTag.PASSIVE,
                description = "If your hands remain unsteady, use what's around you for support.",
                tips = listOf(
                    Tip("Lean", "Rest your shoulders or back against a sturdy wall."),
                    Tip("Rest", "Place elbows on a table, park bench, or railing."),
                    Tip("Prop", "No surface? Prop the phone on a coffee cup, water bottle, or backpack."),
                ),
                exercise = GuidedExercise(
                    instruction = "Brace against something and hold steady.",
                    hint = "Lean on a wall or rest your elbows on a surface.",
                    successMessage = "Perfectly braced! That's pro technique.",
                    type = ExerciseType.HOLD_STEADY,
                ),
            ),
            GuideLesson(
                id = "2.3",
                title = "Gentle shutter techniques",
                tag = LessonTag.ACTIVE,
                description = "Jabbing the screen shakes the camera at the exact moment the shutter opens.",
                tips = listOf(
                    Tip("Volume button", "Roll your finger over the side volume button to take the photo. Much gentler than tapping the screen."),
                    Tip("Self-timer", "Set a 2–3 second timer. Press, then steady your grip before the shutter fires."),
                    Tip("Breathe", "Exhale slowly and press the shutter at the bottom of the breath for maximum steadiness."),
                    Tip("Your app can help", "Zero-Shake auto-capture detects when you're holding perfectly still and takes the photo automatically. No tap needed.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Hold perfectly still — auto-capture will fire.",
                    hint = "Breathe out slowly, then freeze.",
                    successMessage = "Auto-captured! Zero shake, zero tap.",
                    type = ExerciseType.AUTO_CAPTURE,
                ),
            ),
        ),
    ),
    // ── Module 3: Focus & Exposure ─────────────────────────────────
    GuideModule(
        id = "focus_exposure",
        title = "Focus and exposure",
        icon = Icons.Rounded.Tune,
        lessons = listOf(
            GuideLesson(
                id = "3.1",
                title = "Tap to focus",
                tag = LessonTag.ACTIVE,
                description = "Don't rely on autofocus alone. Take control of what the camera focuses on.",
                tips = listOf(
                    Tip("Tap the subject", "Tap directly on the subject's face or the main point of interest. A box will appear confirming focus lock."),
                    Tip("Your app can help", "Say \"focus on the red mug\" or \"focus on the person in the hat\" and the AI locates and focuses on exactly the right spot.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Tap on any object to focus on it.",
                    hint = "Tap directly on what you want sharp.",
                    successMessage = "Locked on! You chose the focus.",
                    type = ExerciseType.TAP_TO_FOCUS,
                ),
            ),
            GuideLesson(
                id = "3.2",
                title = "Exposure slider",
                tag = LessonTag.ACTIVE,
                description = "After tapping to focus, a brightness bar appears. Drag it to control how bright or dark the photo is.",
                tips = listOf(
                    Tip("Drag up to brighten", "If the subject is too dark, drag the sun slider upward."),
                    Tip("Drag down to darken", "If highlights are washed out, drag it down to recover detail."),
                    Tip("Your app can help", "Say \"make it brighter\" or \"too dark\" and the app adjusts exposure for you.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Say \"make it brighter\" or \"too dark\".",
                    hint = "Tap the microphone, then speak naturally.",
                    successMessage = "Voice-controlled exposure!",
                    type = ExerciseType.VOICE_COMMAND,
                ),
            ),
            GuideLesson(
                id = "3.3",
                title = "Focus lock",
                tag = LessonTag.PASSIVE,
                description = "For moving subjects, lock the focus so the camera doesn't re-hunt.",
                tips = listOf(
                    Tip("Tap and hold", "Long-press the screen on your subject to lock both focus and exposure."),
                    Tip("Look for the indicator", "A \"Lock\" or \"AE/AF Lock\" badge will appear confirming the settings are held."),
                ),
            ),
            GuideLesson(
                id = "3.4",
                title = "HDR — balancing bright and dark",
                tag = LessonTag.PASSIVE,
                description = "HDR merges multiple exposures into one balanced image, rescuing both shadows and highlights.",
                tips = listOf(
                    Tip("Set it to Auto", "The phone activates HDR only when the scene needs it — dark landscape under bright sky, for example."),
                    Tip("When it helps most", "High-contrast scenes: a window behind someone, a sunset landscape, indoor scenes with bright spots."),
                ),
            ),
        ),
    ),
    // ── Module 4: Composition ──────────────────────────────────────
    GuideModule(
        id = "composition",
        title = "Composition",
        icon = Icons.Rounded.GridOn,
        lessons = listOf(
            GuideLesson(
                id = "4.1",
                title = "Rule of thirds",
                tag = LessonTag.ACTIVE,
                description = "Place subjects along grid lines or at intersection points instead of dead center. This creates balance and visual interest.",
                tips = listOf(
                    Tip("Enable gridlines", "Open your camera settings and turn on the 3x3 grid overlay."),
                    Tip("Four magic spots", "The four points where grid lines cross are the strongest positions for your subject."),
                    Tip("Your app can help", "The composition overlay shows rule-of-thirds guides right on the camera preview.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Place your subject on a grid intersection, then shoot.",
                    hint = "Imagine the screen divided into thirds — put the subject where lines cross.",
                    successMessage = "Composed like a pro!",
                    type = ExerciseType.TAKE_PHOTO,
                ),
            ),
            GuideLesson(
                id = "4.2",
                title = "Clean your frame",
                tag = LessonTag.ACTIVE,
                description = "Before shooting, scan the edges of the viewfinder. Remove distracting elements from the background.",
                tips = listOf(
                    Tip("Scan the edges", "Look for trash cans, branches, poles \"growing\" out of heads, or other visual clutter."),
                    Tip("One step fixes it", "Often, a single step to the side or a slight change in angle removes a distracting background element."),
                    Tip("Your app can help", "The visual analysis can flag cluttered backgrounds and suggest repositioning.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Scan the edges of your frame, reposition, then shoot.",
                    hint = "Check all four edges for distractions before pressing the shutter.",
                    successMessage = "Clean frame, clean photo!",
                    type = ExerciseType.TAKE_PHOTO,
                ),
            ),
            GuideLesson(
                id = "4.3",
                title = "Leading lines",
                tag = LessonTag.PASSIVE,
                description = "Use natural paths, fences, railings, or shorelines to guide the viewer's eye toward the subject.",
                tips = listOf(
                    Tip("Look around you", "Roads, hallways, rivers, staircases, and fences all create natural leading lines."),
                    Tip("Point toward your subject", "Position yourself so the lines draw attention to the focal point of your photo."),
                ),
            ),
            GuideLesson(
                id = "4.4",
                title = "Foreground interest",
                tag = LessonTag.PASSIVE,
                description = "Including an object in the foreground adds depth and invites the viewer into the scene.",
                tips = listOf(
                    Tip("Add a layer", "A flower, a textured rock, a railing, or even your hand framing the shot adds dimension."),
                    Tip("Don't block the subject", "The foreground element should complement, not compete with, your main subject."),
                ),
            ),
            GuideLesson(
                id = "4.5",
                title = "Straight horizons",
                tag = LessonTag.ACTIVE,
                description = "A crooked horizon is immediately distracting. Use gridlines or your phone's level to keep it straight.",
                tips = listOf(
                    Tip("Use the gridlines", "The horizontal grid line is your built-in level. Align the horizon to it."),
                    Tip("Your app can help", "The spatial tracker detects phone tilt and nudges you: \"Tilt slightly left to level the horizon.\"", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Level the horizon and take a photo.",
                    hint = "Align any horizontal line with the grid.",
                    successMessage = "Straight and true!",
                    type = ExerciseType.TAKE_PHOTO,
                ),
            ),
            GuideLesson(
                id = "4.6",
                title = "Group photo triangles",
                tag = LessonTag.PASSIVE,
                description = "Avoid lining everyone up in a flat row. Arrange heads at different heights to form a triangle shape.",
                tips = listOf(
                    Tip("Vary the heights", "Have some people sit, others stand, one kneel. Wider at the base, one person at the top."),
                    Tip("Unity and cohesion", "The triangle creates a visual sense of togetherness that a flat line never achieves."),
                ),
            ),
        ),
    ),
    // ── Module 5: People & Portraits ───────────────────────────────
    GuideModule(
        id = "portraits",
        title = "People and portraits",
        icon = Icons.Rounded.Groups,
        lessons = listOf(
            GuideLesson(
                id = "5.1",
                title = "Avoid wide-angle distortion",
                tag = LessonTag.ACTIVE,
                description = "Shooting too close with the standard lens exaggerates facial features — the \"big nose\" effect.",
                tips = listOf(
                    Tip("Use telephoto", "Switch to the 2x or 3x lens and stand 2–8 feet away. This compresses features and looks professional."),
                    Tip("Portrait mode", "It combines telephoto framing with background blur (bokeh) for a flattering look."),
                    Tip("Your app can help", "Face detection warns about perspective distortion and suggests stepping back when you're too close.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Step back, switch to 2x lens, and take a portrait.",
                    hint = "Stand 4–8 feet from the person. Use the telephoto lens.",
                    successMessage = "Flattering portrait — no distortion!",
                    type = ExerciseType.TAKE_PHOTO,
                ),
            ),
            GuideLesson(
                id = "5.2",
                title = "Flattering light for faces",
                tag = LessonTag.PASSIVE,
                description = "Light direction and quality matter more than any filter for making people look good.",
                tips = listOf(
                    Tip("Face the window", "Position the subject facing a window — soft, even light minimizes shadows and wrinkles."),
                    Tip("Avoid midday sun", "Overhead sun creates harsh shadows under eyes and nose. Open shade is better."),
                    Tip("DIY reflector", "A piece of white cardboard held below chin level fills in under-eye shadows."),
                ),
            ),
            GuideLesson(
                id = "5.3",
                title = "Group photo management",
                tag = LessonTag.PASSIVE,
                description = "Managing groups is stressful. A systematic approach keeps everyone smiling.",
                tips = listOf(
                    Tip("Anti-blink trick", "Have everyone close their eyes, then open on the count of three. Far fewer blinks."),
                    Tip("Use burst mode", "Hold the shutter to capture rapid frames, then pick the best one where everyone looks great."),
                    Tip("Direct with confidence", "Give clear, kind instructions — \"Everyone look at the camera lens, not the screen.\""),
                ),
            ),
            GuideLesson(
                id = "5.4",
                title = "Step back for groups",
                tag = LessonTag.ACTIVE,
                description = "Groups need more space than solo portraits. Make sure everyone fits comfortably.",
                tips = listOf(
                    Tip("Room to breathe", "Leave some space around the edges of the group — don't clip anyone."),
                    Tip("Your app can help", "Spatial tracking detects multiple faces and suggests \"Step back a bit to fit everyone in\" when the group is being clipped.", isAppAssist = true),
                ),
            ),
        ),
    ),
    // ── Module 6: Challenging Conditions ───────────────────────────
    GuideModule(
        id = "challenging",
        title = "Challenging conditions",
        icon = Icons.Rounded.NightsStay,
        lessons = listOf(
            GuideLesson(
                id = "6.1",
                title = "Low-light photography",
                tag = LessonTag.ACTIVE,
                description = "Low light challenges every camera. Understanding a few principles keeps photos sharp and noise-free.",
                tips = listOf(
                    Tip("Keep ISO low", "High ISO (above 800) introduces visible grain. Let the phone manage it through Night Mode instead."),
                    Tip("Use Night Mode", "Most modern phones capture multiple frames and merge them — use it in dim environments."),
                    Tip("Your app can help", "The coach detects underexposed scenes and suggests enabling Night Mode or finding better light.", isAppAssist = true),
                ),
            ),
            GuideLesson(
                id = "6.2",
                title = "Backlit subjects",
                tag = LessonTag.ACTIVE,
                description = "When light is behind the subject, their face goes dark. Two fixes.",
                tips = listOf(
                    Tip("Tap the face", "Tap on the subject's face to tell the camera to expose for them, not the bright background."),
                    Tip("Reposition", "Move so the light is behind you, not behind the subject. This is always the better fix."),
                    Tip("Your app can help", "Say \"make it brighter\" and the visual analysis detects the backlit silhouette and adjusts.", isAppAssist = true),
                ),
                exercise = GuidedExercise(
                    instruction = "Tap the subject's face to fix the exposure.",
                    hint = "The camera will brighten the face and darken the background.",
                    successMessage = "Exposure rescued from the backlight!",
                    type = ExerciseType.TAP_TO_FOCUS,
                ),
            ),
            GuideLesson(
                id = "6.3",
                title = "Close-up and detail shots",
                tag = LessonTag.PASSIVE,
                description = "Most phones have a minimum focus distance. Getting too close causes blur.",
                tips = listOf(
                    Tip("Shoot and crop", "Take the photo from a comfortable distance and crop in the gallery afterward — preserves sharpness."),
                    Tip("Check for Macro mode", "Some phones have a dedicated Macro mode in the camera modes menu."),
                ),
            ),
        ),
    ),
    // ── Module 7: Editing & Enhancing (Coming Soon) ────────────────
    GuideModule(
        id = "editing",
        title = "Editing and enhancing",
        icon = Icons.Rounded.PhotoLibrary,
        comingSoon = true,
        lessons = listOf(
            GuideLesson(
                id = "7.1",
                title = "Key adjustments",
                tag = LessonTag.ACTIVE,
                description = "Brightness, shadows, warmth, and saturation — the four sliders that transform a photo.",
                tips = listOf(
                    Tip("Coming soon", "Voice-driven post-processing presets will let you say \"make it warmer\" or \"bring out the shadows\" after the shot."),
                ),
            ),
            GuideLesson(
                id = "7.2",
                title = "AI-powered editing",
                tag = LessonTag.ACTIVE,
                description = "Describe what you want changed. The AI modifies the photo without overwriting the original.",
                tips = listOf(
                    Tip("Coming soon", "Select a photo, describe the change, confirm, and iterate. Multi-round editing with version history."),
                ),
            ),
            GuideLesson(
                id = "7.3",
                title = "Object removal",
                tag = LessonTag.PASSIVE,
                description = "Modern phones include built-in erasers for removing background distractions.",
                tips = listOf(
                    Tip("Pixel: Magic Eraser", "Tap or circle a distraction and it disappears."),
                    Tip("Samsung: Object Eraser", "Found in the gallery's edit tools."),
                    Tip("iPhone: Clean Up", "Available in the Photos app editor."),
                ),
            ),
        ),
    ),
    // ── Module 8: Sharing & Preserving (Coming Soon) ───────────────
    GuideModule(
        id = "sharing",
        title = "Sharing and preserving",
        icon = Icons.AutoMirrored.Rounded.Send,
        comingSoon = true,
        lessons = listOf(
            GuideLesson(
                id = "8.1",
                title = "Sending to family",
                tag = LessonTag.ACTIVE,
                description = "Select photos and send them directly to WhatsApp, WeChat, or email.",
                tips = listOf(
                    Tip("Coming soon", "Voice command \"Send this to my daughter on WhatsApp\" will open the share flow with photos pre-attached."),
                ),
            ),
            GuideLesson(
                id = "8.2",
                title = "Captions that tell the story",
                tag = LessonTag.ACTIVE,
                description = "A good caption adds context and emotional depth to your shared photos.",
                tips = listOf(
                    Tip("Coming soon", "AI caption generation from your photo. Say \"Write a caption\" and choose short or long."),
                ),
            ),
            GuideLesson(
                id = "8.3",
                title = "Cloud backups",
                tag = LessonTag.PASSIVE,
                description = "Set up auto-backup on Google Photos or Apple Photos to protect your memories.",
                tips = listOf(
                    Tip("Safety first", "Auto-backup protects memories if the phone is lost or damaged."),
                    Tip("Share smart", "Generate private sharing links for family — no need to send large files that clog up everyone's phone."),
                ),
            ),
            GuideLesson(
                id = "8.4",
                title = "The shooting journal",
                tag = LessonTag.PASSIVE,
                description = "For meaningful photos, write a two-sentence backstory: where it was taken and how you felt.",
                tips = listOf(
                    Tip("Preserve the why", "The story behind a photo matters more than the settings used to take it."),
                    Tip("For future generations", "A caption transforms a photo from \"nice picture\" to \"this is the day we...\""),
                ),
            ),
        ),
    ),
    // ── Module 9: Building a Practice ──────────────────────────────
    GuideModule(
        id = "practice",
        title = "Building a practice",
        icon = Icons.Rounded.Star,
        lessons = listOf(
            GuideLesson(
                id = "9.1",
                title = "Photo-a-day",
                tag = LessonTag.PASSIVE,
                description = "Commit to one intentional photo every day to stay observant and engaged.",
                tips = listOf(
                    Tip("Consistency over perfection", "It doesn't have to be great — it's about building the habit of seeing."),
                    Tip("Watch the progress", "Over weeks, the improvement is visible and motivating."),
                ),
            ),
            GuideLesson(
                id = "9.2",
                title = "The delete button",
                tag = LessonTag.PASSIVE,
                description = "Don't hoard blurry shots or duplicates. Culling makes great photos stand out.",
                tips = listOf(
                    Tip("Keep the best 2–3", "From each session, pick the standouts and delete the rest."),
                    Tip("Free up space", "Fewer photos means faster scrolling and less storage anxiety."),
                ),
            ),
            GuideLesson(
                id = "9.3",
                title = "Your memory book",
                tag = LessonTag.PASSIVE,
                description = "Curate your best photos into a printed book or digital album. The capstone of your photography journey.",
                tips = listOf(
                    Tip("Two-sentence backstory", "For each selected photo, write where it was taken and how you felt."),
                    Tip("Share it", "Show it to grandchildren, friends, or the class. Photography is a bridge to others."),
                ),
            ),
        ),
    ),
)
