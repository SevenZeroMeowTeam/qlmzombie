package com.qlm.zombie.dayphase

enum class DayPhase(val minDay: Int, val maxDay: Int, val multiplier: Double, @get:JvmName("displayName") val displayName: String) {
    PEACE(0, 24, 0.5, "和平"),
    EASY(25, 49, 1.0, "简单"),
    NORMAL(50, 99, 1.5, "普通"),
    HARD(100, 149, 2.0, "困难"),
    EXTREME(150, Int.MAX_VALUE, 3.0, "极限"),
    LOCKED(Int.MIN_VALUE, Int.MIN_VALUE, 0.0, "锁定");

    val difficultyMultiplier: Double get() = multiplier

    fun isLocked(): Boolean = this == LOCKED

    companion object {
        @JvmStatic
        fun fromDay(day: Int): DayPhase = entries.firstOrNull { day in it.minDay..it.maxDay } ?: LOCKED

        @JvmStatic
        fun forDay(day: Long): DayPhase = fromDay(day.toInt())
    }
}
