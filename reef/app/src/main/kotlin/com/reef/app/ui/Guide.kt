package com.reef.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.reef.app.ui.art.Art
import com.reef.app.ui.art.Icons
import com.reef.app.ui.art.Portraits
import com.reef.app.ui.art.Scenery
import com.reef.app.ui.art.Tokens
import com.reef.engine.FactionId
import com.reef.engine.Suit

/** One piece of an action diagram: a picture, a word such as "→", points, or a card. */
sealed class Glyph {
    data class Pic(val image: ImageVector, val count: String? = null) : Glyph()
    data class Word(val text: String) : Glyph()
    data class Vp(val text: String) : Glyph()
    data class Card(val suit: Suit? = null, val count: String? = null) : Glyph()
}

/** What an action card says: a title, one plain sentence, and a picture of cost → effect. */
data class ActionInfo(val title: String, val blurb: String, val diagram: List<Glyph>)

/**
 * The words and diagrams on every action card. Each line matches the faction's plate in the
 * design guide; the plate itself (in the Rules) has the full wording.
 */
object Guide {
    private fun pic(i: ImageVector, n: String? = null) = Glyph.Pic(i, n)
    private val to = Glyph.Word("→")
    private val plus = Glyph.Word("+")
    private fun vp(t: String) = Glyph.Vp(t)
    private fun card(n: String? = null) = Glyph.Card(null, n)

