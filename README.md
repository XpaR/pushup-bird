# Pushup Bird

Pushup Bird is an original portrait game. Bars scroll in from the right. Your head is the controller.

Prop the phone upright in front of you, facing the front camera, and do pushups. When you lower down, the bird drops. When you push back up, the bird rises. Thread the gap in each bar. Touch a bar, the ceiling, or the mat and the set is over. Each gap you clear is one point.

It is not Flappy Bird, and it does not use Nintendo art or anyone else's sprites. The bird (sweatband included) and the bars are drawn in code.

The camera preview stays on screen, under the playfield, so you can see yourself. Face detection runs on the phone. Nothing is recorded, saved, or uploaded. There is no account and no ads.

Full camera play needs a physical phone. An emulator usually cannot see you do a pushup, and this repo does not include a desktop preview.

## Play

1. Prop the phone in **portrait**, about head height, with the front camera seeing your face at the top and the bottom of a pushup. More light helps.
2. Allow the camera when asked. The preview is the background of the game.
3. Tap **Start**. There is no height setup. The bird follows your head immediately.
4. Rise to climb. Drop to fall. Slip through the bars.
5. **How sensitive** is optional:
   - **Medium** — the calm default. A normal pushup covers the playfield.
   - **Small** — livelier, for when the phone is far and your head barely moves in the frame.
   - **Large** — gentler, for when the phone is close and your head sweeps the frame.
6. After a game over, tap **Try again**.

Go back once during a set to pause. Go back again to leave. The bird keeps following your head while the bars are held.

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

- head-height smoothing, spike limiting, slow baseline, and the up/down mapping
- a speed limit so one frame cannot throw the bird across the screen
- scoring a cleared gap once
- dying on a bar, the mat, or the ceiling
- a stalled frame not skipping the bird through a bar

Camera, preview, and permission UI need a real device.

## How the bird follows you

The analyzer reads the largest face box from the front camera. Its vertical center, in the upright frame, is a number from 0 (top) to 1 (bottom). A strong moving average and a per-sample step limit knock down jitter. The neutral pose is the middle of the frame, and that baseline only drifts toward your head very slowly, so there is nothing to calibrate. The selected range is how far your head must move to reach the ceiling or the mat. The bird also has a maximum vertical speed, so a single noisy frame cannot fling it across the playfield. A full pushup still reaches the top and the bottom.

## Project map

- `app/src/main/java/com/pushupbird/MainActivity.kt` — permission, start, score, pause
- `app/src/main/java/com/pushupbird/camera/HeadTracker.kt` — CameraX preview and ML Kit face detection
- `app/src/main/java/com/pushupbird/logic/HeadSignal.kt` — smoothing and the head-to-control mapping
- `app/src/main/java/com/pushupbird/logic/GameWorld.kt` — bars, collision, scoring
- `app/src/main/java/com/pushupbird/ui/GameView.kt` — frame loop
- `app/src/main/java/com/pushupbird/ui/GameRenderer.kt` — bird, bars, mat, score

## License

MIT. See [LICENSE](LICENSE).
