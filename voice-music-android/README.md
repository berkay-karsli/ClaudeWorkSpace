# Voice Music (Android)

Hands-free song picking for **YouTube Music**, made for driving. Say your wake phrase (default
**"hey music"**), wait for the beep, then **just say the song**:

> "hey music" … *beep* … "Bohemian Rhapsody"
> → the phone says *"Playing Bohemian Rhapsody"* and YouTube Music starts it.

- Any song, artist, album or playlist name works, in English or Turkish ("Tarkan Kuzu Kuzu").
  Saying "play …" first is optional.
- It says out loud what it heard, so you know if it misheard without looking at the screen.
- If it didn't hear you, it asks "Sorry, what should I play?" and listens once more.
- Long titles are fine; it waits for a 1.5 s pause before deciding you're done.

Other commands also work after the beep:

| Say | What happens |
| --- | --- |
| "open YouTube Music" | Opens the app |
| "next" / "skip" / "change song" | Next track |
| "previous" / "go back" | Previous track |
| "pause" / "stop" | Pause |
| "resume" / "continue" | Resume |
| "volume up" / "volume down" | Changes media volume |

Turkish: "sonraki", "önceki", "durdur", "devam", "sesi aç", "sesi kıs", "*şarkı* çal".

A command only counts if it's the whole sentence, so titles like "Hold On", "Back in Black" or
"Another Love" play the song. If a title is exactly a command word (e.g. "Stop"), say
"play Stop".

## Why not "Hey Google" or "Hey Siri"?

Those phrases belong to Google Assistant and Siri; the phone's hotword hardware only wakes those
assistants, and ordinary apps can't claim them. Instead this app has its **own** wake phrase that you
can change in the app (for example "hey music", "okay jukebox", "hello player"). Two or three common
English words work best.

## How a song is started

1. If YouTube Music's player is running (playing or paused), the app asks it directly to
   "play from search" — the same request Android Auto sends. The top result starts playing.
2. If it isn't running, the app presses a virtual "play" media button to wake it (Android sends
   this to the last music app you used), then asks it for the song.
3. After 3.5 s it checks that a new song is actually playing and says its title and artist.
   If not, it opens YouTube Music's search for the song instead and tells you.

Tip: play something in YouTube Music once before driving, so it's the last music app used.

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
3. **Allow notification access** – **required for songs to start by themselves.** Songs are started
   through YouTube Music's player (the way Android Auto does it), and Android only gives apps
   access to other apps' players through this permission. The app doesn't read your notifications.
4. **Allow display over other apps** – needed for "open YouTube Music", and for the fallback
   that opens YouTube Music's search if a song can't be started directly.
5. **Allow notifications** – so you can see the "listening" notification with its Stop button.

Then tap **Start listening**. Use the "Try a command without speaking" box to test commands.

## Notes and limitations

- Requires Android 8.0+ and the Google app (or another speech recognizer) for commands.
- Listening must be started from the app; Android doesn't allow microphone services to start
  themselves in the background (e.g. after reboot).
- Continuous listening uses some battery. Stop it from the notification when you don't need it.
- Keep the phone unlocked on its car mount while driving; Android may not let YouTube Music start a
  new song while the screen is locked.
- With music playing loudly through the phone speaker the wake phrase is harder to hear; a headset
  or a little more distance helps.
- Some battery-saver modes (Xiaomi, Samsung, Huawei…) kill background services. If listening stops
  on its own, set the app's battery usage to "Unrestricted".