    fun action(f: FactionId, kind: String): ActionInfo {
        val me = Art.portrait(f)
        val noun = when (f) {
            FactionId.CORAL -> "polyps"
            FactionId.TURTLES -> "turtles"
            FactionId.SNAKE -> "snake"
            FactionId.OCTOPUS -> "arms"
            else -> f.display.lowercase()
        }
        return when (f to kind) {
            // ---- Sharks
            FactionId.SHARKS to "Hunt" -> ActionInfo("Hunt", "Move sharks from one reef to the next (up to 2 reefs toward Blood, or from a gate to any gate), then they may battle there. Sharks that never move drown at Dusk.",
                listOf(pic(me), to, pic(Icons.move), to, pic(Icons.battle)))
            FactionId.SHARKS to "Arrive" -> ActionInfo("Sharks arrive", "1 shark arrives at the gate you choose, or 2 if you have none on the map.",
                listOf(pic(Icons.arrive), to, pic(me, "1-2")))
            FactionId.SHARKS to "Eat Blood" -> ActionInfo("Feed", "Eat this Blood: 2 VP.",
                listOf(pic(Tokens.blood), to, vp("+2")))
            FactionId.SHARKS to "Frenzy" -> ActionInfo("Frenzy", "Instead of eating the Blood, 2 sharks arrive here. They count as having moved, so they don't drown tonight.",
                listOf(pic(Tokens.blood), to, pic(me, "+2")))
            // ---- Coral
            FactionId.CORAL to "Grow" -> ActionInfo("Grow coral", "Discard a card of the reef's suit to grow coral in an empty slot of a reef you rule. Coral scores 1 VP for every 4 on the map. Not in a turn you bleach.",
                listOf(card(), to, pic(Tokens.coral), vp("+VP")))
            FactionId.CORAL to "Spawn" -> ActionInfo("Spawn", "Discard a card: every reef of its suit with your coral puts 1 polyp into each neighboring reef. Once per turn.",
                listOf(card(), to, pic(Tokens.coral), to, pic(me, "+1 each")))
            FactionId.CORAL to "Bleach" -> ActionInfo("Bleach", "Take one of your coral out of the game to draw 2 cards. You can't grow this turn.",
                listOf(pic(Tokens.coral), to, card("+2")))
            FactionId.CORAL to "Battle" -> ActionInfo("Sting", "Your polyps battle a faction in their reef. Coral there doesn't fight when you attack, only when you're attacked.",
                listOf(pic(me), Glyph.Word("vs"), pic(Icons.battle)))
            // ---- Sardines
            FactionId.SARDINES to "Run" -> ActionInfo("The run", "3 sardines arrive at the gate you choose. The game remembers which gate every fish came in by.",
                listOf(pic(Icons.arrive), to, pic(me, "3")))
            FactionId.SARDINES to "Move" -> ActionInfo("Swim as one school", "The whole school in a reef moves up to 2 reefs, ignoring rule. A school of 6 or more may then push a weaker faction out.",
                listOf(pic(me, "all"), to, pic(Icons.move), Glyph.Word("≤2")))
            FactionId.SARDINES to "Rally" -> ActionInfo("Rally", "Discard a card: 2 sardines join a school in a reef of its suit.",
                listOf(card(), to, pic(me, "+2")))
            FactionId.SARDINES to "Push" -> ActionInfo("Wall of fish", "Your big school just arrived: push all of a weaker faction's warriors here to a neighboring reef. Free.",
                listOf(pic(me, "6+"), to, pic(Icons.push)))
            FactionId.SARDINES to "Battle" -> ActionInfo("Mob", "A school of 6 or more battles. It deals at most 1 hit for every 3 fish.",
                listOf(pic(me, "6+"), Glyph.Word("vs"), pic(Icons.battle)))
            FactionId.SARDINES to "Leave" -> ActionInfo("Leave the map", "A school on a gate it didn't come in by leaves: 1–3 fish score 1, 4–6 score 2, 7–9 score 4, 10 or more score 6.",
                listOf(pic(me), to, pic(Icons.leave), vp("1-6")))
            FactionId.SARDINES to "Bait ball" -> ActionInfo("Bait ball", "Give up the fight: lose half the school, and the rest escape to a neighboring reef.",
                listOf(pic(me, "½"), to, pic(Icons.baitball)))
            // ---- Lionfish
            FactionId.LIONFISH to "Gorge" -> ActionInfo("Gorge", "Where you outnumber a faction's warriors, it takes 1 hit: no dice, no hits back. 1 VP per warrior eaten. Then they're stuffed: no moving or gorging there this turn.",
                listOf(pic(me, ">"), pic(Icons.gorge), to, vp("+1")))
            FactionId.LIONFISH to "Arrive" -> ActionInfo("Release", "With fewer than 2 lionfish on the map, 2 more arrive at a gate.",
                listOf(pic(Icons.arrive), to, pic(me, "2")))
            FactionId.LIONFISH to "Release" -> ActionInfo("Release", "Discard a card: 2 lionfish arrive at the gate you choose. Once per turn.",
                listOf(card(), to, pic(Icons.release), pic(me, "+2")))
            FactionId.LIONFISH to "Breed" -> ActionInfo("Breed", "This reef has 3 or more lionfish: a young one is born. Keep it here, or send it next door.",
                listOf(pic(me, "3+"), to, pic(Icons.breed)))
            // ---- Starfish
            FactionId.STARFISH to "Spawn" -> ActionInfo("Spawn", "Discard a card: 2 starfish appear in a reef of its suit where you have starfish (anywhere of that suit if you have none).",
                listOf(card(), to, pic(me, "+2")))
            FactionId.STARFISH to "Devour" -> ActionInfo("Devour", "With 3 or more starfish here: eat an enemy building (+1 VP), or strip an empty slot to Rubble, which scores every Dusk.",
                listOf(pic(me, "3+"), to, pic(Icons.devour), Glyph.Word("or"), pic(Tokens.rubble)))
            FactionId.STARFISH to "Move" -> ActionInfo("Crawl", "Move starfish to a neighboring reef. Ignore rule when crawling toward an enemy building.",
                listOf(pic(me), to, pic(Icons.move)))
            // ---- Jellyfish
            FactionId.JELLYFISH to "Current" -> ActionInfo("Set a current", "Place or turn one of your current arrows (2 per turn). Anyone moving along a current ignores rule, and your jellyfish drift along them at Dusk.",
                listOf(pic(Icons.current)))
            FactionId.JELLYFISH to "Bloom" -> ActionInfo("Bloom", "Discard a card: 1 jellyfish is born in every reef of its suit where you have jellyfish (2 blooms per turn).",
                listOf(card(), to, pic(me, "+1 each")))
            FactionId.JELLYFISH to "Battle" -> ActionInfo("Pulse", "Once per turn, a swarm of 3 or more battles a faction in its reef.",
                listOf(pic(me, "3+"), Glyph.Word("vs"), pic(Icons.pulse)))
            FactionId.JELLYFISH to "Drift" -> ActionInfo("Drift", "This group has more than one current to follow. Choose where it drifts at Dusk.",
                listOf(pic(me), to, pic(Icons.drift)))
            // ---- Parrotfish
            FactionId.PARROTFISH to "Graze" -> ActionInfo("Graze", "In a reef you rule, eat an enemy building or token (+3 Sand, +1 VP), or chew the bare reef (+2 Sand) once per turn.",
                listOf(pic(Icons.graze), to, pic(Tokens.sandbar, "+2/+3")))
            FactionId.PARROTFISH to "Sandbar" -> ActionInfo("Build a sandbar", "Spend 2 Sand to close a channel next to a reef you rule. Only parrotfish can cross it. +1 VP.",
                listOf(pic(Tokens.sandbar, "2"), to, pic(Icons.sandbar), vp("+1")))
            FactionId.PARROTFISH to "Island" -> ActionInfo("Raise an island", "Spend 4 Sand in a reef you rule that touches 2 of your sandbars. It is yours for good and can't be attacked. +3 VP now, +1 every Dusk.",
                listOf(pic(Tokens.sandbar, "4"), to, pic(Tokens.island), vp("+3")))
            FactionId.PARROTFISH to "Cocoon" -> ActionInfo("Pajamas", "Wrap the parrotfish here in slime. Until your next Dusk nothing can attack or push them, but they can't move.",
                listOf(pic(me), to, pic(Tokens.cocoon)))
            // ---- Sea Turtles
            FactionId.TURTLES to "Swim" -> ActionInfo("Swim", "One turtle swims to a neighboring reef, or rides a current as far as it flows, ignoring rule. Turtles carry their food with them.",
                listOf(pic(me), to, pic(Icons.ride)))
            FactionId.TURTLES to "Feed" -> ActionInfo("Feed", "A turtle away from the shore eats the reef's suit. Each turtle can carry one food of each suit. Food can also pay for gear.",
                listOf(pic(me), plus, pic(Icons.feed)))
            FactionId.TURTLES to "Lay" -> ActionInfo("Lay eggs", "A turtle at your nest lays 1 egg per food, plus 2. Each egg scores 1 VP now and 1 more when it hatches, unless enemies wait on the beach.",
                listOf(pic(Icons.feed), to, pic(Tokens.egg, "food+2"), vp("+1 each")))
            FactionId.TURTLES to "Nest" -> ActionInfo("Build a nest", "In an empty slot of a shore reef where you have a turtle. Nests score 1, 2, 3.",
                listOf(pic(me), to, pic(Tokens.nest), vp("1-3")))
            // ---- Sea Snake
            FactionId.SNAKE to "Slither" -> ActionInfo("Slither", "The Head moves one reef, ignoring rule. Each segment follows. Slither onto your own body to coil: everyone else there takes 1 hit.",
                listOf(pic(Icons.slither), to, pic(Icons.move)))
            FactionId.SNAKE to "Bite" -> ActionInfo("Bite", "Battle in the Head's reef. Every warrior the bite removes adds a segment to your tail.",
                listOf(pic(Icons.bite), to, pic(me, "+1")))
            FactionId.SNAKE to "Molt" -> ActionInfo("Molt", "Discard a card: shed your skin and grow 2 segments.",
                listOf(card(), to, pic(Icons.molt), pic(me, "+2")))
            FactionId.SNAKE to "Arrive" -> ActionInfo("A new Head", "The snake is gone: a new Head arrives at the gate you choose.",
                listOf(pic(Icons.arrive), to, pic(me)))
            // ---- Remoras
            FactionId.REMORAS to "Swim" -> ActionInfo("Swim", "Free remoras move one reef, ignoring rule.",
                listOf(pic(me), to, pic(Icons.move)))
            FactionId.REMORAS to "Attach" -> ActionInfo("Attach", "Free remoras stick to another faction's warriors here. Riders can't be hit, go where the host goes, pile into its battles and score when it kills.",
                listOf(pic(me), to, pic(Icons.attach)))
            FactionId.REMORAS to "Let go" -> ActionInfo("Let go", "Riders drop off their host here and are free again.",
                listOf(pic(Icons.letgo), to, pic(me)))
            FactionId.REMORAS to "Clean" -> ActionInfo("Cleaning station", "Clean a faction you ride here: it draws a card, and so do you. Once per turn.",
                listOf(pic(Icons.clean), to, card("+1"), plus, card("+1")))
            FactionId.REMORAS to "Hitch" -> ActionInfo("Hitch a ride", "Discard a card: 2 new remoras stick to a faction's warriors in a reef of its suit.",
                listOf(card(), to, pic(Icons.hitch), pic(me, "+2")))
            FactionId.REMORAS to "Arrive" -> ActionInfo("Arrive", "1 remora arrives at the gate you choose.",
                listOf(pic(Icons.arrive), to, pic(me, "1")))
            // ---- Hermit Crabs
            FactionId.CRABS to "Till" -> ActionInfo("Fill the Till", "Put a card from your hand into your Till. Every Day action spends one Till card.",
                listOf(card(), to, pic(Icons.card)))
            FactionId.CRABS to "Recruit" -> ActionInfo("Recruit", "3 crabs join at one of your markets (at any gate if you have none). Costs 1 Till card.",
                listOf(card(), to, pic(me, "+3")))
            FactionId.CRABS to "Market" -> ActionInfo("Build a market", "Pay a Till card of the reef's suit to build a market in a reef you rule. Every market scores 1 VP each Dusk.",
                listOf(card(), to, pic(Tokens.market), vp("+1/Dusk")))
            FactionId.CRABS to "Move" -> ActionInfo("Move", "Move crabs to a neighboring reef you rule at either end. Costs 1 Till card.",
                listOf(card(), to, pic(me), pic(Icons.move)))
            FactionId.CRABS to "Battle" -> ActionInfo("Battle", "Battle a faction in a reef with your crabs. Costs 1 Till card.",
                listOf(card(), to, pic(me), Glyph.Word("vs"), pic(Icons.battle)))
            FactionId.CRABS to "Wear shell" -> ActionInfo("Wear a shell", "Your crabs here take a shell from your pool for free. It ignores the next hit on them.",
                listOf(pic(me), plus, pic(Tokens.shell)))
            FactionId.CRABS to "Price" -> ActionInfo("Set the price", "How many cards one shell costs the other factions until your next turn.",
                listOf(pic(Tokens.shell), Glyph.Word("="), card("1-3")))
            // ---- Anglerfish
            FactionId.ANGLERS to "Lure" -> ActionInfo("Hang a lure", "Over a rim reef or its neighbor. Treasure: movers draw a card and are hooked. Shelter: nobody but you can battle there. Glory: its ruler scores 1 VP.",
                listOf(pic(Tokens.lureTreasure), pic(Tokens.lureShelter), pic(Tokens.lureGlory)))
            FactionId.ANGLERS to "Move lure" -> ActionInfo("Move a lure", "Move a lure to another rim reef or neighbor, or change what it offers. This relights a dark lure.",
                listOf(pic(Icons.lure), to, pic(Icons.move)))
            FactionId.ANGLERS to "Spawn" -> ActionInfo("Spawn", "Discard a card: 2 anglers join the Trench (2 per turn).",
                listOf(card(), to, pic(me, "+2")))
            FactionId.ANGLERS to "Snap" -> ActionInfo("Snap", "Up to 3 anglers rise from the Trench at a lit lure, a rim reef, or anywhere a hooked faction is. Bite first for 1 hit, then battle. 1 VP per warrior eaten.",
                listOf(pic(Scenery.hole), to, pic(Icons.snap), vp("+1 each")))
            // ---- Octopus
            FactionId.OCTOPUS to "Order" -> ActionInfo("Give an order", "Put a card under an arm with no order. Kelp: reach. Sponge: grab. Pearl: steal. Moon: any. Orders stay.",
                listOf(card(), to, pic(Icons.order)))
            FactionId.OCTOPUS to "Swap" -> ActionInfo("Swap orders", "Once each Dawn, two arms trade their orders.",
                listOf(pic(Icons.order), pic(Icons.swap), pic(Icons.order)))
            FactionId.OCTOPUS to "Reach" -> ActionInfo("Reach", "This arm moves one reef, ignoring rule, staying within 2 reefs of the Mantle.",
                listOf(pic(Icons.reach)))
            FactionId.OCTOPUS to "Grab" -> ActionInfo("Grab", "This arm battles in its reef, with every octopus piece there.",
                listOf(pic(Icons.grab), to, pic(Icons.battle)))
            FactionId.OCTOPUS to "Steal" -> ActionInfo("Steal", "Take an enemy token, or a random card from a faction here, into your garden. +1 VP.",
                listOf(pic(Icons.steal), to, pic(Icons.card), vp("+1")))
            FactionId.OCTOPUS to "Recoil" -> ActionInfo("Recoil", "This arm can't carry out its order: its order and every higher arm's are discarded, −1 VP each.",
                listOf(pic(Icons.recoil), vp("−1 each")))
            FactionId.OCTOPUS to "Mantle" -> ActionInfo("Move the Mantle", "The Mantle jets to a reef with one of your arms. Arms more than 2 reefs away snap back to it.",
                listOf(pic(Icons.mantle), to, pic(Icons.move)))
            FactionId.OCTOPUS to "Ink" -> ActionInfo("Ink", "Discard a card: the battle ends before any hits, and your pieces here jet to a neighboring reef.",
                listOf(card(), to, pic(Icons.ink), to, pic(Icons.move)))
            // ---- Cuttlefish
            FactionId.CUTTLEFISH to "Paint" -> ActionInfo("Paint", "Discard a card: this reef becomes that suit for everyone. Cuttlefish in a painted reef are camouflaged.",
                listOf(card(), to, pic(Icons.paint)))
            FactionId.CUTTLEFISH to "Hatch" -> ActionInfo("Hatch", "Discard a card: 2 cuttlefish hatch in a reef of its suit where you have cuttlefish (any reef of that suit if you have none).",
                listOf(card(), to, pic(Icons.hatch), pic(me, "+2")))
            FactionId.CUTTLEFISH to "Hypnotize" -> ActionInfo("Hypnotize", "Discard a card: move up to 3 warriors of another faction out of a reef with your cuttlefish, to a neighboring reef.",
                listOf(card(), to, pic(Icons.hypnotize), to, pic(Icons.move)))
            FactionId.CUTTLEFISH to "Arrive" -> ActionInfo("Arrive", "With no pigment on the map, 2 cuttlefish arrive at the gate you choose.",
                listOf(pic(Icons.arrive), to, pic(me, "2")))
            else -> shared(kind, me, noun)
        }
    }

