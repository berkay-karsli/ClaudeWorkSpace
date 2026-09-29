package com.reef.app.ui.art

import androidx.compose.ui.graphics.vector.ImageVector
import com.reef.engine.AnglersRules
import com.reef.engine.FactionId
import com.reef.engine.Piece
import com.reef.engine.PieceType
import com.reef.engine.Suit

/** Which picture stands for what. */
object Art {
    fun portrait(f: FactionId): ImageVector = when (f) {
        FactionId.SHARKS -> Portraits.shark
        FactionId.SARDINES -> Portraits.sardines
        FactionId.LIONFISH -> Portraits.lionfish
        FactionId.STARFISH -> Portraits.starfish
        FactionId.CORAL -> Portraits.coral
        FactionId.JELLYFISH -> Portraits.jellyfish
        FactionId.PARROTFISH -> Portraits.parrotfish
        FactionId.TURTLES -> Portraits.turtle
        FactionId.SNAKE -> Portraits.snake
        FactionId.REMORAS -> Portraits.remora
        FactionId.CRABS -> Portraits.crab
        FactionId.ANGLERS -> Portraits.angler
        FactionId.OCTOPUS -> Portraits.octopus
        FactionId.CUTTLEFISH -> Portraits.cuttlefish
    }

    fun suit(s: Suit): ImageVector = when (s) {
        Suit.KELP -> Suits.kelp
        Suit.SPONGE -> Suits.sponge
        Suit.PEARL -> Suits.pearl
        Suit.MOON -> Suits.moon
    }

    fun pigment(s: Suit): ImageVector = when (s) {
        Suit.KELP -> Tokens.pigmentKelp
        Suit.SPONGE -> Tokens.pigmentSponge
        else -> Tokens.pigmentPearl
    }

    fun lure(offer: String?): ImageVector = when (offer) {
        AnglersRules.TREASURE -> Tokens.lureTreasure
        AnglersRules.SHELTER -> Tokens.lureShelter
        else -> Tokens.lureGlory
    }

    fun piece(p: Piece): ImageVector = when (p.type) {
        PieceType.CORAL -> Tokens.coral
        PieceType.MARKET -> Tokens.market
        PieceType.NEST -> Tokens.nest
        PieceType.BLOOD -> Tokens.blood
        PieceType.RUBBLE -> Tokens.rubble
        PieceType.EGG -> Tokens.egg
        PieceType.LURE -> lure(p.variant)
        PieceType.PIGMENT -> pigment(p.suit ?: Suit.PEARL)
    }

    /** The icon for an option's kind (the words on its action card). */
    fun action(kind: String): ImageVector = when (kind) {
        "Set up" -> Icons.setup
        "Arrive", "Run" -> Icons.arrive
        "Move" -> Icons.move
        "Move lure" -> Icons.lure
        "Battle", "Fight" -> Icons.battle
        "Dig out" -> Icons.dig
        "Craft" -> Icons.craft
        "Extra action" -> Icons.extra
        "Dominance" -> Icons.dominance
        "End Day" -> Icons.endday
        "Done" -> Icons.done
        "Ambush" -> Icons.ambush
        "Discard", "Pay", "Till" -> Icons.card
        "Hunt" -> Icons.hunt
        "Grow" -> Icons.grow
        "Spawn", "Bloom" -> Icons.spawn
        "Add fish", "Recruit" -> Icons.recruit
        "Leave" -> Icons.leave
        "Bait ball" -> Icons.baitball
        "Gorge" -> Icons.gorge
        "Current" -> Icons.current
        "Drift" -> Icons.drift
        "Graze" -> Icons.graze
        "Sandbar" -> Icons.sandbar
        "Island" -> Icons.island
        "Swim" -> Icons.swim
        "Feed" -> Icons.feed
        "Lay" -> Icons.lay
        "Nest" -> Icons.nest
        "Slither" -> Icons.slither
        "Bite" -> Icons.bite
        "Attach" -> Icons.attach
        "Let go" -> Icons.letgo
        "Market" -> Icons.market
        "Wear shell", "Buy shell" -> Icons.shell
        "Price" -> Icons.price
        "Lure" -> Icons.lure
        "Snap" -> Icons.snap
        "Order" -> Icons.order
        "Reach" -> Icons.reach
        "Grab" -> Icons.grab
        "Steal" -> Icons.steal
        "Recoil" -> Icons.recoil
        "Mantle" -> Icons.mantle
        "Paint" -> Icons.paint
        else -> Icons.move
    }
}
