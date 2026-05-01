package com.gelfond.focusblocker

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class BlockReason {
    BEFORE_10_AM,
    OVER_LIMIT_DAYTIME,
    OVER_LIMIT_EVENING_OR_WEEKEND,
    AFTER_9_30_PM
}

data class BlockDecision(
    val shouldBlock: Boolean,
    val reason: BlockReason?,
    val todaysLimitMinutes: Int,
    val accumulatedMinutes: Int,
    val message: String
)

fun isBeforeTenAm(now: LocalTime = LocalTime.now()): Boolean {
    return now.isBefore(LocalTime.of(10, 0))
}

fun isAfterFivePm(now: LocalTime = LocalTime.now()): Boolean {
    return !now.isBefore(LocalTime.of(17, 0))
}

fun isAfterEveningCutoff(now: LocalTime = LocalTime.now()): Boolean {
    return !now.isBefore(LocalTime.of(21, 30))
}

fun isWeekend(now: LocalDateTime = LocalDateTime.now()): Boolean {
    return now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
}

fun defaultLimitForDate(date: LocalDate): Int {
    return when (date.dayOfWeek) {
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 45
        else -> 30
    }
}

fun defaultLimitForNow(now: LocalDateTime = LocalDateTime.now()): Int {
    return defaultLimitForDate(now.toLocalDate())
}

fun computeBlockDecision(
    accumulatedMinutes: Int,
    todaysLimitMinutes: Int,
    now: LocalDateTime = LocalDateTime.now()
): BlockDecision {
    val timeNow = now.toLocalTime()

    return when {
        isBeforeTenAm(timeNow) -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.BEFORE_10_AM,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "It's 10:00 AM Dummy!"
        )

        isAfterEveningCutoff(timeNow) -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.AFTER_9_30_PM,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "It's after 9:30 PM Dummy!"
        )

        accumulatedMinutes >= todaysLimitMinutes &&
                (isAfterFivePm(timeNow) || isWeekend(now)) -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.OVER_LIMIT_EVENING_OR_WEEKEND,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "Stop the brainrot, you hit the limit!"
        )

        accumulatedMinutes >= todaysLimitMinutes -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.OVER_LIMIT_DAYTIME,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "Stop the brainrot, you hit the limit!"
        )

        else -> BlockDecision(
            shouldBlock = false,
            reason = null,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "No block."
        )
    }
}