    private fun shared(kind: String, me: ImageVector, noun: String): ActionInfo = when (kind) {
        "Set up" -> ActionInfo("Set up", "Place your starting pieces.", listOf(pic(Icons.setup)))
        "Arrive" -> ActionInfo("Arrive", "New $noun arrive where you choose.", listOf(pic(Icons.arrive), to, pic(me)))
        "Move" -> ActionInfo("Move", "Move $noun to a neighboring reef. You must rule where they leave or where they arrive, unless they follow a current.",
            listOf(pic(me), to, pic(Icons.move)))
        "Battle" -> ActionInfo("Battle", "Roll two dice: you deal the higher, they deal the lower, each capped by warriors there. No defenders: 1 extra hit.",
            listOf(pic(me), Glyph.Word("vs"), pic(Icons.battle)))
        "Dig out" -> ActionInfo("Dig out a sandbar", "With warriors at both ends, remove a sandbar. Uses an action.", listOf(pic(Icons.dig), to, pic(Tokens.sandbar)))
        "Craft" -> ActionInfo("Craft gear", "Your crafting pieces pay its suits, each once per turn. Gear scores its VP and gives a lasting bonus.",
            listOf(card(), to, pic(Icons.craft), vp("+VP")))
        "Extra action" -> ActionInfo("Extra action", "Discard any card for one more action this Day. Once per turn.", listOf(card(), to, pic(Icons.extra)))
        "Dominance" -> ActionInfo("Play Dominance", "Stop scoring VP. Win instead if, at the start of your turn, you rule what the card asks.",
            listOf(card(), to, pic(Icons.dominance)))
        "End Day" -> ActionInfo("End your Day", "Go on to Dusk: its steps, then draw.", listOf(pic(Icons.endday)))
        "Done" -> ActionInfo("Done", "Stop here and go on.", listOf(pic(Icons.done)))
        "Ambush" -> ActionInfo("Ambush", "Discard an Ambush of this reef's suit: deal 2 hits before the dice.", listOf(card(), to, pic(Icons.ambush), Glyph.Word("2 hits")))
        "Fight" -> ActionInfo("Fight", "Take the battle without an ambush.", listOf(pic(Icons.battle)))
        "Discard" -> ActionInfo("Discard", "You may keep 5 cards. Choose one to let go.", listOf(card(), to, pic(Icons.card)))
        "Buy shell" -> ActionInfo("Buy a shell", "Pay the Hermit Crabs' price in cards. The shell ignores the next hit on your pieces in that reef, until their next turn.",
            listOf(card("price"), to, pic(Tokens.shell)))
        "Pay" -> ActionInfo("Pay", "Choose a card to pay with.", listOf(card(), to, pic(Portraits.crab)))
        "Spawn" -> ActionInfo("Spawn", "Discard a card to add $noun.", listOf(card(), to, pic(me)))
        "Swim" -> ActionInfo("Swim", "Move to a neighboring reef.", listOf(pic(me), to, pic(Icons.move)))
        else -> ActionInfo(kind, "", listOf(pic(Art.action(kind))))
    }

