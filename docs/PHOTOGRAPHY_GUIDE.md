# Photography Guide — In-App Curriculum

> A structured, progressive photography course delivered through the app's book
> icon. Each module is a swipeable lesson card. Lessons tagged **[ACTIVE]** mean
> the app can detect or coach in real time; **[PASSIVE]** means it is educational
> text only.

---

## Module 1: Getting Started — Know Your Camera

**Goal:** Build confidence by understanding what the phone camera can and cannot
do, and establish good habits before the first shot.

### Lesson 1.1: The Zoom Trap [ACTIVE]

- **Digital zoom** pinches and crops pixels — the image gets blurry and grainy.
- **Optical zoom** uses the physical lens (0.5x, 1x, 2x, 3x buttons) — no
  quality loss.
- **Best practice:** Move your feet closer instead of pinching to zoom.

**App assist:** When the user voice-commands "zoom in", the coach can warn if
the zoom level exceeds the optical range and suggest stepping closer instead.

### Lesson 1.2: Clean Your Lens [PASSIVE]

- Phones live in pockets and bags — fingerprints and lint cause foggy, low-
  contrast photos.
- **Habit:** Wipe the lens with a soft microfiber cloth before every session.
- **Caution:** Avoid paper towels or rough fabric — they can scratch the coating.

**App assist:** A gentle reminder card shown once at the start of each session:
"Have you wiped your lens?"

### Lesson 1.3: Light Is Everything [ACTIVE]

- The built-in flash creates harsh, flat light, washed-out faces, and red-eye.
  Avoid it for portraits.
- **Seek natural light:** Position subjects near windows or move outdoors.
- **Golden hour:** The period just after sunrise or before sunset gives warm,
  soft, flattering light.

**App assist:** The exposure analysis already detects dark scenes. The coach can
suggest "Try moving closer to a window" or "Turn to face the light source"
instead of defaulting to flash.

---

## Module 2: Steady Hands — Beating the Blur

**Goal:** Teach physical stabilization techniques so every photo is sharp, even
for users with arthritis or tremors.

### Lesson 2.1: The Stable Triangle [PASSIVE]

- Do **not** hold the phone at arm's length.
- Grip with **both hands**, tuck **elbows tight against your ribs**.
- This creates a stable triangle anchored to your core.

**App assist:** Illustrated diagram showing the triangle posture.

### Lesson 2.2: Use Your Surroundings [PASSIVE]

- **Lean** your back or shoulders against a wall.
- **Rest** elbows on a table, bench, or railing.
- **Prop** the phone on a mug, water bottle, or bag if no surface is available.

### Lesson 2.3: Gentle Shutter Techniques [ACTIVE]

- "Jabbing" the screen shakes the camera at the worst moment.
- **Volume button:** Roll your finger over the side button — much gentler than a
  screen tap.
- **Self-timer (2–3 seconds):** Press the button, then use those seconds to
  steady your grip before the shutter fires.
- **Breathing:** Exhale slowly and press the shutter at the bottom of the breath.

**App assist:** The app already has **Zero-Shake auto-capture** via ARCore
accelerometer data. The coach can say "Hold still… capturing now" when stillness
is detected. The guide teaches *why* this feature exists.

---

## Module 3: Focus & Exposure — Take Control

**Goal:** Move beyond "point and shoot" by teaching deliberate focus and
brightness control.

### Lesson 3.1: Tap to Focus [ACTIVE]

- Do not rely on autofocus alone — tap directly on the subject's face or the
  main point of interest.
- A yellow/white box appears confirming focus lock.

**App assist:** Voice command "focus on the person in red" triggers Qwen point
grounding to tap-focus on the right spot. The guide teaches the manual version.

### Lesson 3.2: Exposure Slider [ACTIVE]

- After tapping to focus, a **sun slider** (brightness bar) appears.
- Drag **up** to brighten, **down** to darken.
- Use this to rescue backlit subjects or tone down harsh highlights.

**App assist:** Voice commands "make it brighter" / "make it darker" already
adjust EV compensation. The guide teaches the manual equivalent.

### Lesson 3.3: Focus Lock [PASSIVE]

- For moving subjects, **tap and hold** the screen to lock both focus and
  exposure.
- The camera will not re-hunt even if the subject shifts slightly.

### Lesson 3.4: HDR — Balancing Bright and Dark [PASSIVE]

- **HDR (High Dynamic Range)** merges multiple exposures into one balanced
  image.
- Set it to **Auto** — the phone activates it when the scene has high contrast
  (e.g., dark landscape under bright sky).
- It prevents "blown-out" skies and "crushed" shadows.

---

## Module 4: Composition — Making Photos Look Intentional

**Goal:** Teach the visual principles that separate a snapshot from a photograph.

### Lesson 4.1: Rule of Thirds [ACTIVE]

- Enable the **3x3 gridlines** in your camera settings.
- Place your subject along the grid lines or at the **four intersection points**
  — not dead center.
- This creates balance and visual interest.

