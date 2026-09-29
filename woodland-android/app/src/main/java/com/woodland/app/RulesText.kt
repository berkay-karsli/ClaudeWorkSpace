package com.woodland.app

import com.woodland.engine.Game

object RulesText {
    val text = """
GOAL
First to ${Game.WIN_VP} victory points (VP) wins — or win by dominance (below). Every faction plays by its own rules.

THE WOODLAND
12 clearings, each Fox 🦊, Rabbit 🐰 or Mouse 🐭, joined by paths. Squares in a clearing are building slots; a grey ruin fills a slot until the Vagabond explores it. The areas between paths are forests — only the Vagabond goes there.
You RULE a clearing if you have the most warriors + buildings there (tokens don't count). The Eyrie Dynasties win ties they are part of (Lords of the Forest). The Vagabond never rules.

Board key: warrior figures with a number = how many. Building tiles: saw blade = sawmill, hammer = workshop, figure = recruiter (cats); tree house = roost (birds); banner = base (uprising). Log = wood, tower = the cats' keep, leaf = sympathy.

TURNS
Each turn has three phases: Birdsong, Daylight, Evening.

MOVING
Move any number of your warriors from a clearing along one path, if you rule the clearing you leave OR the one you enter.

BATTLE
Pick a clearing with your warriors and an enemy piece. The defender may first play an AMBUSH card matching the clearing (2 hits); the attacker can cancel it with an ambush of their own. Then two dice (0–3) are rolled: the attacker deals the higher, the defender the lower, but never more hits than warriors they have there. A defender with no warriors takes an extra hit. Hits remove warriors first, then buildings/tokens. Removing an enemy building or token scores 1 VP.

CARDS AND CRAFTING
Cards have a suit; 🐦 bird cards are wild. Craft a card by using crafting pieces (workshops / roosts / sympathy / the Vagabond's hammers) in clearings matching its cost.
• Items give an item (there are only a few of each) and score VP.
• Favors remove every enemy piece in all clearings of a suit.
• Lasting abilities: Armorers, Sappers, Brutal Tactics, Scouting Party, Royal Claim, Stand and Deliver, Tax Collector, Command Warren, Cobbler, Better Burrow Bank. Tap a card to read it.
Hand limit: 5 at the end of your turn.

DOMINANCE
With 10+ VP you may play a dominance card in Daylight. You stop scoring VP, but win at the start of your turn if you rule 3 clearings of its suit (bird dominance: two opposite corners). Discarded dominance cards can be taken by spending a card of their suit. The Vagabond instead forms a coalition with the player with the fewest VP and wins if they do.

🐱 MARQUISE DE CAT — industry
Birdsong: every sawmill makes a wood.
Daylight: craft with workshops, then take 3 actions: March (two moves), Battle, Recruit (a warrior at each recruiter, once a turn), Build (a sawmill, workshop or recruiter in a clearing you rule, paying wood from clearings connected through clearings you rule), Overwork (discard a card matching a sawmill's clearing to add a wood there). Hawks for Hire: discard a 🐦 card for an extra action.
Buildings score VP and cost more wood as you build more. The Keep: only the Marquise may place pieces in the keep's clearing. Field Hospitals: when your warriors fall, spend a matching card to return them to the keep.
Evening: draw 1 card (+1 with 3 recruiters, +1 with 5).

🦅 EYRIE DYNASTIES — the Decree
Birdsong: add 1 or 2 cards (at most one 🐦) to the Decree's columns: Recruit, Move, Battle, Build. With no roosts, a new roost appears where the woods are emptiest.
Daylight: craft with roosts, then carry out EVERY card in the Decree, left to right, in a clearing matching its suit: recruit at a roost, move from, battle in, or build a roost in a clearing you rule. If you can't: TURMOIL — lose 1 VP per 🐦 card in the Decree, discard all but your leader's two loyal viziers, and choose a new leader.
Leaders: Builder (ignores Disdain for Trade: otherwise the Eyrie scores only 1 VP per item), Charismatic (recruit 2), Commander (+1 hit when attacking), Despot (+1 VP for buildings/tokens destroyed in battle).
Evening: score VP for roosts on the map, then draw.

🌿 WOODLAND ALLIANCE — sympathy and revolt
Supporters are a private stack of cards (max 5 without a base).
Birdsong: Spread sympathy next to existing sympathy (first one anywhere), paying matching supporters — more as you spread, +1 under Martial Law (3+ warriors of another player). Revolt in a sympathetic clearing by paying 2 matching supporters: every enemy piece there is removed, and a base appears with warriors and an officer.
Daylight: craft with sympathy, Mobilize (hand card → supporters), Train (card matching a base → officer).
Evening: one operation per officer: move, battle, recruit at a base, or organize (a warrior becomes sympathy). Then draw 1 + 1 per base.
Outrage: whoever removes sympathy or moves into a sympathetic clearing must give the Alliance a matching card — or it draws one.
Guerrilla War: when defending, the Alliance deals the higher die. Losing a base loses the supporters of its suit and half the officers.

🦝 VAGABOND — a lone wanderer
Lone Wanderer: a single pawn that can't be removed and never rules. Everything it does costs items: ready items are exhausted (↓) to act, and hits damage items (✗).
Choose a character: Thief (steal a random card), Tinker (take a card from the discard pile) or Ranger (hide out to repair 3 items).
Birdsong: refresh 3 exhausted items (+2 per tea), then Slip — move free to an adjacent clearing or forest.
Daylight actions in your clearing:
• Move (👢; one more if hostile warriors are there) — rule doesn't matter
• Battle (🗡️) — you deal up to one hit per undamaged sword
• Explore a ruin (🔥) — take its item, +1 VP
• Aid (any item + a matching card) — give a card to a faction here, and you may take one of their crafted items. Aiding improves your relationship: Indifferent → Amiable → Friendly → Allied, scoring VP at each step; each aid to an ally scores 2 VP
• Quest (the two items shown) — in a clearing of the quest's suit: draw 2 cards, or score VP equal to quests of that suit you've done
• Strike (🏹) — remove one warrior, or a building/token if none
• Repair (🔨) a damaged item, or craft using hammers
Remove a faction's warrior and it turns Hostile: afterwards each of its pieces you remove in battle scores +1 VP (Infamy).
Evening: in a forest, repair everything. Draw 1 + 1 per coins. Carry at most 6 items (+2 per bag).
""".trimIndent()
}
