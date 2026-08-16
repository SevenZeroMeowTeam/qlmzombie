package com.qlm.zombie.dayphase

enum class DayPhase(val minDay: Int, val maxDay: Int, val multiplier: Double, @get:JvmName("displayName") val displayName: String) {
    /** 0-25 天：和平 */
    PEACE(0, 25, 0.5, "和平"),
    /** 26-50 天：简单 */
    EASY(26, 50, 1.0, "简单"),
    /** 51-75 天：普通 */
    NORMAL(51, 75, 1.5, "普通"),
    /** 76-100 天：困难 */
    HARD(76, 100, 2.0, "困难"),
    /** 100 天+：锁定困难，无法更改 */
    LOCKED_HARD(101, Int.MAX_VALUE, 2.0, "锁定困难");

    val difficultyMultiplier: Double get() = multiplier

    fun isLocked(): Boolean = this == LOCKED_HARD

    /** 是否已进入最终锁定阶段（100 天后） */
    fun isFinal(): Boolean = this == LOCKED_HARD

    companion object {
        @JvmStatic
        fun fromDay(day: Int): DayPhase = entries.firstOrNull { day in it.minDay..it.maxDay } ?: LOCKED_HARD

        @JvmStatic
        fun forDay(day: Long): DayPhase = fromDay(day.toInt())
    }
}
