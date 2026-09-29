# Voice Music (Android)

Hands-free voice control for **YouTube Music**. Say your wake phrase (default **"hey music"**),
wait for the beep, then say a command:

| Say | What happens |
| --- | --- |
| "open YouTube Music" | Opens the app |
| "play *Bohemian Rhapsody*" / "play *Tarkan*" | Searches YouTube Music and plays the top result |
| "change song", "next", "skip" | Next track |
| "change to *song name*" | Plays that song instead |
| "previous", "go back" | Previous track |
| "pause", "stop" | Pause |
| "resume", "continue", "play" | Resume |
| "volume up" / "volume down" | Changes media volume |

Turkish also works: "sonraki", "önceki", "durdur", "devam", "sesi aç", "sesi kıs", "*şarkı* çal".

## Why not "Hey Google" or "Hey Siri"?

Those phrases belong to Google Assistant and Siri; the phone's hotword hardware only wakes those
assistants, and ordinary apps can't claim them. Instead this app has its **own** wake phrase that you
can change in the app (for example "hey music", "okay jukebox", "hello player"). Two or three common
English words work best.

## How it works

1. `VoiceControlService` is a foreground service (you'll see a notification) that keeps the
   microphone open and runs [Vosk](https://alphacephei.com/vosk/) **offline**, listening only for
   the wake phrase. Nothing is sent anywhere while waiting.
2. When it hears the phrase it beeps, briefly ducks the music, and uses the phone's speech
   recognizer to capture one command (good with song and artist names).
3. `CommandParser` turns the sentence into a command and `YouTubeMusicController` carries it out,
   through YouTube Music's media session (next/pause/…) or the same "play from search" intent
   Google Assistant uses.

## Build and install

No Android Studio needed. Every push that touches this folder runs the
**Voice Music Android** GitHub Actions workflow, which runs the tests and builds an APK:

1. On GitHub open **Actions → Voice Music Android → latest run**, download the
   `VoiceMusic-apk` artifact and unzip it.
2. Copy `app-debug.apk` to your phone and open it (allow "Install unknown apps" when asked).

Or build locally with the Android SDK installed: `./gradlew assembleDebug`
(output: `app/build/outputs/apk/debug/app-debug.apk`).

## First-time setup (in the app)

Tap each button in the **Setup** list:

1. **Allow microphone** – required.
2. **Download voice model** – ~40 MB, one time, for offline wake-phrase detection.
3. **Allow notification access** – lets the app control YouTube Music's playback. (The app doesn't
   read your notifications; Android just ties media control to this permission.)
4. **Allow display over other apps** – Android blocks background apps from opening other apps
   unless this is on. Needed for "open YouTube Music" and "play …" when the screen isn't showing
   this app.
5. **Allow notifications** – so you can see the "listening" notification with its Stop button.

Then tap **Start listening**. Use the "Try a command without speaking" box to test commands.

## Notes and limitations

- Requires Android 8.0+ and the Google app (or another speech recognizer) for commands.
- Listening must be started from the app; Android doesn't allow microphone services to start
  themselves in the background (e.g. after reboot).
- Continuous listening uses some battery. Stop it from the notification when you don't need it.
- With music playing loudly through the phone speaker the wake phrase is harder to hear; a headset
  or a little more distance helps.
- Some battery-saver modes (Xiaomi, Samsung, Huawei…) kill background services. If listening stops
  on its own, set the app's battery usage to "Unrestricted".
