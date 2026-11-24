# Pushup Bird

Pushup Bird is an original portrait game. Bars scroll in from the right. Your head is the controller.

Prop the phone upright in front of you, facing the front camera, and do pushups. When you lower down, the bird drops. When you push back up, the bird rises. Thread the gap in each bar. Touch a bar, the ceiling, or the mat and the set is over. Each gap you clear is one point.

It is not Flappy Bird, and it does not use Nintendo art or anyone else's sprites. The bird (sweatband included) and the bars are drawn in code.

The camera preview stays on screen, under the playfield, so you can see yourself. Face detection runs on the phone. Nothing is recorded, saved, or uploaded. There is no account and no ads.

Full camera play needs a physical phone. An emulator usually cannot see you do a pushup, and this repo does not include a desktop preview.

## Play

1. Prop the phone in **portrait**, about head height, with the front camera seeing your face at the top and the bottom of a pushup. More light helps.
2. Allow the camera when asked. The preview is the background of the game.
3. Tap **Start**. The bird sits on the same horizontal line as your head in the camera preview. There is no sensitivity setting and no height setup.
4. Lower into a pushup and the bird goes down with your head. Push back up and it comes back up. It does not jump, drift, or fall on its own.
5. If the camera loses your face for a moment, the bird holds still on the last line until it sees you again.
6. After a game over, tap **Try again**.

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

- 1:1 mapping from the head's place on screen to the bird
- holding that line when the face is missing, with no drift and no drop
- a 3-sample median that ignores a one-frame spike, then an ease that does not overshoot
- the FILL_CENTER crop from the camera image into the preview
- scoring a cleared gap once
- dying on a bar, the mat, or the ceiling
- a stalled frame not skipping the bird through a bar

Camera, preview, and permission UI need a real device.

## How the bird follows you

The analyzer reads faces from the front camera and maps each box through the same rotation and FILL_CENTER crop as the on-screen preview. The bird's vertical center is that screen line: a head 35% of the way down the preview puts the bird 35% of the way down the playfield.

The first face is the largest one. After that, the tracker stays with the face nearest the line it was already following, so a second face or a one-frame false box does not steal the bird. A 3-sample median throws out a single glitch, and a short ease follows the median without velocity, so it cannot coast past the head or keep moving after the head stops. Missed frames are ignored. The bird stays on the last line. There is no gravity, no baseline, and no sensitivity gain.

## Project map

- `app/src/main/java/com/pushupbird/MainActivity.kt` — permission, start, score, pause
- `app/src/main/java/com/pushupbird/camera/HeadTracker.kt` — CameraX preview and ML Kit face detection
- `app/src/main/java/com/pushupbird/logic/HeadSignal.kt` — median, ease, and preview crop math
- `app/src/main/java/com/pushupbird/logic/GameWorld.kt` — bars, collision, scoring
- `app/src/main/java/com/pushupbird/ui/GameView.kt` — frame loop
- `app/src/main/java/com/pushupbird/ui/GameRenderer.kt` — bird, bars, mat, score

## License

MIT. See [LICENSE](LICENSE).
