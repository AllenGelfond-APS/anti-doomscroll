# Anti Doomscroll

Android app to reduce doomscrolling via time-based blocking and friction-driven overrides. Very Lightweight, doesn't affect battery signficantly.

## Features
- Blocks Instagram, YouTube, Reddit
- Time-based restrictions (morning, evening, night)
- Daily usage limits
- Override system with built-in friction

## Tech
- Kotlin
- AccessibilityService
- Local usage tracking (no UsageStats dependency)

## Status
Personal project, actively iterating

## Core Files

- [Blocking Logic](app/src/main/java/com/gelfond/focusblocker/BlockRules.kt)
- [Accessibility Service](app/src/main/java/com/gelfond/focusblocker/FocusAccessibilityService.kt)
- [Usage Tracking](app/src/main/java/com/gelfond/focusblocker/LocalUsageTracker.kt)
