import fs from "node:fs/promises";
import path from "node:path";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const ROOT = "C:\\Users\\bolin\\Desktop\\CameraAgent";
const OUT = path.join(ROOT, "submission-assets");
const RENDERS = path.join(OUT, "pitch-deck-renders");

const ASSETS = {
  cover: path.join(OUT, "photo-helper-cover-16x9-v6-actual-ui.png"),
  listening: path.join(ROOT, "outputs", "workbuddy", "final-device-verification", "final", "04_active_listening.png"),
  focus: path.join(ROOT, "outputs", "workbuddy", "focus-verification.png"),
  restored: path.join(ROOT, "outputs", "workbuddy", "final-device-verification", "final", "07_after_reset.png"),
};

const C = {
  ink: "#111318",
  muted: "#5E6572",
  line: "#DDE2E8",
  paper: "#F7F8FA",
  white: "#FFFFFF",
  blue: "#66BDF1",
  blueDark: "#176A9E",
  bluePale: "#EAF6FD",
  warm: "#E6B15E",
  warmPale: "#FFF4E0",
  green: "#3BAA7A",
  darkPanel: "#1B1E24",
};

const FONT = "Arial";

async function readImageBlob(imagePath) {
  const bytes = await fs.readFile(imagePath);
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}

async function writeBlob(filePath, blob) {
  await fs.writeFile(filePath, new Uint8Array(await blob.arrayBuffer()));
}

function box(slide, { left, top, width, height, fill = C.white, line = C.line, radius = "rounded-xl", shadow = undefined }) {
  return slide.shapes.add({
    geometry: "roundRect",
    position: { left, top, width, height },
    fill,
    line: { style: "solid", fill: line, width: line === "none" ? 0 : 1 },
    borderRadius: radius,
    shadow,
  });
}

function textBox(slide, text, { left, top, width, height, size = 24, color = C.ink, bold = false, align = "left", valign = "top", font = FONT, italic = false, fill = "none", line = "none", margin = 0 }) {
  const s = slide.shapes.add({
    geometry: "textbox",
    position: { left, top, width, height },
    fill,
    line: { style: "solid", fill: line, width: line === "none" ? 0 : 1 },
  });
  s.text = text;
  s.text.style = {
    fontFamily: font,
    fontSize: size,
    color,
    bold,
    italic,
    alignment: align,
    verticalAlignment: valign,
    inset: margin,
  };
  return s;
}

function slideHeader(slide, number, title, kicker) {
  textBox(slide, `0${number}`, { left: 64, top: 42, width: 52, height: 22, size: 13, bold: true, color: C.blueDark });
  textBox(slide, kicker.toUpperCase(), { left: 122, top: 42, width: 420, height: 22, size: 13, bold: true, color: C.muted });
  textBox(slide, title, { left: 64, top: 78, width: 1148, height: 72, size: 42, bold: true, color: C.ink });
}

function footer(slide, text = "PHOTO HELPER · FIVE­CENT") {
  slide.shapes.add({
    geometry: "rect",
    position: { left: 64, top: 682, width: 1152, height: 1 },
    fill: C.line,
    line: { style: "solid", fill: C.line, width: 0 },
  });
  textBox(slide, text, { left: 64, top: 690, width: 500, height: 18, size: 10, bold: true, color: "#8A919E" });
}

function notes(slide, body, sources) {
  slide.speakerNotes.textFrame.setText(`${body}\n\n[Sources]\n${sources.map((s) => `- ${s}`).join("\n")}`);
  slide.speakerNotes.setVisible(true);
}

function circle(slide, x, y, d, fill, line = "none") {
  return slide.shapes.add({
    geometry: "ellipse",
    position: { left: x, top: y, width: d, height: d },
    fill,
    line: { style: "solid", fill: line, width: line === "none" ? 0 : 1 },
  });
}

async function addImage(slide, filePath, position, { fit = "cover", geometry = "rect", radius = undefined, alt = "" } = {}) {
  return slide.images.add({
    blob: await readImageBlob(filePath),
    contentType: "image/png",
    alt,
    fit,
    position,
    geometry,
    ...(radius ? { borderRadius: radius } : {}),
  });
}

