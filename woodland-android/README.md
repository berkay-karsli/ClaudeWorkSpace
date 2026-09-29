# Root (Android fan edition)

A personal, unofficial Android version of the board game *Root* by Cole Wehrle and Leder Games.
Play against the computer, or pass one phone around between friends. Not affiliated with or
endorsed by Leder Games — buy the real game!

It uses Root's faction and card names; the map, rules text and all board graphics are original.

## Install

Every push builds an APK and publishes it to the **woodland-latest** GitHub release. Open the
release on your phone, tap **Root.apk**, and allow installing from your browser when
Android asks.

## Factions

| Faction | Plays like | Scores by |
| --- | --- | --- |
| 🐱 Marquise de Cat | Industry: sawmills make wood, wood pays for buildings, 3 actions a turn | Building, crafting |
| 🦅 Eyrie Dynasties | A Decree of orders that grows every turn and must be carried out in full, or the leader falls into turmoil | Roosts each evening |
| 🌿 Woodland Alliance | Secret supporters spread sympathy, then revolt to wipe out a clearing and found a base | Sympathy tokens |
| 🦝 Vagabond | A lone wanderer (Thief, Tinker or Ranger) who spends items to explore ruins, quest, aid and fight | Ruins, quests, relationships, infamy |

Any 2 to 4 factions can play; each seat is "You" or "Computer". First to 30 VP wins, or win by
playing a dominance card (the Vagabond forms a coalition instead).
The full rules are under **How to play** in the app.

## What's in the box

- `engine/` — the complete rules as a plain Kotlin library with no Android code. The game runs as
  one coroutine that pauses at every choice a player makes, so the UI and the computer players just
  answer `Decision`s. A save file is the random seed plus the list of answers.
- `engine/.../Ai.kt` — computer players: a heuristic score on every option, plus a look-ahead for
  the Eyrie Dynasties's Decree that plays out the rest of its turn (with fresh dice) to avoid turmoil.
- `app/` — the Android UI: a drawn board you can tap, option buttons, your hand, and a game log.

Also implemented: ruins and forests, the shared item supply, dominance cards and coalitions, and
the lasting card abilities (Armorers, Sappers, Brutal Tactics, Scouting Party, Royal Claim, Stand
and Deliver, Tax Collector, Command Warren, Cobbler, Better Burrow Bank).

Differences from the physical game: an original map and a 53-card deck (real card names, costs from memory), the Vagabond
refreshes its items automatically (undamaged first), allied warriors don't move or fight with the
Vagabond, and Codebreakers is left out. All board art is drawn in code.

## Build

```
./gradlew :engine:test assembleDebug
```

The engine tests play 160 full computer-vs-computer games and check that pieces and cards are
conserved, and that replaying a save rebuilds the exact same game.
