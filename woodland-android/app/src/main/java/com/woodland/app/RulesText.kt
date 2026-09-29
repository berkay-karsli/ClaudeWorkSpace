package com.woodland.app

import com.woodland.engine.Game

object RulesText {
    val text = """
GOAL
First to ${Game.WIN_VP} victory points (VP) wins. Each faction plays by different rules.

THE WOODLAND
12 clearings, each a Fox 🦊, Rabbit 🐰 or Mouse 🐭 clearing, joined by paths. Squares in a clearing are building slots. You RULE a clearing if you have the most warriors + buildings there (tokens don't count). The Bird Dynasty wins ties it is part of.

Board key: coloured discs = warriors (number = how many). S Sawmill, W Workshop, R Recruiter (cats), N Roost (birds), B Base (uprising). ▮ wood, ♜ the cats' keep, ✦ sympathy.

MOVING
Move any number of your warriors from a clearing along one path, if you rule the clearing you leave OR the one you enter.

BATTLE
Pick a clearing with your warriors and an enemy piece. The defender may first play an AMBUSH card matching the clearing (2 hits); the attacker can cancel it with an ambush of their own. Then two dice (0–3) are rolled: the attacker deals the higher, the defender the lower, but never more hits than warriors they have in the clearing. A defender with no warriors takes an extra hit. Hits remove warriors first, then buildings/tokens. Removing an enemy building or token scores 1 VP.

CARDS
Cards have a suit; 🐦 bird cards are wild and match any clearing. Craft cards by using your crafting pieces (workshops / roosts / sympathy) in clearings matching the suits in the cost. Items score VP, favors wipe enemies from every clearing of a suit, and some cards give lasting abilities. Hand limit: 5 at the end of your turn.

🐱 CAT DOMINION — industry
Birdsong: every sawmill makes a wood.
Daylight: craft with workshops, then take 3 actions: March (two moves), Battle, Recruit (a warrior at each recruiter, once per turn), Build (a sawmill, workshop or recruiter in a clearing you rule, paying wood from clearings connected to it through clearings you rule), Overwork (discard a card matching a sawmill's clearing to add wood there). Discard a 🐦 card for an extra action.
Buildings score VP and get pricier as you build more. Only the cats may place pieces in the keep's clearing. Field hospitals: when your warriors fall, spend a matching card to return them to the keep.
Evening: draw 1 card (+1 with 3 recruiters, +1 with 5).

🦅 BIRD DYNASTY — the Decree
Birdsong: add 1 or 2 cards (at most one 🐦) to the Decree's columns: Recruit, Move, Battle, Build. If you have no roosts, a new roost appears where the woods are emptiest.
Daylight: craft with roosts, then carry out EVERY card in the Decree, left to right, each in a clearing matching its suit: recruit at a roost, move from, battle in, or build a roost in a clearing you rule. If you can't carry out a card: TURMOIL — lose 1 VP per 🐦 card in the Decree, discard it all except your leader's two loyal viziers, and choose a new leader.
Leaders: Builder (items score full VP; otherwise birds score only 1 VP per item), Charismatic (recruit 2), Commander (+1 hit when attacking), Despot (+1 VP for buildings/tokens destroyed in battle).
Evening: score VP for roosts on the map, then draw.

🌿 FOREST UPRISING — sympathy and revolt
Supporters are a private stack of cards (max 5 without a base).
Birdsong: Spread sympathy next to existing sympathy (first one anywhere), paying matching supporters — more as you spread, +1 under martial law (3+ enemy warriors). Revolt in a sympathetic clearing by paying 2 matching supporters: every enemy piece there is removed, a base is built with warriors and an officer.
Daylight: craft with sympathy, Mobilize (hand card → supporters), Train (card matching a base → officer).
Evening: one operation per officer: move, battle, recruit at a base, or organize (a warrior becomes sympathy).
Outrage: whoever removes sympathy or moves into a sympathetic clearing must give the Uprising a matching card — or it draws one.
Guerrilla war: when defending, the Uprising deals the higher die.
Losing a base loses the supporters of its suit and half the officers.
""".trimIndent()
}
