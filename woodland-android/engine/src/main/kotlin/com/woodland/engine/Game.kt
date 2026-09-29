package com.woodland.engine

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One choice offered to a player. [clearing] is the clearing it acts on (tappable on the board),
 * [from] the origin of a move. [ai] is a heuristic score the computer players maximise.
 */
class Option(val label: String, val clearing: Int = -1, val from: Int = -1, val ai: Double = 0.0)

/** [tag] marks decisions the AI looks ahead on. */
class Decision(val faction: Faction, val prompt: String, val options: List<Option>, val tag: String = "")

internal class Choice(
    val label: String,
    val clearing: Int = -1,
    val from: Int = -1,
    val ai: Double = 0.0,
    val run: suspend () -> Unit,
)

internal class GameOver : RuntimeException()

/**
 * The whole game runs as one coroutine. Each time a player must choose, it suspends and exposes a
 * [Decision] in [pending]; [answer] resumes it. Because the game is deterministic given its seed,
 * the list of [answers] is a complete save file.
 */
class Game(val config: GameConfig, val seed: Long) {
    companion object {
        const val WIN_VP = 30
        const val HAND_LIMIT = 5
        const val MAX_WOOD = 8
        const val MAX_SYMPATHY = 10
        const val MAX_ROUNDS = 60

        /**
         * Rebuilds a game from its answers. With [reseed], dice rolled after the second-to-last
         * answer come from a fresh generator, so the AI can look ahead without seeing real rolls.
         */
        fun replay(config: GameConfig, seed: Long, answers: List<Int>, reseed: Long? = null): Game {
            val game = Game(config, seed)
            game.start()
            for ((i, a) in answers.withIndex()) {
                if (reseed != null && i == answers.lastIndex) game.rng = Random(reseed)
                if (game.finished || game.pending == null || a !in game.pending!!.options.indices) break
                game.answer(a)
            }
            return game
        }
    }

    internal var rng = Random(seed)
    val board: List<ClearingState> = WoodlandMap.clearings.map { ClearingState(it) }
    val players: Map<Faction, PlayerState> = config.seats.associate { it.faction to PlayerState(it.faction, it.human) }
    val order: List<Faction> = Faction.entries.filter { it in players }
    internal val deck = mutableListOf<Card>()
    val discardPile = mutableListOf<Card>()
    val log = mutableListOf<String>()
    val answers = mutableListOf<Int>()

    var pending: Decision? = null
        private set
    private var continuation: Continuation<Int>? = null
    private var started = false
    var finished = false
        private set
    var winner: Faction? = null
        internal set
    var round = 1
        private set
    var current: Faction = order.first()
        internal set
    var phase = "Setup"
        internal set
    var lastRoll: String? = null
        internal set
    internal var catsCorner = -1

    val deckSize get() = deck.size

    fun player(f: Faction) = players.getValue(f)
    fun has(f: Faction) = f in players

