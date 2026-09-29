# Woodland Warfare (Android)

An asymmetric woodland war game for Android, modelled on the rules of the board game *Root*.
Play against the computer, or pass one phone around between friends.

It uses its own 12-clearing map, faction names, card names, rules text and graphics; it is a
fan-made rules implementation and is not affiliated with or endorsed by Leder Games.

## Install

Every push builds an APK and publishes it to the **woodland-latest** GitHub release. Open the
release on your phone, tap **WoodlandWarfare.apk**, and allow installing from your browser when
Android asks.

## Factions

| Faction | Plays like | Scores by |
| --- | --- | --- |
| 🐱 Cat Dominion | Industry: sawmills make wood, wood pays for buildings, 3 actions a turn | Building, crafting |
| 🦅 Bird Dynasty | A Decree of orders that grows every turn and must be carried out in full, or the leader falls into turmoil | Roosts each evening |
| 🌿 Forest Uprising | Secret supporters spread sympathy, then revolt to wipe out a clearing and found a base | Sympathy tokens |

Any 2 or 3 factions can play; each seat is "You" or "Computer". First to 30 VP wins.
The full rules are under **How to play** in the app.

## What's in the box

- `engine/` — the complete rules as a plain Kotlin library with no Android code. The game runs as
  one coroutine that pauses at every choice a player makes, so the UI and the computer players just
  answer `Decision`s. A save file is the random seed plus the list of answers.
- `engine/.../Ai.kt` — computer players: a heuristic score on every option, plus a look-ahead for
  the Bird Dynasty's Decree that plays out the rest of its turn (with fresh dice) to avoid turmoil.
- `app/` — the Android UI: a drawn board you can tap, option buttons, your hand, and a game log.

Simplifications compared with the physical game: no Vagabond, no ruins or dominance cards, a
smaller custom deck, and the loser of a battle removes buildings/tokens in the order they choose
from a list.

## Build

```
./gradlew :engine:test assembleDebug
```

The engine tests play 160 full computer-vs-computer games and check that pieces and cards are
conserved, and that replaying a save rebuilds the exact same game.
