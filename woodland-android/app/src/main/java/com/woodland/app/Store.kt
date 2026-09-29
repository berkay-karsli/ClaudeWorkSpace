package com.woodland.app

import android.content.Context
import com.woodland.engine.Game
import com.woodland.engine.GameConfig
import kotlin.random.Random

/** Keeps the running game in memory and saves it as seed + answers so it survives restarts. */
object Store {
    var game: Game? = null
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("woodland", Context.MODE_PRIVATE)

    fun newGame(ctx: Context, config: GameConfig): Game {
        val g = Game(config, Random.nextLong())
        g.start()
        game = g
        save(ctx, g)
        return g
    }

    fun save(ctx: Context, g: Game) {
        prefs(ctx).edit()
            .putString("config", g.config.encode())
            .putLong("seed", g.seed)
            .putString("answers", g.answers.joinToString(","))
            .putBoolean("finished", g.finished)
            .apply()
    }

    fun hasUnfinished(ctx: Context): Boolean {
        val p = prefs(ctx)
        return game?.let { !it.finished } ?: (p.contains("config") && !p.getBoolean("finished", false))
    }

    fun load(ctx: Context): Game? {
        game?.let { return it }
        val p = prefs(ctx)
        val config = p.getString("config", null) ?: return null
        return try {
            val answers = p.getString("answers", "")!!.split(",").filter { it.isNotBlank() }.map { it.toInt() }
            Game.replay(GameConfig.decode(config), p.getLong("seed", 0), answers).also { game = it }
        } catch (e: Exception) {
            null
        }
    }

    var fastAi = false

    fun loadSettings(ctx: Context) {
        fastAi = prefs(ctx).getBoolean("fastAi", false)
    }

    fun saveSettings(ctx: Context) {
        prefs(ctx).edit().putBoolean("fastAi", fastAi).apply()
    }
}
