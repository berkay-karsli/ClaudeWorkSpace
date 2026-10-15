# Daily To-Do (Android)

A to-do app with two spaces — **🏡 Home** and **🎓 University** — switched from the bottom bar.
Each space has two sections:

- **Daily routines** — things you do regularly. They renew themselves every morning, so yesterday's
  ticks are cleared automatically. **Swipe right** to mark one done (swipe right again to undo),
  or tap the circle. Doing a routine several times in a row builds a 🔥 streak.
  Each routine can repeat **every day** or only on **chosen weekdays** (e.g. Mon · Wed · Fri); it
  only appears on the days it's due. Routines for other days sit behind a "more on other days" row.
- **Planned events** — one-off things like an exam, a bill or a dentist visit. Give each one or
  more **alert times**; you get a notification at every one of them (with a *Mark done* button).
  Quick picks like "Tomorrow 09:00" and "remind me 1 hour before" make it fast.

**Alarms for each to-do.** Routines can have alarm times (they go off on the days the routine is
due, unless it's already done) and events ring at their alert times. Each to-do can either **ring
like an alarm** — a full-screen alarm screen over the lock screen with *Stop*, *Snooze 10 min* and
*Stop & mark as done*, playing on the alarm volume and getting louder over a few seconds — or just
post a notification. Every to-do picks its **own alarm sound** from the phone's sound picker (or
"Silent" to only vibrate).

**⏰ Alarm ladder** (the *Alarms* tab). Pick a deadline — it always rings — and add as many alarms
before it as you like: tap **Add an alarm at a time**, or keep tapping **−5 min / −10 min / −15 min /
−30 min / −1 h** to add one more before the earliest alarm. Each alarm remembers how long before the
deadline it rings, so adding or removing one never moves the others, and changing the deadline
moves them all together (08:00, 08:10, 08:20, 08:45 with a 10:00 deadline become 09:00 … 11:00 for
an 11:00 deadline). **Apply** creates the alarms in your phone's own clock app. You can keep several
ladders (e.g. "Wake-up", "Exam day"), each with its own name and sound.

Every to-do can have **notes**. Cards show just the name (with a small 📝 icon when notes exist);
tap a card to open it and read or edit its notes. Swipe left to delete (with undo). Home uses warm peach tones and
University uses calm indigo; both follow your phone's light/dark mode.

## Install

Every push builds the app on GitHub Actions and publishes it to the
[`todo-latest` release](../../releases/tag/todo-latest). On your phone, open that page, tap
**DailyTodo.apk** and allow installing from your browser. On first launch, allow notifications so
event alerts can reach you.

## Build locally

Requires JDK 17 and the Android SDK:

```sh
./gradlew testDebugUnitTest assembleDebug
```

## How it works

- Jetpack Compose + Material 3, no network, no account. Data is stored in a small JSON file in the
  app's private storage.
- A routine stores the dates it was done; "done today" just checks today's date, which is why
  routines reset at midnight without any background job.
- Each alert time is an exact `AlarmManager` alarm. Alarms are restored after a reboot or app
  update.