async function build() {
  await fs.mkdir(RENDERS, { recursive: true });
  const deck = Presentation.create({ slideSize: { width: 1280, height: 720 } });

  // 1 — Cover. Full-bleed image; restrained attribution overlay only.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.ink;
    await addImage(slide, ASSETS.cover, { left: 0, top: 0, width: 1280, height: 720 }, { fit: "cover", alt: "Photo Helper cover showing a family and the camera interface" });
    box(slide, { left: 748, top: 646, width: 468, height: 42, fill: "#111318CC", line: "none", radius: "rounded-full" });
    textBox(slide, "LIFE AGENT · TEAM FIVECENT · SINGAPORE", { left: 770, top: 657, width: 424, height: 20, size: 12, bold: true, color: C.white, align: "center" });
    notes(slide,
      "0:00–0:25\n\nSay:\nGood afternoon. We built Photo Helper, a voice-first camera-control agent for family photography. People know the outcome they want—a brighter picture, a closer subject or time to join the frame—but not which settings create it. Photo Helper translates that request into a bounded workflow on an Android camera.\n\nPresenter cue: Let the cover image establish the family scenario before advancing.",
      [ASSETS.cover, path.join(ROOT, "submission.md")],
    );
  }

  // 2 — Problem. Two-column contrast inspired by Codex Grid slide 10.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.paper;
    slideHeader(slide, 2, "People describe outcomes. Cameras expose settings.", "The gap");

    box(slide, { left: 64, top: 180, width: 468, height: 444, fill: C.ink, line: "none", radius: "rounded-2xl" });
    textBox(slide, "What people say", { left: 100, top: 214, width: 380, height: 30, size: 18, bold: true, color: C.blue });
    const phrases = [
      ["“It’s too dark.”", 276],
      ["“Focus on that.”", 372],
      ["“Take it in five seconds.”", 468],
    ];
    for (const [phrase, y] of phrases) {
      circle(slide, 100, y + 10, 12, C.warm);
      textBox(slide, phrase, { left: 130, top: y, width: 350, height: 54, size: 28, bold: true, color: C.white, valign: "middle" });
    }

    textBox(slide, "The user should not have to translate that into exposure compensation, metering, focus regions and timers.", { left: 594, top: 214, width: 594, height: 190, size: 35, bold: true, color: C.ink });
    box(slide, { left: 594, top: 446, width: 594, height: 126, fill: C.bluePale, line: "none", radius: "rounded-xl" });
    textBox(slide, "Photo Helper acts as the translator—not the photographer.", { left: 626, top: 478, width: 530, height: 66, size: 25, bold: true, color: C.blueDark, valign: "middle" });
    footer(slide);
    notes(slide,
      "0:25–1:02\n\nSay:\nPeople do not think in exposure compensation, focus coordinates or timer menus. They say, ‘It is too dark,’ ‘Focus on that,’ or ‘Take it in five seconds.’ The challenge is translating those intentions before the moment is lost. This can be harder for less-confident smartphone users, including older adults, although we have not completed research with that group. Photo Helper translates ordinary language into supported camera controls; it is not an autonomous photographer.\n\nPresenter cue: Point first to the natural phrases, then to the translation statement.",
      [path.join(ROOT, "submission.md"), path.join(OUT, "project-brief.md")],
    );
  }

  // 3 — Minimal holding slide while the presenter switches to the demo video.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.ink;
    textBox(slide, "DEMO", { left: 240, top: 286, width: 800, height: 110, size: 64, bold: true, color: C.white, align: "center", valign: "middle" });
    notes(slide,
      "1:02–2:22 — Reserved demo block; no live narration.\n\nPresenter cue:\n1. Advance to this holding slide.\n2. Switch to the video tab.\n3. Play the 75-second stitched demo.\n4. Switch back to the deck after the video ends.\n5. Advance immediately to the architecture slide.\n\nTiming allowance: seventy-five seconds for playback and approximately five seconds total for switching out of and back into the deck.",
      [path.join(OUT, "demo-video-full-script.md")],
    );
  }

  // 4 — Architecture. Connectors are created before nodes.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.paper;
    slideHeader(slide, 4, "The model interprets. Android retains authority.", "Agent design");

    const xs = [64, 362, 660, 958];
    for (const x of [318, 616, 914]) {
      slide.shapes.add({
        geometry: "rightArrow",
        position: { left: x, top: 292, width: 36, height: 42 },
        fill: C.blue,
        line: { style: "solid", fill: C.blue, width: 0 },
      });
    }

    const nodes = [
      { title: "1 · CONTEXT", body: "Voice request\nClean frame\nGridded frame\nCamera telemetry", fill: C.white },
      { title: "2 · QWEN PLAN", body: "Qwen3.7 Flash\nStrict JSON\n≤ 8 ordered actions\nNo free-form controls", fill: C.bluePale },
      { title: "3 · VALIDATE", body: "Allowlist + schema\nCapabilities\nLens / frame / session\nApproval gates", fill: C.white },
      { title: "4 · EXECUTE", body: "CameraX / Camera2\nFocus + settings\nCapture\nRollback / Reset", fill: C.warmPale },
    ];
    nodes.forEach((node, i) => {
      box(slide, { left: xs[i], top: 204, width: 258, height: 252, fill: node.fill, line: C.line, radius: "rounded-xl" });
      textBox(slide, node.title, { left: xs[i] + 24, top: 232, width: 210, height: 28, size: 15, bold: true, color: i === 1 ? C.blueDark : C.muted });
      textBox(slide, node.body, { left: xs[i] + 24, top: 278, width: 210, height: 148, size: 20, bold: true, color: C.ink });
    });

    textBox(slide, "THE ONLY ACTIONS THE MODEL MAY PROPOSE", { left: 64, top: 496, width: 430, height: 22, size: 13, bold: true, color: C.muted });
    const actionLabels = ["ADJUST", "SET CAMERA", "SET FLASH", "FOCUS CELL", "RESET", "CAPTURE"];
    actionLabels.forEach((label, i) => {
      const x = 64 + i * 190;
      box(slide, { left: x, top: 532, width: 172, height: 52, fill: i === 5 ? C.ink : C.white, line: i === 5 ? "none" : C.line, radius: "rounded-full" });
      textBox(slide, label, { left: x + 8, top: 548, width: 156, height: 20, size: 13, bold: true, color: i === 5 ? C.white : C.ink, align: "center" });
    });
    box(slide, { left: 64, top: 610, width: 1152, height: 48, fill: C.ink, line: "none", radius: "rounded-lg" });
    textBox(slide, "Privacy boundary: audio stays on-device; hosted interpretation receives reduced still frames, not a preview stream.", { left: 84, top: 624, width: 1112, height: 22, size: 15, bold: true, color: C.white, align: "center" });
    footer(slide);
    notes(slide,
      "2:22–3:03\n\nSay:\nWhat you saw has four stages. The app supplies the request, a clean frame, a labelled grid and trusted camera telemetry. Qwen returns only strict JSON: at most eight actions from six permitted types. Android validates the schema, capabilities and whether the context is current before applying CameraX or Camera2 controls. Failed setting transactions roll back, and Reset restores the baseline. Voice is transcribed on-device; Alibaba receives reduced still frames for eligible requests, but never microphone audio or a continuous preview stream.\n\nPresenter cue: Trace the four boxes from left to right, then finish on the privacy boundary.",
      [path.join(ROOT, "README.md"), path.join(ROOT, "app", "src", "main", "java", "com", "bolin", "photohelper", "visual", "CommandContracts.kt"), path.join(ROOT, "submission.md")],
    );
  }

  // 5 — Evidence. Metric-led composition inspired by Codex Grid slide 19.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.ink;
    textBox(slide, "05", { left: 64, top: 42, width: 52, height: 22, size: 13, bold: true, color: C.blue });
    textBox(slide, "ENGINEERING EVIDENCE", { left: 122, top: 42, width: 420, height: 22, size: 13, bold: true, color: "#A8B0BD" });
    textBox(slide, "Bounded by design. Verified on hardware.", { left: 64, top: 78, width: 1148, height: 72, size: 42, bold: true, color: C.white });

    const metrics = [
      { x: 64, value: "209", label: "JVM UNIT TESTS", detail: "Core planning, validation and state behavior" },
      { x: 454, value: "57/57", label: "DEVICE TESTS", detail: "Deterministic run on a physical OnePlus" },
      { x: 844, value: "6", label: "ACTION TYPES", detail: "Small allowlist; maximum eight per plan" },
    ];
    metrics.forEach((m, i) => {
      box(slide, { left: m.x, top: 190, width: 352, height: 276, fill: i === 1 ? "#263340" : C.darkPanel, line: "#363B45", radius: "rounded-2xl" });
      textBox(slide, m.value, { left: m.x + 24, top: 220, width: 304, height: 112, size: 64, bold: true, color: i === 2 ? C.warm : C.blue, align: "center", valign: "middle" });
      textBox(slide, m.label, { left: m.x + 24, top: 350, width: 304, height: 26, size: 15, bold: true, color: C.white, align: "center" });
      textBox(slide, m.detail, { left: m.x + 36, top: 394, width: 280, height: 52, size: 16, color: "#C8CED8", align: "center" });
    });

    const claims = [
      ["↺", "Transactional rollback + persistent Reset"],
      ["⌁", "Stale frame, lens, session and capture context rejected"],
      ["●", "Push-to-talk; up to 15 seconds; audio stays local"],
    ];
    claims.forEach((claim, i) => {
      const x = 64 + i * 390;
      textBox(slide, claim[0], { left: x, top: 520, width: 36, height: 34, size: 24, bold: true, color: i === 2 ? C.green : C.blue, align: "center" });
      textBox(slide, claim[1], { left: x + 48, top: 514, width: 300, height: 60, size: 17, bold: true, color: C.white, valign: "middle" });
    });
    box(slide, { left: 64, top: 612, width: 1152, height: 48, fill: "#242830", line: "none", radius: "rounded-lg" });
    textBox(slide, "The evidence supports reliable camera control—not a claim that AI automatically creates better photos.", { left: 86, top: 626, width: 1108, height: 22, size: 15, bold: true, color: "#D8DDE5", align: "center" });
    notes(slide,
      "3:03–3:34\n\nSay:\nReliability is part of the design. The code passes 209 JVM unit tests, Android lint and a clean debug build. WorkBuddy completed 57 out of 57 instrumented tests on a physical OnePlus. They cover voice capture, encrypted key storage, Reset persistence, baseline restoration and responsive layouts. This does not prove better photography. It shows that real camera behaviour is bounded, testable and recoverable.\n\nPresenter cue: Read the three headline figures first; use the bottom sentence to qualify the claim.",
      [path.join(ROOT, "submission.md"), path.join(ROOT, "outputs", "workbuddy", "FINAL_STABILIZATION_REPORT.md"), path.join(ROOT, "outputs", "workbuddy", "unit-test-run.log"), path.join(ROOT, "outputs", "workbuddy", "deterministic-connected-run1.log")],
    );
  }

  // 6 — Build workflow. Process/timeline composition inspired by Codex Grid slides 01 and 17.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.paper;
    slideHeader(slide, 6, "Agents had distinct jobs—and human approval between them.", "Build approach");

    slide.shapes.add({
      geometry: "rect",
      position: { left: 150, top: 302, width: 980, height: 6 },
      fill: C.line,
      line: { style: "solid", fill: C.line, width: 0 },
    });
    const stages = [
      { x: 110, name: "CODEX", sub: "Trace failures\nWrite regression tests\nImplement focused fixes", c: C.blue },
      { x: 392, name: "WORKBUDDY HY3", sub: "Repo-scale audit\nImplementation pass\nDevice verification", c: C.warm },
      { x: 674, name: "KIMI-K3 + UI/UX", sub: "High-leverage review\nInteraction decisions\nEvidence checks", c: C.green },
      { x: 956, name: "HUMAN APPROVAL", sub: "Resolve tradeoffs\nRun the phone demo\nAccept shipped behavior", c: C.ink },
    ];
    stages.forEach((s, i) => {
      circle(slide, s.x + 72, 279, 52, s.c);
      textBox(slide, String(i + 1), { left: s.x + 72, top: 292, width: 52, height: 24, size: 17, bold: true, color: i === 3 ? C.white : C.ink, align: "center" });
      box(slide, { left: s.x, top: 348, width: 252, height: 192, fill: C.white, line: C.line, radius: "rounded-xl" });
      textBox(slide, s.name, { left: s.x + 22, top: 374, width: 208, height: 28, size: 16, bold: true, color: i === 3 ? C.ink : s.c });
      textBox(slide, s.sub, { left: s.x + 22, top: 418, width: 208, height: 96, size: 18, bold: true, color: C.ink });
    });

    box(slide, { left: 110, top: 576, width: 1098, height: 78, fill: C.ink, line: "none", radius: "rounded-xl" });
    textBox(slide, "Shipped outcome", { left: 142, top: 598, width: 158, height: 24, size: 15, bold: true, color: C.blue });
    textBox(slide, "Clear square Stop guidance · Persistent right-aligned Reset · 57/57 final device tests", { left: 310, top: 596, width: 866, height: 30, size: 20, bold: true, color: C.white, valign: "middle" });
    footer(slide);
    notes(slide,
      "3:34–4:08\n\nSay:\nThe development agents had distinct jobs. I used Codex to trace failures, add regression tests, make focused changes, rebuild and install. WorkBuddy handled the HY3 audit, implementation pass and physical-device verification. Kimi-K3 and a UI/UX specialist supported interaction decisions, with human approval between stages. This produced concrete improvements: the guide matches the square Stop control, Reset remains visible and right-aligned, and all 57 final device tests completed.\n\nPresenter cue: Move across the four roles, then land on the concrete shipped changes rather than the tool names.",
      [path.join(ROOT, "outputs", "workbuddy", "HY3_AUDIT_PACKET.md"), path.join(ROOT, "outputs", "workbuddy", "WORKBUDDY_CHANGELOG.md"), path.join(ROOT, "outputs", "workbuddy", "K3_DEVICE_VERIFICATION.md"), path.join(ROOT, "outputs", "workbuddy", "FINAL_STABILIZATION_REPORT.md")],
    );
  }

  // 7 — Close. Sparse closing layout inspired by Codex Grid slide 26.
  {
    const slide = deck.slides.add();
    slide.background.fill = C.white;
    circle(slide, 1000, 0, 280, C.warmPale);
    circle(slide, 1080, 24, 200, C.bluePale);
    textBox(slide, "PHOTO HELPER", { left: 64, top: 58, width: 280, height: 26, size: 15, bold: true, color: C.blueDark });
    textBox(slide, "Speak in outcomes,\nnot camera settings.", { left: 64, top: 146, width: 930, height: 190, size: 58, bold: true, color: C.ink });
    textBox(slide, "A trustworthy voice-to-camera translator for everyday family photography.", { left: 68, top: 372, width: 800, height: 52, size: 24, color: C.muted });

    box(slide, { left: 64, top: 490, width: 536, height: 104, fill: C.ink, line: "none", radius: "rounded-xl" });
    textBox(slide, "NOW", { left: 92, top: 512, width: 80, height: 22, size: 13, bold: true, color: C.blue });
    textBox(slide, "Consumer family camera utility", { left: 92, top: 544, width: 468, height: 30, size: 22, bold: true, color: C.white });
    box(slide, { left: 624, top: 490, width: 592, height: 104, fill: C.bluePale, line: "none", radius: "rounded-xl" });
    textBox(slide, "NEXT", { left: 652, top: 512, width: 80, height: 22, size: 13, bold: true, color: C.blueDark });
    textBox(slide, "Partner with phone makers or camera apps", { left: 652, top: 544, width: 524, height: 30, size: 22, bold: true, color: C.ink });
    textBox(slide, "Pilot metrics: successful capture · time-to-capture · manual interactions · retakes · confidence", { left: 68, top: 632, width: 1148, height: 28, size: 16, bold: true, color: C.muted });
    footer(slide, "FIVECENT · LU BOLIN · ETHAN YAP · NATHANAEL LEONG");
    notes(slide,
      "4:08–5:00\n\nSay:\nThe immediate product is a family-camera utility: a trustworthy translation layer between spoken intent and controls already on the phone. If a pilot shows value, future distribution could involve phone makers or camera-app partners. Photo Helper might become an optional voice mode, or a limited integration exposing only the same allowlisted actions, validation and Reset behaviour. That is a direction, not a current partnership. Next, we would measure successful capture, time-to-capture, manual interactions, retakes and confidence, then research directly with older adults before making accessibility claims. Photo Helper: speak in outcomes, not camera settings.\n\nPresenter cue: Clearly separate what exists now from the future partnership path, then finish on the tagline.",
      [path.join(ROOT, "submission.md"), path.join(OUT, "project-brief.md"), path.join(OUT, "pitch-day-plan.md")],
    );
  }

  for (const [index, slide] of deck.slides.items.entries()) {
    const stem = `slide-${String(index + 1).padStart(2, "0")}`;
    await writeBlob(path.join(RENDERS, `${stem}.png`), await deck.export({ slide, format: "png", scale: 1 }));
    const layout = await slide.export({ format: "layout" });
    await fs.writeFile(path.join(RENDERS, `${stem}.layout.json`), await layout.text());
  }

  await writeBlob(path.join(OUT, "photo-helper-pitch-deck-montage.webp"), await deck.export({ format: "webp", montage: true, scale: 0.65 }));
  const pptx = await PresentationFile.exportPptx(deck);
  await pptx.save(path.join(OUT, "photo-helper-pitch-deck.pptx"));
}

build().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