| Phone    | Path to Gridlines                                        |
| -------- | -------------------------------------------------------- |
| Samsung  | Camera → Settings (gear) → Toggle "Grid lines" ON       |
| Pixel    | Camera → Settings → Composition → Grid type: 3x3        |
| OnePlus  | Camera → Settings → Grid and guides → Grid              |

**App assist:** The composition overlay can show rule-of-thirds guides. ARCore
spatial data can detect if the subject is centered and suggest off-center
placement.

### Lesson 4.2: Clean Your Frame [ACTIVE]

- Before shooting, **scan the edges** of the viewfinder.
- Remove "background noise": trash cans, distracting branches, poles "growing"
  out of heads.
- One step to the side often fixes a cluttered background.

**App assist:** The visual analysis can flag cluttered backgrounds and suggest
repositioning.

### Lesson 4.3: Leading Lines [PASSIVE]

- Use natural paths, fences, railings, roads, or shorelines to **guide the
  viewer's eye** toward the subject.
- Leading lines add depth and draw attention to the focal point.

### Lesson 4.4: Foreground Interest [PASSIVE]

- Including an object in the foreground (a flower, a textured rock, a railing)
  adds **depth** and invites the viewer into the scene.
- It transforms a flat image into a layered one.

### Lesson 4.5: Straight Horizons [ACTIVE]

- A crooked horizon is immediately distracting.
- Use the gridlines or the phone's built-in level to keep it straight.

**App assist:** ARCore tilt data from the SpatialTracker can detect a tilted
horizon and nudge the user: "Tilt your phone slightly left to level the horizon."

### Lesson 4.6: The Power of Triangles (Group Photos) [PASSIVE]

- Avoid lining everyone up in a flat row.
- Arrange heads at **different heights** to form a triangle shape — wider at the
  base, one person at the top.
- This creates a sense of unity and visual cohesion.

---

## Module 5: People & Portraits

**Goal:** Teach how to photograph people flatteringly, from solo portraits to
group shots.

### Lesson 5.1: Avoid Wide-Angle Distortion [ACTIVE]

- Shooting too close with the standard (1x) lens exaggerates facial features —
  the "big nose" effect.
- **Use Portrait Mode** or the **2x/3x telephoto lens** from 2–8 feet away.
- The telephoto compresses features and creates professional background blur
  (bokeh).

**App assist:** ML Kit face detection + the existing `FACE_SIZE_AMBIGUOUS` coach
flow already warns about perspective distortion and suggests stepping back. The
guide teaches *why*.

### Lesson 5.2: Flattering Light for Faces [PASSIVE]

- Position the subject facing a window — the soft, even light minimizes shadows
  and wrinkles.
- Avoid overhead midday sun which creates harsh shadows under eyes and nose.
- **DIY reflector:** A piece of white cardboard held below chin level fills in
  shadows.

### Lesson 5.3: Group Photo Management [PASSIVE]

- **Anti-blink trick:** Have everyone close their eyes, then open on the count
  of three.
- **Burst mode:** Hold the shutter to capture rapid frames, then pick the best
  one where everyone is smiling.
- **Direct with confidence:** Give clear, kind instructions — "Everyone look at
  the camera lens, not the screen."

### Lesson 5.4: Step Back for Groups [ACTIVE]

- Groups need more space than solo portraits.
- Ensure everyone fits comfortably in the frame with some breathing room.

**App assist:** ARCore spatial tracking can detect multiple faces and suggest
"Step back a bit to fit everyone in" when the group is being clipped.

---

## Module 6: Challenging Conditions

**Goal:** Handle low light, high contrast, and close-up photography with
confidence.

### Lesson 6.1: Low-Light Photography [ACTIVE]

- **ISO management:** High ISO (above 800) introduces visible grain/noise. Keep
  it low when possible.
- **Night Mode:** Most modern phones have a dedicated night mode that captures
  multiple frames and merges them — use it in dim environments.
- **Exposure compensation:** Tap to focus, then drag the sun slider **down**
  slightly to preserve detail in bright lights against dark backgrounds.

**App assist:** The coach detects underexposed scenes and can suggest enabling
Night Mode or finding better light rather than just boosting EV.

### Lesson 6.2: Backlit Subjects [ACTIVE]

- When the light source is behind the subject, the face goes dark.
- **Fix:** Tap on the subject's face to tell the camera to expose for them, not
  the bright background.
- **Better fix:** Reposition so the light is behind *you*, not behind the
  subject.

**App assist:** Voice command "make it brighter" with visual analysis detecting a
backlit silhouette can trigger targeted EV adjustment.

### Lesson 6.3: Close-Up and Detail Shots [PASSIVE]

- Most phones have a minimum focus distance — getting too close causes blur.
- **Better approach:** Shoot from a comfortable distance and **crop afterward**
  in the gallery — this preserves sharpness.
- Some phones have a dedicated Macro mode — check your camera modes.

---

## Module 7: After the Shot — Editing & Enhancing

**Goal:** Teach simple, impactful edits using built-in tools and the app's AI
editing features.

### Lesson 7.1: The Key Adjustments [ACTIVE]

