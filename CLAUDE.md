# Photo Helper

Voice-first Android camera-control agent. A user describes the outcome they want
("make it brighter, focus on the person in red"), and the app turns it into a
bounded, reviewable plan it executes against CameraX.

Kotlin + Jetpack Compose, single `:app` module, `com.bolin.photohelper`.

## Build and run

One command builds, installs, and launches on an emulator, booting one first if
nothing is attached:

```powershell
.\run.cmd
```

That wraps [scripts/run-app.ps1](scripts/run-app.ps1), which can also be called directly:

| Goal | Command |
|---|---|
| build, install, launch | `.\run.cmd` |
| clean build first | `.\run.cmd -Clean` |
| build and install, don't launch | `.\run.cmd -NoLaunch` |
| pick a specific emulator | `.\run.cmd -Avd Pixel_7` |
| a different Gradle task | `.\run.cmd -Task assembleDebug` |

The script resolves the repo from its own location, so it works from any working
directory. Android Studio is not needed to build or run - only to create an AVD
the first time (Tools > Device Manager).

Underneath it is just Gradle, if you need the raw commands:

```powershell
.\gradlew.bat installDebug                       # build + install
.\gradlew.bat testDebugUnitTest lintDebug        # JVM tests + lint
.\gradlew.bat connectedDebugAndroidTest          # instrumented, needs a device
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Environment gotchas

These have all bitten this project before; the run script handles the first two
automatically.

- **`local.properties` is gitignored** and `ANDROID_HOME` is not set on this
  machine. Without that file Gradle cannot locate the SDK and fails immediately.
  The script writes it, pointing at `%LOCALAPPDATA%\Android\Sdk`.
- **`local-debug.keystore` is gitignored**, but the `debug` build type signs with
  `signingConfigs.localDebug`, which points at it. A fresh clone has no keystore
  and packaging fails. The script generates one with the standard debug
  credentials (alias `androiddebugkey`, password `android`).
- **Only Windows PowerShell 5.1 is installed - there is no `pwsh`.** Scripts must
  avoid `&&`, `||`, ternaries, and `??`. The older scripts in [scripts/](scripts/) declare
  `#requires -Version 7.0` and therefore will not run as-is.
- **`adb` and `emulator` are not on PATH.** Invoke them by full path under the SDK
  directory, or let the run script resolve them.
- The first build takes several minutes - it downloads Gradle, AGP, and the API 34
  platform. Incremental installs are ~15 seconds.

## Toolchain

- `compileSdk` / `targetSdk` 34, `minSdk` 31
- AGP 8.3.0, Kotlin 1.9.20, Gradle 8.10, builds fine on the installed JDK 23
- Emulator: AVD `Pixel_7`, API 37.1 x86_64. A newer API than `compileSdk` is fine.

On that AVD the system image uses 16 KB memory pages, and the prebuilt native
libraries in `com.google.mlkit:face-detection` and CameraX are not 16 KB aligned,
so Android shows an "App Compatibility" dialog on launch and runs the app in page
size compatibility mode. It is a warning, not a failure - the app works normally.
MLKit face-detection 16.1.7 is the newest version published, so a dependency bump
cannot fully silence it.

## Layout

```
app/src/main/java/com/bolin/photohelper/
  capture/   CameraX session, focus, countdown capture
  coach/     complaint -> control intent -> bounded plan
  ui/        Compose screens
  visual/    frame analysis
  voice/     speech capture and transcription
app/src/test/          JVM unit tests
app/src/androidTest/   instrumented tests
```

See [CONTEXT.md](CONTEXT.md) for the project's domain vocabulary - complaint, control
intent, control capability, measured diagnosis - and prefer those terms in code
and comments.
