# Baritone — Agent Notes

Minecraft pathfinder bot (Gradle + Unimined, MC 1.21.11, Java 21). Public API is `baritone.api.*` only; everything else is ProGuard-obfuscated in releases.

## Build / test

- JDK 21 required on this branch (Temurin 21 in CI). Older MC branches need older Java — see table in `SETUP.md`.
- Windows: `gradlew.bat ...`; Mac/Linux: `./gradlew ...`.
- `./gradlew build` — full build. CI uses `./gradlew build -Pmod_version="$(git describe --always --tags --first-parent | cut -c2-)"` (version falls back to `mod_version` in `gradle.properties`).
- `./gradlew test` — JUnit4 pure-logic tests, no Minecraft/client needed. Single test: `./gradlew test --tests "baritone.pathing.calc.openset.OpenSetsTest"`.
- Release artifacts (`baritone-api-*`, `baritone-standalone-*`, `baritone-unoptimized-*` jars + `checksums.txt`) land in `dist/` via `CreateDistTask`; ProGuard mappings in `mapping/`. See `SETUP.md#artifacts` for which qualifier to use.
- No lint / format / typecheck config in repo. `javadoc` has `-Xwerror` (fails on doc errors) but only runs as part of `build`, not `test`.

## Loader gotcha

- `gradle.properties` pins `available_loaders=fabric`, and `settings.gradle` only includes those subprojects. `forge/` and `neoforge/` dirs exist but are **not** built until you add them to `available_loaders` (comma-separated).
- `gradle.properties` also hardcodes `org.gradle.java.home` to a Windows path — override or remove it on any other machine instead of "fixing" the toolchain.

## Architecture

- Root `build.gradle` defines shared sourceSets: `api` (public), `main` (impl), `launch` (mixins), `schematica_api`, `test`. Each loader (`fabric/`, `forge/`, `neoforge/`) has its own `build.gradle` (shadowJar + `ProguardTask` + `CreateDistTask`).
- Entrypoints: `src/api/.../BaritoneAPI.java` (static `getProvider()` / `getSettings()`) → `src/main/.../Baritone.java`, `BaritoneProvider.java`. Mappings: Mojmap + Intermediary + Parchment (`2025.12.20`) via Unimined.
- Mixins live in `src/launch/java/baritone/launch/mixins/`, registered client-only in `src/launch/resources/mixins.baritone.json` (`required: true`). ProGuard keeps `baritone.api.**`, `baritone.launch.**`, `BaritoneProvider`, `Settings` field names (reflected), and `@KeepName` members — see `scripts/proguard.pro`.
- External integrations must use only `baritone.api` (`BaritoneAPI.getProvider().getPrimaryBaritone()...`); anything outside it is unsupported and renamed per release.
- Tests in `src/test/java/baritone/` cover pathing math only (`calc/openset`, `goals`, `movement`, `cache`, `utils`, elytra hitbox). No client harness, no fixtures.
