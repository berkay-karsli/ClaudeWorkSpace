package com.woodland.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.woodland.engine.Faction
import com.woodland.engine.GameConfig
import com.woodland.engine.Seat

class MainActivity : Activity() {
    private enum class Mode(val label: String) { YOU("You"), AI("Computer"), OFF("Not playing") }

    private val modes = mutableMapOf(Faction.CATS to Mode.YOU, Faction.BIRDS to Mode.AI, Faction.ALLIANCE to Mode.AI, Faction.VAGABOND to Mode.AI)
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.loadSettings(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.FOREST_DARK)
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "🌲 Root"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Palette.PARCHMENT)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "A Game of Woodland Might and Right"
            textSize = 15f
            setTextColor(0xFFB8C9A8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(20))
        })

        continueButton = bigButton("▶ Continue game", 0xFFE0A33A.toInt()) { openGame() }
        root.addView(continueButton)

        root.addView(TextView(this).apply {
            text = "New game — tap a faction to change who plays it"
            setTextColor(Palette.PARCHMENT)
            textSize = 15f
            setPadding(0, dp(18), 0, dp(8))
        })
        for (f in Faction.entries) {
            lateinit var b: Button
            b = bigButton("", Palette.faction(f)) {
                modes[f] = Mode.entries[(modes.getValue(f).ordinal + 1) % Mode.entries.size]
                label(b, f)
            }
            label(b, f)
            root.addView(b)
        }
        root.addView(TextView(this).apply {
            text = "2 to 4 factions. Several \"You\" seats = pass-and-play on one phone."
            setTextColor(0xFFB8C9A8.toInt())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(12))
        })
        root.addView(bigButton("⚔ Start new game", 0xFF7A4E2A.toInt()) { startNew() })
        root.addView(bigButton("📜 How to play", 0xFF455A3E.toInt()) { showRules() })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Palette.FOREST_DARK)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        continueButton.visibility = if (Store.hasUnfinished(this)) Button.VISIBLE else Button.GONE
    }

    private fun label(b: Button, f: Faction) {
        val m = modes.getValue(f)
        b.text = "${f.icon}  ${f.label}:  ${m.label}"
        b.alpha = if (m == Mode.OFF) 0.45f else 1f
    }

    private fun startNew() {
        val seats = Faction.entries.filter { modes[it] != Mode.OFF }.map { Seat(it, modes[it] == Mode.YOU) }
        if (seats.size < 2) {
            Toast.makeText(this, "Pick at least 2 factions", Toast.LENGTH_SHORT).show()
            return
        }
        val go = {
            Store.newGame(this, GameConfig(seats))
            openGame()
        }
        if (Store.hasUnfinished(this)) {
            AlertDialog.Builder(this)
                .setMessage("Abandon the game in progress?")
                .setPositiveButton("New game") { _, _ -> go() }
                .setNegativeButton("Cancel", null)
                .show()
        } else go()
    }

    private fun openGame() {
        if (Store.load(this) == null) {
            Toast.makeText(this, "Couldn't load the saved game", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, GameActivity::class.java))
    }

    private fun showRules() {
        val tv = TextView(this).apply {
            text = RulesText.text
            setPadding(dp(20), dp(16), dp(20), dp(16))
            textSize = 14f
        }
        AlertDialog.Builder(this).setTitle("How to play").setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("OK", null).show()
    }

    private fun bigButton(label: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 17f
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(color)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
            topMargin = dp(8)
        }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
