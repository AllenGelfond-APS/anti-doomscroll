package com.gelfond.focusblocker

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class BlockAction {
    READ,
    BACK_TO_WORK,
    EXERCISE,
    STRETCH,
    DAILY_LOG,
    OVERRIDE
}

enum class BlockReason {
    BEFORE_10_AM,
    OVER_LIMIT_DAYTIME,
    OVER_LIMIT_EVENING_OR_WEEKEND,
    AFTER_9_30_PM
}

data class BlockDecision(
    val shouldBlock: Boolean,
    val reason: BlockReason?,
    val actions: List<BlockAction>,
    val todaysLimitMinutes: Int,
    val accumulatedMinutes: Int,
    val message: String
)

fun isAfterDailyLogTime(now: LocalTime = LocalTime.now()): Boolean {
    return !now.isBefore(LocalTime.of(20, 30))
}

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

fun isWeekdayWorkHours(now: LocalDateTime = LocalDateTime.now()): Boolean {
    val timeNow = now.toLocalTime()
    val isWeekday = now.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY

    return isWeekday &&
            !timeNow.isBefore(LocalTime.of(9, 0)) &&
            timeNow.isBefore(LocalTime.of(17, 0))
}

fun defaultLimitForDate(date: LocalDate): Int {
    return when (date.dayOfWeek) {
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 75
        else -> 60
    }
}

fun defaultLimitForNow(now: LocalDateTime = LocalDateTime.now()): Int {
    return defaultLimitForDate(now.toLocalDate())
}

private fun suggestedActions(now: LocalDateTime): List<BlockAction> {
    val actions = mutableListOf<BlockAction>()
    val timeNow = now.toLocalTime()

    actions += BlockAction.READ

    if (isWeekdayWorkHours(now)) {
        actions += BlockAction.BACK_TO_WORK
    }

    if (isWeekend(now) || isAfterFivePm(timeNow)) {
        actions += BlockAction.EXERCISE
    }

    if (isBeforeTenAm(timeNow)) {
        actions += BlockAction.STRETCH
    }

    if (isAfterDailyLogTime(timeNow)) {
        actions += BlockAction.DAILY_LOG
    }

    actions += BlockAction.OVERRIDE

    return actions.distinct()
}

fun computeBlockDecision(
    accumulatedMinutes: Int,
    todaysLimitMinutes: Int,
    now: LocalDateTime = LocalDateTime.now()
): BlockDecision {
    val timeNow = now.toLocalTime()
    val actions = suggestedActions(now)

    return when {
        isBeforeTenAm(timeNow) -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.BEFORE_10_AM,
            actions = actions,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "Social media is blocked before 10:00 AM."
        )

        isAfterEveningCutoff(timeNow) -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.AFTER_9_30_PM,
            actions = actions,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "Social media is blocked after 9:30 PM."
        )

        accumulatedMinutes >= todaysLimitMinutes &&
                (isAfterFivePm(timeNow) || isWeekend(now)) -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.OVER_LIMIT_EVENING_OR_WEEKEND,
            actions = actions,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "You are over today’s limit."
        )

        accumulatedMinutes >= todaysLimitMinutes -> BlockDecision(
            shouldBlock = true,
            reason = BlockReason.OVER_LIMIT_DAYTIME,
            actions = actions,
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "You are over today’s limit."
        )

        else -> BlockDecision(
            shouldBlock = false,
            reason = null,
            actions = emptyList(),
            todaysLimitMinutes = todaysLimitMinutes,
            accumulatedMinutes = accumulatedMinutes,
            message = "No block."
        )
    }
}