    fun start() {
        check(!started)
        started = true
        val block: suspend () -> Unit = { runGame() }
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result ->
            finished = true
            pending = null
            val e = result.exceptionOrNull()
            if (e != null && e !is GameOver) throw e
        })
    }

    fun answer(index: Int) {
        val d = pending ?: error("No decision is pending")
        require(index in d.options.indices) { "Bad option $index" }
        val c = continuation!!
        pending = null
        continuation = null
        answers += index
        c.resume(index)
    }

    internal suspend fun ask(f: Faction, prompt: String, options: List<Option>, tag: String = ""): Int {
        require(options.isNotEmpty()) { "No options for: $prompt" }
        if (options.size == 1) return 0
        return suspendCoroutine { c ->
            pending = Decision(f, prompt, options, tag)
            continuation = c
        }
    }

    internal suspend fun choose(f: Faction, prompt: String, choices: List<Choice>, tag: String = "") {
        val i = ask(f, prompt, choices.map { Option(it.label, it.clearing, it.from, it.ai) }, tag)
        choices[i].run()
    }

    internal fun log(msg: String) {
        log += msg
    }

    internal fun score(f: Faction, n: Int, why: String) {
        if (n == 0) return
        val p = player(f)
        p.vp = max(0, p.vp + n)
        log("${f.icon} ${if (n > 0) "+$n" else "$n"} VP ($why) → ${p.vp}")
        if (p.vp >= WIN_VP) {
            winner = f
            log("🏆 ${f.label} wins with ${p.vp} VP!")
            throw GameOver()
        }
    }

    private suspend fun runGame() {
        deck.addAll(Deck.build().shuffled(rng))
        for (f in order) draw(f, 3)
        if (has(Faction.CATS)) catsSetup()
        if (has(Faction.BIRDS)) birdsSetup()
        if (has(Faction.ALLIANCE)) allianceSetup()
        while (true) {
            for (f in order) {
                current = f
                log("— Round $round: ${f.icon} ${f.label} —")
                when (f) {
                    Faction.CATS -> catsTurn()
                    Faction.BIRDS -> birdsTurn()
                    Faction.ALLIANCE -> allianceTurn()
                }
            }
            round++
            if (round > MAX_ROUNDS) {
                winner = order.maxBy { player(it).vp }
                log("The seasons have passed. ${winner!!.label} leads and wins!")
                throw GameOver()
            }
        }
    }

    // ---------------------------------------------------------------- Queries

    fun name(c: Int) = WoodlandMap.name(c)

    fun ruler(c: Int): Faction? {
        val cs = board[c]
        var best = 0
        var who: Faction? = null
        var tie = false
        for (f in order) {
            val p = cs.rulePower(f)
            if (p > best) {
                best = p; who = f; tie = false
            } else if (p == best && p > 0) tie = true
        }
        if (who == null || !tie) return who
        // Lords of the Forest: the Bird Dynasty rules ties it is part of.
        return if (has(Faction.BIRDS) && cs.rulePower(Faction.BIRDS) == best) Faction.BIRDS else null
    }

    fun rules(f: Faction, c: Int) = ruler(c) == f

    fun warriorsOnMap(f: Faction) = board.sumOf { it.warriors(f) }
    fun warriorSupply(f: Faction): Int =
        f.maxWarriors - warriorsOnMap(f) - (if (f == Faction.ALLIANCE) player(f).officers else 0)

    fun count(type: BuildingType) = board.sumOf { cs -> cs.buildings.count { it == type } }
    fun woodOnMap() = board.sumOf { it.wood }
    fun sympathyOnMap() = board.count { it.sympathy }
    fun keepClearing(): Int? = board.firstOrNull { it.keep }?.id
    fun baseSuits(): List<Suit> = board.filter { BuildingType.BASE in it.buildings }.map { it.suit }

    /** Only the Cat Dominion may place pieces in the keep's clearing. */
    fun canPlace(f: Faction, c: Int) = f == Faction.CATS || !board[c].keep

    fun enemiesIn(f: Faction, c: Int) = order.filter { it != f && board[c].hasAnyPiece(it) }

    fun canMove(f: Faction, from: Int, to: Int) =
        board[from].warriors(f) > 0 && (rules(f, from) || rules(f, to))

    fun legalMoves(f: Faction): List<Pair<Int, Int>> = board.flatMap { cs ->
        if (cs.warriors(f) == 0) emptyList()
        else WoodlandMap.adjacent[cs.id].filter { canMove(f, cs.id, it) }.map { cs.id to it }
    }

    fun battleTargets(f: Faction): List<Pair<Int, Faction>> = board.flatMap { cs ->
        if (cs.warriors(f) == 0) emptyList() else enemiesIn(f, cs.id).map { cs.id to it }
    }

    // ---------------------------------------------------------------- Cards

    internal fun drawCard(): Card? {
        if (deck.isEmpty()) {
            if (discardPile.isEmpty()) return null
            deck.addAll(discardPile.shuffled(rng))
            discardPile.clear()
            log("The discard pile is shuffled into a new deck")
        }
        return deck.removeAt(deck.lastIndex)
    }

    internal fun draw(f: Faction, n: Int) {
        repeat(n) { drawCard()?.let { player(f).hand += it } }
    }

    internal fun discard(card: Card) {
        if (card.kind != CardKind.VIZIER) discardPile += card
    }

    internal fun cardValue(card: Card): Double = when (card.kind) {
        CardKind.AMBUSH -> 5.0
        CardKind.ITEM -> 1.0 + card.vp
        CardKind.FAVOR -> 3.0
        else -> 2.5
    } + if (card.suit == Suit.BIRD) 1.0 else 0.0

    internal suspend fun discardDown(f: Faction) {
        val p = player(f)
        while (p.hand.size > HAND_LIMIT) {
            val i = ask(f, "Discard down to $HAND_LIMIT cards", p.hand.map {
                Option("Discard ${it.title}", ai = -cardValue(it))
            })
            discard(p.hand.removeAt(i))
        }
    }

    internal suspend fun royalClaim(f: Faction) {
        val p = player(f)
        val card = p.effects.firstOrNull { it.kind == CardKind.ROYAL_CLAIM } ?: return
        val n = board.count { rules(f, it.id) }
        choose(f, "Royal Claim", listOf(
            Choice("Discard Royal Claim to score $n VP", ai = n - 2.5) {
                p.effects.remove(card); discard(card); score(f, n, "Royal Claim")
            },
            Choice("Keep it for later", ai = 0.0) {},
        ))
    }

    // ---------------------------------------------------------------- Crafting

    /** Crafting pieces per clearing suit: workshops, roosts or sympathy. */
    fun craftingPieces(f: Faction): IntArray {
        val out = IntArray(3)
        for (cs in board) {
            val n = when (f) {
                Faction.CATS -> cs.buildings.count { it == BuildingType.WORKSHOP }
                Faction.BIRDS -> cs.buildings.count { it == BuildingType.ROOST }
                Faction.ALLIANCE -> if (cs.sympathy) 1 else 0
            }
            out[cs.suit.ordinal] += n
        }
        return out
    }

    private fun canCraft(f: Faction, card: Card, avail: IntArray): Boolean {
        if (card.kind == CardKind.AMBUSH || card.kind == CardKind.VIZIER) return false
        if (card.kind.persistent && player(f).hasEffect(card.kind)) return false
        return Suit.CLEARING_SUITS.all { s -> card.cost.count { it == s } <= avail[s.ordinal] }
    }

    private fun itemVp(f: Faction, card: Card) =
        if (f == Faction.BIRDS && player(f).leader != Leader.BUILDER) 1 else card.vp

    private fun craftAi(f: Faction, card: Card): Double = when (card.kind) {
        CardKind.ITEM -> 2.0 + 3.0 * itemVp(f, card)
        CardKind.FAVOR -> board.filter { it.suit == card.suit }.sumOf { cs ->
            enemiesIn(f, cs.id).sumOf { cs.warriors(it) + 3 * (cs.buildings(it) + cs.tokens(it)) }
        } - 4.0
        else -> 3.0
    }

    internal suspend fun craftPhase(f: Faction) {
        val used = IntArray(3)
        var done = false
        while (!done) {
            val pieces = craftingPieces(f)
            val avail = IntArray(3) { pieces[it] - used[it] }
            val p = player(f)
            val craftable = p.hand.filter { canCraft(f, it, avail) }
            if (craftable.isEmpty()) return
            val choices = craftable.map { card ->
                Choice("Craft ${card.title} (${card.costText}): ${card.effectText}", ai = craftAi(f, card)) {
                    card.cost.forEach { used[it.ordinal]++ }
                    p.hand.remove(card)
                    resolveCraft(f, card)
                }
            } + Choice("Done crafting", ai = 0.5) { done = true }
            choose(f, "Craft with your ${craftPieceName(f)}", choices)
        }
    }

    private fun craftPieceName(f: Faction) = when (f) {
        Faction.CATS -> "workshops"
        Faction.BIRDS -> "roosts"
        Faction.ALLIANCE -> "sympathy"
    }

    private suspend fun resolveCraft(f: Faction, card: Card) {
        log("${f.icon} crafts ${card.title}")
        when {
            card.kind == CardKind.ITEM -> {
                discard(card)
                score(f, itemVp(f, card), "crafted ${card.name}")
            }
            card.kind == CardKind.FAVOR -> {
                discard(card)
                for (cs in board.filter { it.suit == card.suit }) wipeEnemies(f, cs.id)
            }
            else -> player(f).effects += card
        }
    }

    /** Removes every enemy piece in a clearing (favors and revolts). */
    internal suspend fun wipeEnemies(by: Faction, c: Int) {
        val cs = board[c]
        for (enemy in order.filter { it != by }) {
            removeWarriors(c, enemy, cs.warriors(enemy), by)
            for (b in cs.buildings.filter { it.owner == enemy }) removeBuilding(c, b, by, battle = false)
            if (enemy == Faction.CATS) {
                repeat(cs.wood) { removeToken(c, TokenType.WOOD, by, battle = false) }
                if (cs.keep) removeToken(c, TokenType.KEEP, by, battle = false)
            }
            if (enemy == Faction.ALLIANCE && cs.sympathy) removeToken(c, TokenType.SYMPATHY, by, battle = false)
        }
    }

    // ---------------------------------------------------------------- Removal

    internal suspend fun removeWarriors(c: Int, victim: Faction, n: Int, by: Faction?) {
        val k = min(n, board[c].warriors(victim))
        if (k <= 0) return
        board[c].warriors[victim.ordinal] -= k
        log("${victim.icon} loses $k warrior${if (k > 1) "s" else ""} in ${name(c)}")
        if (victim == Faction.CATS) fieldHospitals(c, k)
    }

    private fun removalVp(by: Faction, battle: Boolean) =
        1 + if (battle && by == Faction.BIRDS && player(by).leader == Leader.DESPOT) 1 else 0

    internal suspend fun removeBuilding(c: Int, type: BuildingType, by: Faction?, battle: Boolean) {
        if (!board[c].buildings.remove(type)) return
        log("${type.owner.icon} loses a ${type.label} in ${name(c)}")
        if (type == BuildingType.BASE) baseRemoved(board[c].suit)
        if (by != null && by != type.owner) score(by, removalVp(by, battle), "destroyed a ${type.label}")
    }

    internal suspend fun removeToken(c: Int, type: TokenType, by: Faction?, battle: Boolean) {
        val cs = board[c]
        when (type) {
            TokenType.WOOD -> if (cs.wood > 0) cs.wood-- else return
            TokenType.KEEP -> if (cs.keep) cs.keep = false else return
            TokenType.SYMPATHY -> if (cs.sympathy) cs.sympathy = false else return
        }
        log("${type.owner.icon} loses ${type.label} in ${name(c)}")
        if (by != null && by != type.owner) {
            if (type == TokenType.SYMPATHY) outrage(by, c)
            score(by, removalVp(by, battle), "removed ${type.label}")
        }
    }

    private suspend fun fieldHospitals(c: Int, n: Int) {
        val keep = keepClearing() ?: return
        val cats = player(Faction.CATS)
        val cards = cats.hand.filter { it.suit.matches(board[c].suit) }
        if (cards.isEmpty()) return
        val choices = cards.map { card ->
            Choice("Spend ${card.title} to return $n to the keep", keep, ai = 2.0 * n - cardValue(card)) {
                cats.hand.remove(card)
                discard(card)
                board[keep].warriors[Faction.CATS.ordinal] += n
                log("🐱 Field hospitals: $n warrior(s) return to the keep")
            }
        } + Choice("Let them fall", ai = 0.0) {}
        choose(Faction.CATS, "Field hospitals: $n warrior(s) fell in ${name(c)}", choices)
    }

    internal fun addSupporter(card: Card) {
        val a = player(Faction.ALLIANCE)
        if (baseSuits().isEmpty() && a.supporters.size >= 5) discard(card) else a.supporters += card
    }

    private suspend fun outrage(by: Faction, c: Int) {
        if (!has(Faction.ALLIANCE) || by == Faction.ALLIANCE) return
        val suit = board[c].suit
        val p = player(by)
        val matching = p.hand.filter { it.suit.matches(suit) }
        if (matching.isEmpty()) {
            val card = drawCard()
            if (card != null) addSupporter(card)
            log("😠 Outrage! ${by.icon} has no ${suit.label} card, so the Uprising draws a supporter")
        } else {
            val i = ask(by, "Outrage in ${name(c)}! Give a ${suit.label} card to the Uprising",
                matching.map { Option("Give ${it.title}", c, ai = -cardValue(it)) })
            p.hand.remove(matching[i])
            addSupporter(matching[i])
            log("😠 Outrage! ${by.icon} gives a card to the Uprising's supporters")
        }
    }

    private fun baseRemoved(suit: Suit) {
        val a = player(Faction.ALLIANCE)
        val lost = a.supporters.filter { it.suit.matches(suit) }
        a.supporters.removeAll(lost)
        lost.forEach { discard(it) }
        val o = (a.officers + 1) / 2
        a.officers -= o
        log("🌿 The ${suit.label} base falls: ${lost.size} supporters and $o officers lost")
    }

    // ---------------------------------------------------------------- Movement

    internal suspend fun moveWarriors(f: Faction, from: Int, to: Int, n: Int) {
        board[from].warriors[f.ordinal] -= n
        board[to].warriors[f.ordinal] += n
        log("${f.icon} moves $n from ${name(from)} to ${name(to)}")
        if (board[to].sympathy && f != Faction.ALLIANCE) outrage(f, to)
    }

    internal fun aiMoveScore(f: Faction, from: Int, to: Int): Double {
        val src = board[from]
        val dst = board[to]
        var s = 0.0
        val enemies = enemiesIn(f, to)
        val loot = enemies.sumOf { dst.buildings(it) + dst.tokens(it) }
        val enemyWarriors = enemies.sumOf { dst.warriors(it) }
        s += 3.0 * loot
        if (!rules(f, to)) s += 2.0
        if (dst.freeSlots > 0 && !rules(f, to)) s += 1.0
        if (enemyWarriors > src.warriors(f) + dst.warriors(f)) s -= 3.0
        if (src.buildings(f) + src.tokens(f) > 0) s -= 2.0
        if (enemiesIn(f, from).isNotEmpty()) s -= 2.0
        return s
    }

    /** How many warriors the AI moves: leave one behind to guard its own buildings. */
    private fun aiMoveCount(f: Faction, from: Int): Int {
        val w = board[from].warriors(f)
        return if (board[from].buildings(f) + board[from].tokens(f) > 0 && w > 1) w - 1 else w
    }

    /** Offers every legal move and performs the chosen one. Returns false if skipped. */
    internal suspend fun doMove(f: Faction, prompt: String, allowSkip: Boolean): Boolean {
        val moves = legalMoves(f)
        if (moves.isEmpty()) return false
        var moved = false
        val choices = moves.map { (a, b) ->
            Choice("Move ${name(a)} → ${name(b)}", b, a, ai = aiMoveScore(f, a, b)) {
                val max = board[a].warriors(f)
                val pick = aiMoveCount(f, a)
                val i = ask(f, "How many warriors move from ${name(a)} to ${name(b)}?",
                    (1..max).map { Option("$it", b, a, ai = -kotlin.math.abs(it - pick).toDouble()) })
                moveWarriors(f, a, b, i + 1)
                moved = true
            }
        }.toMutableList()
        if (allowSkip) choices += Choice("Skip this move", ai = 0.5) {}
        choose(f, prompt, choices)
        return moved
    }

    // ---------------------------------------------------------------- Battle

    internal fun aiBattleScore(att: Faction, def: Faction, c: Int): Double {
        val cs = board[c]
        val a = cs.warriors(att)
        val d = cs.warriors(def)
        val loot = cs.buildings(def) + cs.tokens(def)
        var s = 2.0 * min(a, 3) - 1.5 * d + 3.0 * loot - 2.0
        if (d == 0 && loot > 0) s += 4.0
        if (def == Faction.ALLIANCE && d > 0) s -= 2.0
        return s
    }

    internal suspend fun pickBattle(f: Faction, targets: List<Pair<Int, Faction>>) {
        choose(f, "Battle where?", targets.map { (c, enemy) ->
            Choice("Battle ${enemy.icon} ${enemy.label} in ${name(c)}", c, ai = aiBattleScore(f, enemy, c)) {
                battle(f, enemy, c)
            }
        })
    }

    internal suspend fun battle(att: Faction, def: Faction, c: Int) {
        val cs = board[c]
        val ap = player(att)
        val dp = player(def)
        log("⚔️ ${att.icon} attacks ${def.icon} in ${name(c)}")

        if (!ap.hasEffect(CardKind.SCOUTING_PARTY)) {
            val ambushes = dp.hand.filter { it.kind == CardKind.AMBUSH && it.suit.matches(cs.suit) }
            if (ambushes.isNotEmpty()) {
                var played: Card? = null
                choose(def, "${att.label} attacks you in ${name(c)}. Ambush?",
                    ambushes.map { card -> Choice("Ambush with ${card.title} (2 hits)", c, ai = 3.0) { played = card } } +
                        Choice("No ambush", ai = 0.0) {})
                val amb = played
                if (amb != null) {
                    dp.hand.remove(amb)
                    discard(amb)
                    log("💥 ${def.icon} springs an ambush!")
                    val counters = ap.hand.filter { it.kind == CardKind.AMBUSH && it.suit.matches(cs.suit) }
                    var cancelled = false
                    if (counters.isNotEmpty()) {
                        choose(att, "Ambushed in ${name(c)}! Counter it?",
                            counters.map { card ->
                                Choice("Counter with ${card.title}", c, ai = 4.0) {
                                    ap.hand.remove(card); discard(card); cancelled = true
                                    log("${att.icon} counters the ambush")
                                }
                            } + Choice("Take 2 hits", ai = 0.0) {})
                    }
                    if (!cancelled) {
                        removeHits(att, c, 2, def)
                        if (cs.warriors(att) == 0) {
                            log("The attack is broken before it begins")
                            return
                        }
                    }
                }
            }
        }

        val d1 = rng.nextInt(4)
        val d2 = rng.nextInt(4)
        val high = max(d1, d2)
        val low = min(d1, d2)
        // Guerrilla war: the Uprising takes the higher roll when defending.
        val attRoll = if (def == Faction.ALLIANCE) low else high
        val defRoll = if (def == Faction.ALLIANCE) high else low
        var attHits = min(attRoll, cs.warriors(att))
        var defHits = min(defRoll, cs.warriors(def))
        lastRoll = "$d1 · $d2"
        log("🎲 Rolled $d1 and $d2: ${att.icon} deals $attHits, ${def.icon} deals $defHits")

        if (attHits > 0) attHits = armorers(def, c, attHits)
        if (defHits > 0) defHits = armorers(att, c, defHits)
        if (cs.warriors(def) == 0) {
            attHits++
            log("${def.icon} is defenseless: +1 hit")
        }
        if (att == Faction.BIRDS && ap.leader == Leader.COMMANDER) attHits++
        if (ap.hasEffect(CardKind.BRUTAL_TACTICS)) {
            var use = false
            choose(att, "Brutal Tactics: deal an extra hit? ${def.label} scores 1 VP", listOf(
                Choice("Deal an extra hit", c, ai = if (cs.buildings(def) + cs.tokens(def) > 0 || cs.warriors(def) > attHits) 2.0 else -1.0) { use = true },
                Choice("No", ai = 0.0) {},
            ))
            if (use) {
                attHits++
                score(def, 1, "Brutal Tactics")
            }
        }
        val sappers = dp.effects.firstOrNull { it.kind == CardKind.SAPPERS }
        if (sappers != null) {
            choose(def, "Sappers: discard to deal an extra hit?", listOf(
                Choice("Use Sappers", c, ai = if (cs.warriors(att) > defHits) 1.0 else -1.0) {
                    dp.effects.remove(sappers); discard(sappers); defHits++
                },
                Choice("Keep Sappers", ai = 0.0) {},
            ))
        }
        removeHits(def, c, attHits, att)
        removeHits(att, c, defHits, def)
    }

    private suspend fun armorers(victim: Faction, c: Int, hits: Int): Int {
        val p = player(victim)
        val card = p.effects.firstOrNull { it.kind == CardKind.ARMORERS } ?: return hits
        var result = hits
        choose(victim, "Armorers: ignore $hits rolled hit(s) in ${name(c)}?", listOf(
            Choice("Discard Armorers to ignore them", c, ai = hits - 1.5) {
                p.effects.remove(card); discard(card); result = 0
                log("${victim.icon} uses Armorers")
            },
            Choice("Take the hits", ai = 0.0) {},
        ))
        return result
    }

    /** Warriors take hits first; then the owner picks buildings or tokens. */
    internal suspend fun removeHits(victim: Faction, c: Int, n: Int, by: Faction) {
        val cs = board[c]
        var left = n
        val w = min(left, cs.warriors(victim))
        if (w > 0) {
            removeWarriors(c, victim, w, by)
            left -= w
        }
        while (left > 0) {
            val choices = mutableListOf<Choice>()
            for (type in cs.buildings.filter { it.owner == victim }.distinct()) {
                choices += Choice("Lose a ${type.label}", c, ai = if (type == BuildingType.BASE) -9.0 else -3.0) {
                    removeBuilding(c, type, by, battle = true)
                }
            }
            if (victim == Faction.CATS && cs.wood > 0) choices += Choice("Lose a wood", c, ai = -0.5) {
                removeToken(c, TokenType.WOOD, by, battle = true)
            }
            if (victim == Faction.CATS && cs.keep) choices += Choice("Lose the keep", c, ai = -8.0) {
                removeToken(c, TokenType.KEEP, by, battle = true)
            }
            if (victim == Faction.ALLIANCE && cs.sympathy) choices += Choice("Lose sympathy", c, ai = -2.0) {
                removeToken(c, TokenType.SYMPATHY, by, battle = true)
            }
            if (choices.isEmpty()) break
            choose(victim, "Take a hit in ${name(c)} ($left left)", choices)
            left--
        }
    }
}
