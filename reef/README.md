# Reef (Android)

An asymmetric strategy game on a coral reef, built on Root's bones. The full design, with all 14
factions, is in [`docs/field-guide.html`](docs/field-guide.html).

**Phase 1** is playable now: the whole shared rulebook with **Sharks** and **Coral**, against a bot
or pass-and-play on one phone.

## Install

Every push builds a new APK. On your phone, open the repository's **Releases**, find
**Reef (latest build)**, and tap **Reef.apk**. Each build installs over the previous one, and a
game in progress is kept.

## Play

- **New game**: choose who plays Sharks and Coral (person or bot) and who goes first.
- The map is on the left. Pinch to zoom and drag to pan. The panel on the right says whose turn it
  is and what they can do.
- To act, pick what to do (Hunt, Grow, Spawn…), then tap the highlighted reefs in order, pick a
  card or target if asked, and confirm. **Back** starts the choice over.
- **Rules** shows each faction's three rules and the shared rules, word for word from the design.
- **Menu** leaves the game. It is saved after every action; **Continue** on the home screen picks it
  up again.
- With two people, a cover screen hides each hand while the phone changes hands.

## For developers

- `engine/`: the rules as a plain Kotlin module. It has no Android code and is covered by tests,
  including 30 bot-vs-bot games that check no piece or card ever appears or vanishes.
- `app/`: the Android app (Jetpack Compose). `app/src/main/kotlin/com/reef/app/ui` has no Android
  imports, so it can also be compiled against Compose Desktop.
- `docs/build_field_guide.py`: all faction data. It builds the design page **and**
  `engine/src/main/resources/plates.json`, the rules text the app shows, and refuses to build if the
  design breaks its own consistency rules. CI fails if either output is out of date.
- `desktopcheck/`: compiles the UI against Compose Desktop and renders screenshots without a
  phone. CI attaches them to the release as `ReefScreens.zip`.

```sh
./gradlew :engine:test -PengineOnly        # rules and bot games, no Android SDK needed
./gradlew :app:assembleDebug               # the APK
./gradlew :desktopcheck:screenshots -PdesktopCheck
./gradlew :engine:debugGame -PengineOnly -Pseed=1007   # print one bot game's log
```
