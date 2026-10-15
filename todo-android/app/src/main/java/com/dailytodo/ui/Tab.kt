package com.dailytodo.ui

import com.dailytodo.data.Space

/** The three screens of the bottom bar. */
enum class Tab(val space: Space?) {
    HOME(Space.HOME),
    UNIVERSITY(Space.UNIVERSITY),
    ALARMS(null),
}

val Space.tab: Tab get() = if (this == Space.HOME) Tab.HOME else Tab.UNIVERSITY
