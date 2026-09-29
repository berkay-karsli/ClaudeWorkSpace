# Reef (Android)

An asymmetric strategy game on a coral reef, built on Root's bones. The full design is in
[`docs/field-guide.html`](docs/field-guide.html).

All **14 factions** are playable: Sharks, Sardines, Lionfish, Starfish, Coral, Jellyfish,
Parrotfish, Sea Turtles, Sea Snake, Remoras, Hermit Crabs, Anglerfish, Octopus and Cuttlefish.
Play 2 to 4 of them, each by a person or a bot, with pass-and-play on one phone.

## Install

Every push builds a new APK. On your phone, open the repository's **Releases**, find
**Reef (latest build)**, and tap **Reef.apk**. Each build installs over the previous one, and a
game in progress is kept.

## Play

- **New game**: tap 2 to 4 factions (the **?** on each opens its board), choose person or bot for
  each, and use ▲ to set the order. The reach check keeps at least some fighting at every table,
  and the Remoras need two other factions to ride.
- The map is on the left. Pinch to zoom and drag to pan. Each reef shows its suit, its scenery and
  its slots. Its rim takes the color of whoever rules it, and every faction's warriors appear as its
  portrait with a count.
- The panel on the right shows everyone's VP, what the faction on turn has left, and one card per
  action it can take. Each card has a picture of what you pay and what you get.
- Tap an action, then the glowing reefs in order. Arrows on the map show every place the choice can
  go, then exactly what will happen, before you confirm.
- Tap a portrait in the scoreboard, or **Rules**, for a faction's board: its three rules with
  pictures, its Dawn, Day and Dusk, and how it scores.
- **Menu** leaves the game. It is saved after every action; **Continue** on the home screen picks it
  up again.

## For developers

- `engine/`: the rules as a plain Kotlin module with no Android code. Tests cover each faction's
  signature rules, plus bot games across 2 to 4 faction lineups. The bot games check that every game
  ends, that nobody is ever left without a choice (softlocks), and that no piece or card appears or
  vanishes.
- `app/`: the Android app (Jetpack Compose). `app/src/main/kotlin/com/reef/app/ui` has no Android
  imports, so it can also be compiled against Compose Desktop.
- `art/`: every picture in the game, drawn in Python (`creatures.py`, `pieces.py`) with a small
  vector toolkit (`artlib.py`). `build_art.py` writes the same pictures as SVG (`art/svg`, used by
  the design page) and as Compose ImageVector code (`app/.../ui/art/ArtLibrary.kt`).
- `docs/build_field_guide.py`: all faction data. It builds the design page **and**
  `engine/src/main/resources/plates.json`, the rules text the app shows, and refuses to build if the
  design breaks its own consistency rules.
- CI fails if the design page, the rules text or the art code is out of date with its source.
- `desktopcheck/`: compiles the UI against Compose Desktop and renders screenshots of every
  faction's turn without a phone. CI attaches them to the release as `ReefScreens.zip`.

```sh
./gradlew :engine:test -PengineOnly                  # rules and bot games, no Android SDK needed
./gradlew :engine:test -PengineOnly -Pgames=126      # more bot games, with win rates per faction
./gradlew :app:assembleDebug                         # the APK
./gradlew :desktopcheck:screenshots -PdesktopCheck
./gradlew :engine:debugGame -PengineOnly -Pseed=2000 -Plineup=sharks,coral,octopus   # one bot game's log
(cd art && python3 build_art.py)                     # after changing any art
(cd docs && python3 build_field_guide.py)            # after changing any faction data
```
