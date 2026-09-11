# UI/UX Review of Photo Helper

## What was done

Reviewed the Android project's Compose UI across the camera flow, settings, capture review, gallery/share, and photography guide. Identified strengths and concrete UX issues, then produced a structured report with annotated SVG illustrations.

## Key findings

- **Critical:** the API-key setup UI (`QwenKeySetup`, `VisualProviderChooser`, `StyleProfileField`) is defined in `SettingsSheet.kt` but never invoked, so users cannot enable AI interpretation from Settings.
- **High impact:** the capture review shrinks the just-taken photo to a thumbnail after 1 s and auto-dismisses, which feels like the photo disappeared.
- **Composition guidance overlays** only show rectangles/lines; they do not communicate direction, progress, or paused state.
- **Person-selection buttons cover faces** and use small numbered labels.
- **Camera chrome** mixes icons and text, overloads the Orb, and has a 44 dp mic button below the accessibility minimum.
- **Share screen** hard-codes Telegram and shows the caption field before a caption is generated.

## Deliverables

- `artifacts/ui-ux-review/ui-ux-review.md` — full prioritized report with code pointers.
- `artifacts/ui-ux-review/camera-chrome-analysis.svg`
- `artifacts/ui-ux-review/composition-overlays.svg`
- `artifacts/ui-ux-review/person-selection.svg`
- `artifacts/ui-ux-review/settings-api-key.svg`
- `artifacts/ui-ux-review/review-flow.svg`
- `artifacts/ui-ux-review/gallery-share.svg`

## Continuation notes

The report was validated against the live source after the initial pass. `ThemeMode.SYSTEM` is wired in the theme and activity but is not exposed in Settings; API-key callbacks are wired but their setup composables are not rendered. The gallery viewer has pinch/pan but no adjacent-photo pager, while manual "Load more" is an extra step rather than a hard bug. No production source files were changed during this review.

## Follow-up

Implement the P0/P1 items first, then re-run the instrumented test suite and do a physical-device pass for touch-target and overlay readability checks.
