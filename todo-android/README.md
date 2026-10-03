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
