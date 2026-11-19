# AviTrack — Project & Coding Rules (KMP Contest)

## Stack & Targets
- **Project:** AviTrack (Offline-first digital logbook & early warning system for laying hen farms).
- **Module & Package:** `org.avitrack.app` inside `shared/src/commonMain/kotlin/org/avitrack/app/`.
- **Targets:** `commonMain` (core, UI, state), `androidMain`, `jvmMain` (Desktop), `wasmJsMain` & `jsMain` (Web/Vercel), `iosMain` (compilation stub). Entrypoints live in `androidApp`, `desktopApp`, `webApp`.
- **UI:** Compose Multiplatform (Material 3). No platform-specific UI libraries.

## Strict Coding Standards (20% Jury Score)
- Follow **Kotlin Coding Conventions** strictly (`https://kotlinlang.org/docs/coding-conventions.html`).
- All code identifiers (classes, functions, variables, comments, git commits) MUST be in **English**.
- UI strings must support runtime switching between **English (default)** and **Spanish** via a lightweight pure-Kotlin `CompositionLocal` (`LocalStrings`).
- Prefer immutable `data class`, `sealed interface`, `StateFlow`, and pure functions in `commonMain`.
- Avoid `!!` (not-null assertion), wildcard imports (`*`), and magic numbers (use named constants).

## Domain & Zootechnical Rules
- Egg counts are NOT logged (handled externally by Moba grading machines).
- Daily log inputs: `analogTempMin`, `analogTempMax`, `digitalTempMin`, `digitalTempMax` (°C), `relativeHumidityAvg` (%), `mortalityCount` (birds/day), `feedKg` (kg/day), `waterM3` (m³/day), `litterPercentage` (0..100 step 10, informational only, NO alerts), `notes` (String).
- Alive birds are dynamically decremented: `aliveBirds = initialBirdCount - cumulativeMortality`.
- Alert thresholds:
  - Temp (analog or digital): `< 18.0°C` or `> 28.0°C`.
  - Sensor drift: `abs(digitalTemp - analogTemp) > 2.0°C`.
  - Sustained mortality: `> 0.02%` of alive birds for `>= 3` consecutive days without explanatory notes.
  - Feed intake: target `118 g/bird/day` (alert if `< 110g` or `> 126g`).
  - Water intake: target `210–260 cm³/bird/day` (alert if `< 210` or `> 260`).
  - Humidity: `< 50%` or `> 75%`.

## Token-Saving Output Protocol
- Output **ONLY** requested code or file diffs.
- **NO** greetings, explanations, summaries, or post-code commentary unless explicitly asked.