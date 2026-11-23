# Pushup Bird

Pushup Bird is an original portrait game. Bars scroll in from the right. Your head is the controller.

Prop the phone upright in front of you, facing the front camera, and do pushups. When you lower down, the bird drops. When you push back up, the bird rises. Thread the gap in each bar. Touch a bar, the ceiling, or the mat and the set is over. Each gap you clear is one point.

It is not Flappy Bird, and it does not use Nintendo art or anyone else's sprites. The bird (sweatband included) and the bars are drawn in code.

The camera preview stays on screen, under the playfield, so you can see yourself. Face detection runs on the phone. Nothing is recorded, saved, or uploaded. There is no account and no ads.

Full camera play needs a physical phone. An emulator usually cannot see you do a pushup, and this repo does not include a desktop preview.

## Play

1. Prop the phone in **portrait**, about head height, with the front camera seeing your face at the top and the bottom of a pushup. More light helps.
2. Allow the camera when asked. The preview is the background of the game.
3. Hold the **middle** of a pushup — halfway down — and tap **Set height and start**. That pose is the center line.
4. Rise to climb. Drop to fall. Slip through the bars.
5. **Head movement** is how far your head travels on screen:
   - **Small** — phone is far away, so your head barely moves in the frame.
   - **Medium** — a normal setup.
   - **Large** — phone is close, so a pushup sweeps a lot of the frame.
6. **Reset height** during a set pauses and recenters the bird on your head. **Resume** sends the bars again.
7. After a game over, **Reset height and retry** captures your head again.

Go back once during a set to pause. Go back again to leave.

## Build

You need Android Studio (current stable), Android SDK 35, and JDK 17 or newer. The app's `minSdk` is 26 (Android 8.0). A front camera is required.

### Android Studio

1. Open this directory.
2. Let Gradle sync. Studio will download the SDK pieces you do not have yet.
3. Run `app` on a physical phone. Emulator cameras are not a useful test of head tracking.

### Command line

```bash
# JDK 17+. Android Studio's SDK is fine, or any install that contains platform 35.
export ANDROID_HOME="$HOME/Android/Sdk"   # the path you actually use
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With a phone connected and USB debugging on:

```bash
./gradlew installDebug
```

`local.properties` is created by Android Studio (or by you) and is gitignored. It only points at the SDK on your machine.

The face model is the bundled ML Kit face detector, so the game works offline. That makes the APK larger than a Play-services download would. The manifest does not request internet access.

## Tests

`./gradlew testDebugUnitTest` covers the parts that do not need a camera:

- head-height smoothing, spike limiting, and the up/down mapping
- calibration midpoint
- scoring a cleared gap once
- dying on a bar, the mat, or the ceiling
- a stalled frame not skipping the bird through a bar

Camera, preview, and permission UI need a real device.

## How the bird follows you

The analyzer reads the largest face box from the front camera. Its vertical center, in the upright frame, is a number from 0 (top) to 1 (bottom). An exponential moving average plus a per-frame step limit keeps the bird from shaking. **Set height** stores that smoothed value as the midpoint. Moving your head by the selected range above or below the midpoint maps to the top or the bottom of the playfield.

## Project map

- `app/src/main/java/com/pushupbird/MainActivity.kt` — permission, calibration, score, pause
- `app/src/main/java/com/pushupbird/camera/HeadTracker.kt` — CameraX preview and ML Kit face detection
- `app/src/main/java/com/pushupbird/logic/HeadSignal.kt` — smoothing and the head-to-control mapping
- `app/src/main/java/com/pushupbird/logic/GameWorld.kt` — bars, collision, scoring
- `app/src/main/java/com/pushupbird/ui/GameView.kt` — frame loop
- `app/src/main/java/com/pushupbird/ui/GameRenderer.kt` — bird, bars, mat, score

## License

MIT. See [LICENSE](LICENSE).