- **Brightness/Exposure:** Lift if the photo is too dark; pull back if
  washed out.
- **Shadows:** Reveals detail hidden in dark areas without over-brightening the
  whole image.
- **Warmth/Temperature:** If the photo looks too blue/cold, shift toward warm
  (orange) for natural skin tones and comfort.
- **Saturation:** A small boost makes colors pop; too much looks unnatural.

**App assist:** Voice-driven post-processing presets — "make it warmer", "bring
out the shadows" — apply ColorMatrix adjustments to the captured photo.

### Lesson 7.2: AI-Powered Editing [ACTIVE]

- Select a photo from the gallery.
- Describe what you want: "Make the sky bluer", "Remove the trash can in the
  background", "Make me look thinner."
- The app sends the photo + your description to an AI model.
- **Confirmation popup** before any AI modification.
- The original photo is **never overwritten** — each edit creates a new version.
- **Multi-round iteration:** "A bit more", "Undo that", "Now make it warmer."

**App assist:** This is the core AI editing feature — voice-driven, iterative,
with version history.

### Lesson 7.3: Object Removal [PASSIVE]

- Modern phones include built-in erasers for removing background distractions.

| Tool           | Device  |
| -------------- | ------- |
| Magic Eraser   | Pixel   |
| Clean Up       | iPhone  |
| Object Eraser  | Samsung |

- Tap or circle the distraction and it disappears.

---

## Module 8: Sharing & Preserving Memories

**Goal:** Complete the journey from capture to connection — send photos to
family, share on social media, and preserve the legacy.

### Lesson 8.1: Sending to Family [ACTIVE]

- Select one or multiple photos from the gallery.
- Choose a contact or app (WhatsApp, WeChat, email).
- The app handles the share intent — no need to leave and re-find photos.

**App assist:** Voice command "Send this to my daughter on WhatsApp" opens the
share flow with the selected photo(s) pre-attached.

### Lesson 8.2: Captions That Tell the Story [ACTIVE]

- A good caption adds context and emotional depth.
- **Short caption:** One line — what's happening and why it matters.
- **Long caption:** A paragraph — the backstory, the feeling, the memory.

**App assist:** AI caption generation from the photo. Voice command "Write a
caption for this" generates options. "Make it shorter" / "Make it longer" for
iteration.

### Lesson 8.3: Cloud Backups [PASSIVE]

- Set up **auto-backup** on Google Photos or Apple Photos.
- **Safety:** Protects memories if the phone is lost or damaged.
- **Storage:** Generate private sharing links for family — no need to send large
  files that clog up everyone's phone.

### Lesson 8.4: The Shooting Journal [PASSIVE]

- For meaningful photos, write a **two-sentence backstory**: where it was taken
  and how you felt.
- This preserves the "why" behind the photo for future generations.
- The app can store these captions alongside the photo metadata.

---

## Module 9: Building a Practice

**Goal:** Establish ongoing habits that keep photography skills growing.

### Lesson 9.1: Photo-a-Day [PASSIVE]

- Commit to one intentional photo every day.
- It doesn't have to be perfect — it's about staying observant and engaged.
- Over time, the improvement is visible and motivating.

### Lesson 9.2: The Delete Button [PASSIVE]

- Do not hoard blurry shots or duplicates.
- **Ruthlessly cull:** Keep the best 2–3 from each session and delete the rest.
- Great photos stand out more when they aren't buried in clutter.

### Lesson 9.3: The Capstone — Your Memory Book [PASSIVE]

- Curate your best photos into a printed memory book or digital album.
- For each selected photo, write a personal **two-sentence backstory**.
- Share it with grandchildren, friends, or the class — photography is a bridge
  to others.

---

## Lesson Map — What the App Actively Coaches

| Lesson | Feature | App Component |
| ------ | ------- | ------------- |
| 1.1 Zoom Trap | Optical zoom warning | Coach engine |
| 1.3 Light | "Find better light" suggestion | Visual analysis |
| 2.3 Shutter | Zero-Shake auto-capture | ARCore + SpatialTracker |
| 3.1 Tap to Focus | Voice-driven point focus | Qwen point grounding |
| 3.2 Exposure | Voice brightness control | EV compensation |
| 4.1 Rule of Thirds | Composition overlay | UI overlay |
| 4.2 Clean Frame | Background clutter warning | Visual analysis |
| 4.5 Straight Horizons | Tilt detection | ARCore tilt data |
| 5.1 Distortion | "Step back" advisory | ML Kit + coach |
| 5.4 Group Space | "Step back for group" | ARCore + face detection |
| 6.1 Low Light | Night mode suggestion | Exposure analysis |
| 6.2 Backlit | Targeted EV adjustment | Visual analysis |
| 7.1 Key Adjustments | Post-processing presets | ColorMatrix pipeline |
| 7.2 AI Editing | Voice-driven AI edit | LLM image editing |
| 8.1 Send to Family | Share intent flow | Android share API |
| 8.2 Captions | AI caption generation | Qwen text model |