    /** The picture beside each of a faction's three rules, and its quirk, on its board. */
    fun ruleIcon(f: FactionId, title: String): ImageVector = when (title) {
        "Keep swimming" -> Icons.move
        "Big" -> Icons.battle
        "Blood in the water" -> Tokens.blood
        "Open ocean" -> Icons.hunt
        "The run" -> Icons.arrive
        "One school" -> Icons.swim
        "Bait ball" -> Icons.baitball
        "Wall of fish" -> Icons.push
        "Breed" -> Icons.breed
        "Gorge" -> Icons.gorge
        "Venom" -> Icons.ambush
        "Stuffed" -> Icons.gorge
        "Devour" -> Icons.devour
        "Drawn to food" -> Icons.move
        "Cut in half" -> Icons.recruit
        "Spawn" -> Icons.spawn
        "Rooted" -> Tokens.coral
        "Living reef" -> Icons.battle
        "Bleach" -> Icons.bleach
        "Drift" -> Icons.drift
        "Your currents" -> Icons.current
        "Sting" -> Icons.bite
        "Immortal" -> Tokens.cyst
        "Graze" -> Icons.graze
        "Sandbar" -> Icons.sandbar
        "Island" -> Icons.island
        "Pajamas" -> Icons.cocoon
        "Shell" -> Portraits.turtle
        "Wander" -> Icons.ride
        "Feed and lay" -> Icons.lay
        "Hatchling dash" -> Tokens.egg
        "One body" -> Icons.slither
        "Grow" -> Icons.recruit
        "Cut" -> Icons.battle
        "Coil" -> Icons.slither
        "Attach" -> Icons.attach
        "Tiny" -> Icons.swim
        "Pile on" -> Icons.battle
        "Cleaning station" -> Icons.clean
        "Shell shop" -> Icons.price
        "Till" -> Icons.card
        "Own shells" -> Tokens.shell
        "Vacancy chain" -> Icons.market
        "Lures" -> Icons.lure
        "Snap" -> Icons.snap
        "The Trench" -> Scenery.hole
        "Hooked" -> Tokens.lureTreasure
        "Orders" -> Icons.order
        "Recoil" -> Icons.recoil
        "Reach" -> Icons.reach
        "Ink" -> Icons.ink
        "Paint" -> Icons.paint
        "Camouflage" -> Icons.ambush
        "Galleries" -> Icons.dominance
        "Hypnotize" -> Icons.hypnotize
        else -> Art.portrait(f)
    }
}
