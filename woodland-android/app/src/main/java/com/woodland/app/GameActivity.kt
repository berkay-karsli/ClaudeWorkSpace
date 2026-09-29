package com.woodland.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.woodland.engine.Ai
import com.woodland.engine.BuildingType
import com.woodland.engine.CAT_BUILD_COST
import com.woodland.engine.CAT_BUILD_VP
import com.woodland.engine.Card
import com.woodland.engine.DecreeColumn
import com.woodland.engine.Decision
import com.woodland.engine.Faction
import com.woodland.engine.Game
import com.woodland.engine.ROOST_VP
import com.woodland.engine.SYMPATHY_COST
import com.woodland.engine.Suit
import com.woodland.engine.WoodlandMap
import java.util.concurrent.Executors
import kotlin.random.Random

class GameActivity : Activity() {
    private lateinit var game: Game
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val aiRng = Random(System.nanoTime())

    /** The human faction whose hand and secrets are on screen. */
    private var viewer: Faction? = null
    /** In pass-and-play, the faction that confirmed it is holding the phone. */
    private var revealed: Faction? = null
    private var filterClearing = -1
    private var aiBusy = false
    private var generation = 0
    private var gameOverShown = false

    private lateinit var scoreBar: LinearLayout
    private lateinit var roundText: TextView
    private lateinit var board: BoardView
    private lateinit var infoText: TextView
    private lateinit var handRow: LinearLayout
    private lateinit var promptText: TextView
    private lateinit var optionsBox: LinearLayout
    private lateinit var optionsScroll: ScrollView
    private lateinit var logText: TextView
    private lateinit var passOverlay: LinearLayout
    private lateinit var passText: TextView
    private lateinit var passButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val g = Store.load(this)
        if (g == null) {
            finish()
            return
        }
        game = g
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::game.isInitialized) refresh()
    }

    override fun onPause() {
        super.onPause()
        generation++
        aiBusy = false
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    // ------------------------------------------------------------------ Layout

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.FOREST_DARK)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        scoreBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(scoreBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        roundText = TextView(this).apply {
            setTextColor(Palette.PARCHMENT)
            textSize = 12f
            setPadding(dp(4), 0, dp(4), 0)
        }
        top.addView(roundText)
        top.addView(Button(this).apply {
            text = "☰"
            textSize = 18f
            setTextColor(Palette.PARCHMENT)
            background = null
            minWidth = dp(44)
            minimumWidth = dp(44)
            setOnClickListener { showMenu() }
        }, LinearLayout.LayoutParams(dp(44), dp(40)))
        column.addView(top)

        board = BoardView(this)
        board.game = game
        board.onClearingTap = { onBoardTap(it) }
        column.addView(board, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        logText = TextView(this).apply {
            setTextColor(0xFFB8C9A8.toInt())
            textSize = 12f
            maxLines = 2
            setPadding(dp(10), dp(4), dp(10), dp(2))
            setOnClickListener { showLog() }
        }
        column.addView(logText)

        infoText = TextView(this).apply {
            setTextColor(Palette.PARCHMENT)
            textSize = 12f
            setPadding(dp(10), dp(2), dp(10), dp(2))
        }
        column.addView(infoText)

        handRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        column.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(handRow)
        })

        promptText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(4))
        }
        column.addView(promptText)

        optionsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(12))
        }
        optionsScroll = ScrollView(this).apply { addView(optionsBox) }
        column.addView(optionsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        passText = TextView(this).apply {
            setTextColor(Palette.PARCHMENT)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        passButton = Button(this).apply {
            isAllCaps = false
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                revealed = pendingHuman()
                passOverlay.visibility = View.GONE
                refresh()
            }
        }
        passOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF01C2E1A.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
            isClickable = true
            addView(passText)
            addView(passButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)).apply { topMargin = dp(24) })
        }

        setContentView(FrameLayout(this).apply {
            addView(column)
            addView(passOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
    }

    // ------------------------------------------------------------------ State

    private fun humans() = game.order.filter { game.player(it).human }

    private fun pendingHuman(): Faction? = game.pending?.faction?.takeIf { game.player(it).human }

    /** The decision the person holding the phone can answer right now, if any. */
    private fun myDecision(): Decision? {
        val d = game.pending ?: return null
        if (!game.player(d.faction).human) return null
        if (humans().size > 1 && revealed != d.faction) return null
        return d
    }

    private fun refresh() {
        val human = pendingHuman()
        val needsPass = human != null && humans().size > 1 && revealed != human
        if (human != null && !needsPass) viewer = human
        if (viewer == null) viewer = humans().firstOrNull()
        if (humans().isEmpty()) viewer = game.current

        if (needsPass) {
            passText.text = "Pass the phone to\n\n${human!!.icon} ${human.label}"
            passButton.text = "I'm ${human.label} — show my turn"
            passButton.background = rounded(Palette.faction(human), 12)
            passOverlay.visibility = View.VISIBLE
        } else {
            passOverlay.visibility = View.GONE
        }

        renderScores()
        renderBoard()
        renderInfo()
        renderHand()
        renderLog()
        renderOptions()

        if (game.finished) {
            showGameOver()
        } else if (game.pending != null && human == null) {
            scheduleAi()
        }
    }

    private fun answer(i: Int) {
        filterClearing = -1
        try {
            game.answer(i)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Rules engine error")
                .setMessage(e.toString()).setPositiveButton("OK", null).show()
            return
        }
        Store.save(this, game)
        refresh()
        optionsScroll.scrollTo(0, 0)
    }

    private fun scheduleAi() {
        if (aiBusy) return
        aiBusy = true
        val gen = generation
        val d = game.pending
        val delay = if (Store.fastAi) 120L else 650L
        main.postDelayed({
            if (gen != generation) return@postDelayed
            worker.execute {
                val pick = try {
                    Ai.pick(game, aiRng)
                } catch (e: Exception) {
                    0
                }
                main.post {
                    if (gen != generation) return@post
                    aiBusy = false
                    if (game.pending === d && !isFinishing) answer(pick)
                }
            }
        }, delay)
    }

    // ------------------------------------------------------------------ Rendering

    private fun renderScores() {
        scoreBar.removeAllViews()
        for (f in game.order) {
            val p = game.player(f)
            scoreBar.addView(TextView(this).apply {
                text = "${f.icon} ${p.vp}"
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = rounded(Palette.faction(f), 16).apply {
                    if (f == game.current && !game.finished) setStroke(dp(2), Palette.HIGHLIGHT)
                }
                setOnClickListener { showFactionInfo(f) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            })
        }
        roundText.text = if (game.finished) "Game over" else "Round ${game.round}\n${game.current.icon} ${game.phase}"
    }

    private fun renderBoard() {
        val d = myDecision()
        val visible = d?.options?.filter { filterClearing < 0 || it.clearing == filterClearing }
        board.highlights = visible?.mapNotNull { o -> o.clearing.takeIf { it >= 0 } }?.toSet() ?: emptySet()
        board.arrows = visible?.filter { it.from >= 0 && it.clearing >= 0 }?.map { it.from to it.clearing }?.distinct() ?: emptyList()
        board.selected = filterClearing
        board.invalidate()
    }

    private fun renderLog() {
        logText.text = game.log.takeLast(2).joinToString("\n")
    }

    private fun renderInfo() {
        val f = viewer ?: return
        val secret = (game.player(f).human && (humans().size == 1 || myDecision() != null)) || humans().isEmpty()
        infoText.text = factionSummary(f, secret) + "\n" + game.order.joinToString("  ") {
            "${it.icon}✋${game.player(it).hand.size}"
        } + "  🂠${game.deckSize}"
    }

    private fun factionSummary(f: Faction, secret: Boolean): String {
        val p = game.player(f)
        val sb = StringBuilder("${f.icon} ${f.label}: ")
        when (f) {
            Faction.CATS -> {
                sb.append(listOf(BuildingType.SAWMILL, BuildingType.WORKSHOP, BuildingType.RECRUITER).joinToString(" · ") { t ->
                    val n = game.count(t)
                    if (n >= t.max) "${t.label} ${n}/6" else "${t.label} $n (next ${CAT_BUILD_COST[n]}▮ +${CAT_BUILD_VP.getValue(t)[n]})"
                })
                sb.append(" · supply ${game.warriorSupply(f)}")
            }
            Faction.BIRDS -> {
                sb.append("${p.leader?.label ?: "-"} · roosts ${game.count(BuildingType.ROOST)} (+${ROOST_VP[game.count(BuildingType.ROOST)]}/turn)\n")
                sb.append("Decree  " + DecreeColumn.entries.joinToString("  ") { col ->
                    "${col.label}: " + p.decree[col.ordinal].joinToString("") { it.suit.symbol }.ifEmpty { "–" }
                })
            }
            Faction.ALLIANCE -> {
                val sup = if (secret) {
                    Suit.entries.joinToString(" ") { s -> "${s.symbol}${p.supporters.count { it.suit == s }}" }
                } else "${p.supporters.size}"
                sb.append("supporters $sup · officers ${p.officers} · sympathy ${game.sympathyOnMap()}/10")
                if (game.sympathyOnMap() < 10) sb.append(" (next costs ${SYMPATHY_COST[game.sympathyOnMap()]})")
            }
        }
        if (p.effects.isNotEmpty()) sb.append("\nAbilities: " + p.effects.joinToString(", ") { it.name })
        return sb.toString()
    }

    private fun renderHand() {
        handRow.removeAllViews()
        val f = viewer ?: return
        val p = game.player(f)
        if (!p.human && humans().isNotEmpty()) return
        if (humans().size > 1 && myDecision() == null) {
            handRow.addView(TextView(this).apply {
                text = "Hands are hidden between turns"
                setTextColor(0xFFB8C9A8.toInt())
                textSize = 12f
            })
            return
        }
        if (p.hand.isEmpty()) {
            handRow.addView(TextView(this).apply {
                text = "No cards in hand"
                setTextColor(0xFFB8C9A8.toInt())
                textSize = 12f
            })
        }
        for (card in p.hand) handRow.addView(cardView(card))
    }

    private fun cardView(card: Card) = TextView(this).apply {
        text = "${card.suit.symbol} ${card.name}" + if (card.costText.isNotEmpty()) "\n${card.costText}" + (if (card.vp > 0) " → ${card.vp} VP" else "") else "\n${card.kind.label}"
        textSize = 11f
        setTextColor(Palette.INK)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = rounded(Palette.PARCHMENT, 8).apply { setStroke(dp(3), Palette.suit(card.suit)) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(6)
        }
        setOnClickListener {
            AlertDialog.Builder(this@GameActivity).setTitle(card.title)
                .setMessage("${card.suit.label} card · ${card.kind.label}\n" +
                    (if (card.cost.isNotEmpty()) "Craft cost: ${card.costText}\n" else "") + card.effectText)
                .setPositiveButton("OK", null).show()
        }
    }

    private fun renderOptions() {
        optionsBox.removeAllViews()
        if (game.finished) {
            promptText.text = "🏆 ${game.winner?.label ?: "Nobody"} wins!"
            optionsBox.addView(optionButton("Back to menu", Palette.FOREST) { finish() })
            return
        }
        val d = game.pending ?: return
        val mine = myDecision()
        if (mine == null) {
            promptText.text = if (game.player(d.faction).human) "Waiting for ${d.faction.icon} ${d.faction.label}…"
            else "${d.faction.icon} ${d.faction.label} is thinking…"
            return
        }
        promptText.text = "${d.faction.icon} ${d.prompt}"
        val shown = d.options.indices.filter { filterClearing < 0 || d.options[it].clearing == filterClearing }
        if (filterClearing >= 0) {
            optionsBox.addView(optionButton("◀ All options (showing ${WoodlandMap.name(filterClearing)})", 0xFF455A3E.toInt()) {
                filterClearing = -1
                refresh()
            })
        } else if (board.highlights.isNotEmpty()) {
            optionsBox.addView(TextView(this).apply {
                text = "Tip: tap a highlighted clearing to pick it"
                setTextColor(0xFFB8C9A8.toInt())
                textSize = 11f
            })
        }
        for (i in shown) {
            val o = d.options[i]
            val color = if (o.clearing >= 0) Palette.suit(WoodlandMap.clearings[o.clearing].suit) else Palette.faction(d.faction)
            optionsBox.addView(optionButton(o.label, 0xFF33452F.toInt(), accent = color) { answer(i) })
        }
    }

    private fun optionButton(label: String, color: Int, accent: Int? = null, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = rounded(color, 10).apply { if (accent != null) setStroke(dp(2), accent) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        }
        minHeight = dp(44)
        minimumHeight = dp(44)
        setOnClickListener { onClick() }
    }

    // ------------------------------------------------------------------ Interaction

    private fun onBoardTap(c: Int) {
        val d = myDecision()
        if (d != null) {
            val matches = d.options.indices.filter { d.options[it].clearing == c }
            if (matches.size == 1 && filterClearing != c) {
                filterClearing = c
                refresh()
                return
            }
            if (matches.size == 1) {
                answer(matches[0])
                return
            }
            if (matches.size > 1) {
                filterClearing = c
                refresh()
                return
            }
        }
        showClearing(c)
    }

    private fun showClearing(c: Int) {
        val cs = game.board[c]
        val lines = mutableListOf<String>()
        lines += "${cs.suit.label} clearing · ${cs.def.slots} slot${if (cs.def.slots > 1) "s" else ""}"
        lines += "Ruled by: " + (game.ruler(c)?.let { "${it.icon} ${it.label}" } ?: "nobody")
        for (f in game.order) {
            val parts = mutableListOf<String>()
            if (cs.warriors(f) > 0) parts += "${cs.warriors(f)} warrior${if (cs.warriors(f) > 1) "s" else ""}"
            cs.buildings.filter { it.owner == f }.groupBy { it }.forEach { (t, l) -> parts += "${l.size} ${t.label}" }
            if (f == Faction.CATS && cs.wood > 0) parts += "${cs.wood} wood"
            if (f == Faction.CATS && cs.keep) parts += "the keep"
            if (f == Faction.ALLIANCE && cs.sympathy) parts += "sympathy"
            if (parts.isNotEmpty()) lines += "${f.icon} " + parts.joinToString(", ")
        }
        lines += "Paths to: " + WoodlandMap.adjacent[c].joinToString(" ") { WoodlandMap.name(it) }
        AlertDialog.Builder(this).setTitle("Clearing ${WoodlandMap.name(c)}")
            .setMessage(lines.joinToString("\n")).setPositiveButton("OK", null).show()
    }

    private fun showFactionInfo(f: Faction) {
        val p = game.player(f)
        val secret = (p.human && viewer == f && (humans().size == 1 || myDecision() != null)) || humans().isEmpty()
        AlertDialog.Builder(this).setTitle("${f.icon} ${f.label} — ${p.vp} VP")
            .setMessage(factionSummary(f, secret) + "\nCards in hand: ${p.hand.size}" +
                (if (secret && p.hand.isNotEmpty()) "\n" + p.hand.joinToString("\n") { "• ${it.title}" } else "") +
                "\n" + (if (p.human) "Played by you" else "Computer player"))
            .setPositiveButton("OK", null).show()
    }

    private fun showLog() {
        val tv = TextView(this).apply {
            text = game.log.takeLast(300).reversed().joinToString("\n")
            setPadding(dp(18), dp(12), dp(18), dp(12))
            textSize = 13f
        }
        AlertDialog.Builder(this).setTitle("Game log (newest first)")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("OK", null).show()
    }

    private fun showMenu() {
        val items = arrayOf(
            "📜 How to play",
            "📖 Game log",
            if (Store.fastAi) "🐢 Slower computer turns" else "⚡ Faster computer turns",
            "🏠 Back to main menu",
        )
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> {
                    val tv = TextView(this).apply {
                        text = RulesText.text
                        setPadding(dp(18), dp(12), dp(18), dp(12))
                        textSize = 13f
                    }
                    AlertDialog.Builder(this).setTitle("How to play")
                        .setView(ScrollView(this).apply { addView(tv) }).setPositiveButton("OK", null).show()
                }
                1 -> showLog()
                2 -> {
                    Store.fastAi = !Store.fastAi
                    Store.saveSettings(this)
                }
                3 -> finish()
            }
        }.show()
    }

    private fun showGameOver() {
        if (gameOverShown) return
        gameOverShown = true
        val w = game.winner
        val scores = game.order.sortedByDescending { game.player(it).vp }
            .joinToString("\n") { "${it.icon} ${it.label}: ${game.player(it).vp} VP" }
        AlertDialog.Builder(this).setTitle(if (w != null) "🏆 ${w.label} wins!" else "Game over")
            .setMessage("$scores\n\nRounds played: ${game.round}")
            .setPositiveButton("Main menu") { _, _ -> finish() }
            .setNegativeButton("Look at the board", null)
            .show()
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
