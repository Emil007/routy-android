# Status notes for whoever opens this next (probably future-you, in Android Studio)

This file exists because most of this repo was written in a sandbox with **no Android SDK
and no way to install one** — the sandbox's outbound network policy blocks `dl.google.com`
entirely, which backs both the `google()` Gradle repository and every `androidx.*`/Play
Services artifact. So: some of this is real, tested code, and some of it is careful,
traced-against-the-server-source Kotlin that has never been compiled. This file says which
is which, so the first Android Studio sync isn't a surprise.

## First real sync error, and how it actually got fixed (post-M0-M7)

First actual Android Studio sync failed with `Unable to load class
'com.android.build.gradle.api.BaseVariant'`. Root cause: enough time passed between this
project being scaffolded and actually being opened that Android Gradle Plugin crossed a major
version (8.x → 9.x) in the meantime — AGP 9.0 removed the old Variant API wholesale
(`BaseVariant` and everything built on it), which is exactly what that error means.

**Nine attempts confirmed failed (the eighth and ninth themselves deliberate diagnostics); the
tenth was the actual fix, confirmed working in a real Android Studio sync** (kept below, struck
through in spirit rather than
deleted, because the reasoning in each is real and each later attempt builds directly on what the
previous ones ruled out):

1. First attempt bumped AGP `8.7.2` → `9.2.1`, Gradle `8.14.3` → `9.7.0`, all four Kotlin
   plugins `2.0.21` → `2.2.10`, added `android.builtInKotlin=false` (opting out of AGP 9's new
   default Kotlin integration to keep the existing `org.jetbrains.kotlin.android` plugin
   declaration), and migrated the now-fully-removed `kotlinOptions {}` DSL to the top-level
   `kotlin { compilerOptions { ... } }` block Kotlin's own docs specify. `./gradlew :logic:test`
   passed clean against the new Gradle/Kotlin combination in this sandbox — real confirmation
   that half worked — but a real Android Studio sync (which this sandbox can never do, no SDK)
   hit the exact same `BaseVariant` error from a more specific angle.
2. Second attempt added `android.enableLegacyVariantApi=true`, reasoning from Google's own
   AGP-9-migration material that this flag exists specifically to restore old Variant API
   classes for plugins like `org.jetbrains.kotlin.android` that still depend on them.
   **This didn't work** — Android Studio reported the identical stack trace afterward,
   unchanged down to the line numbers. The flag evidently doesn't put the actual
   `BaseVariant.class` file back on the classpath (which is what Gradle's own class-decorating
   reflection needs to instantiate `KotlinAndroidTarget`) — whatever it does restore isn't
   enough for this specific failure.

3. Third attempt retreated from AGP 9 entirely: reverted to AGP `8.13.2` (the last 8.x release,
   where `BaseVariant` genuinely still exists) paired with Kotlin `2.3.20` per
   [Google's Kotlin/AGP compatibility table](https://developer.android.com/build/kotlin-support),
   and removed both AGP-9-only flags from attempts 1–2. `./gradlew :logic:test` passed clean
   against Gradle 8.14.3 + Kotlin 2.3.20. **This didn't fix it either** — a real Android Studio
   sync hit a *different* error this time: `NoClassDefFoundError:
   com/android/build/gradle/BaseExtension`, thrown from Kotlin Gradle Plugin's own
   `AgpWithBuiltInKotlinAppliedCheck.checkIfNewDslIsUsed` — a diagnostic *inside KGP itself*
   that runs unconditionally whenever `org.jetbrains.kotlin.android` is applied, regardless of
   AGP major version, specifically to detect this exact legacy-plugin-vs-new-DSL conflict. Not a
   real absence of `BaseExtension` in AGP 8.13.2's jar — a KGP-side check tripping over the
   conflict it exists to catch.

Attempts 1–3 all shared one bug, invisible until reading AGP 9's own
[release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) instead of
migration guides and compatibility tables. AGP 9 ships **two** independent flags, not one:
`android.builtInKotlin` (default `true` — enables AGP's own Kotlin-Android integration) and
`android.newDsl` (**also** default `true` — exposes only the new extension types, not the legacy
`BaseExtension`/`BaseVariant` ones). Attempt 1 set `android.builtInKotlin=false` while keeping
`org.jetbrains.kotlin.android` applied — but `android.newDsl` stayed `true` regardless, so AGP
still exposed only the new DSL, and `kotlin-android`'s internal `BaseVariant` references broke
exactly as before. The flag needed was `android.newDsl=false`, not `android.builtInKotlin=false`
— a flag that was never tried. Attempt 3's retreat to AGP 8.13.2 sidestepped that specific bug
but ran into KGP 2.3.20's `AgpWithBuiltInKotlinAppliedCheck`, a check that (per the docs) exists
purely to catch this same kotlin-android/built-in-Kotlin conflict — on an AGP version that
predates the conflict entirely, but the check itself doesn't know that.

4. Fourth attempt, following
   [developer.android.com/build/migrate-to-built-in-kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin):
   removed `org.jetbrains.kotlin.android` outright and let AGP 9's built-in Kotlin support (on by
   default) own the Kotlin-Android integration, reasoning there'd be no second integration left
   to conflict with it. Bumped to AGP `9.3.0` (current stable, per
   [developer.android.com/build/releases/gradle-plugin](https://developer.android.com/build/releases/gradle-plugin)
   — no longer guessing a version) and Gradle `9.7.0`, and pinned the remaining compiler plugins
   (`org.jetbrains.kotlin.plugin.compose`/`.serialization`, plus `:logic`'s
   `org.jetbrains.kotlin.jvm`) to `2.2.10` — the version
   [AGP 9.0's release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
   name as AGP 9's own runtime-dependency floor. Also made `android.builtInKotlin=true` explicit
   in `gradle.properties` (redundant with AGP 9's default, but attempt 1 set the *sibling* flag
   to `false` by mistake, so spelling it out guards against that regression coming back
   silently). `./gradlew :logic:test` passed clean against Gradle 9.7.0 + Kotlin 2.2.10.
   **Still didn't fix it** — the *exact same* `BaseVariant` `NoClassDefFoundError` came back, but
   through a different code path this time:
   `KotlinAndroidPlugin.Companion.dynamicallyApplyWhenAndroidPluginIsApplied` — a decades-old
   Kotlin Gradle Plugin convenience
   that auto-applies `org.jetbrains.kotlin.android` the instant it sees `com.android.application`
   on a project, *regardless of whether anyone declared it*, for people who forgot to apply
   `kotlin-android` explicitly by hand. Nothing in `app/build.gradle.kts` asked for it — the
   `org.jetbrains.kotlin.plugin.compose`/`.serialization` compiler plugins pull in enough of
   KGP's common machinery to trigger this convenience on their own. At `2.2.10`, it doesn't yet
   know to back off when AGP's built-in Kotlin is already handling the Android target, so it
   fired anyway and hit the same legacy `BaseVariant` reference.

5. Fifth attempt reasoned that attempt 4's *architecture* (built-in Kotlin, no
   `kotlin-android`, AGP 9.3.0) was correct and only the Kotlin version was wrong: AGP 9.0's
   release notes name `2.2.10` as AGP's runtime-dependency *floor*, which isn't the same claim
   as "this version cooperates with built-in Kotlin," and
   [Google's Kotlin/AGP compatibility table](https://developer.android.com/build/kotlin-support)
   caps Kotlin `2.2.x`/`2.3.x` at the *last 8.x* AGP release each (`8.10`/`8.13`) while leaving
   `2.4.x` open-ended (`8.5.2+`) — read as "the first line actually validated against AGP 9."
   Bumped `org.jetbrains.kotlin.jvm`/`.plugin.compose`/`.plugin.serialization` from `2.2.10` →
   `2.4.10` and nothing else. `./gradlew :logic:test` passed clean against Gradle 9.7.0 + Kotlin
   2.4.10. **Still didn't fix it, and disproved the theory outright**: the *exact same*
   `BaseVariant` `NoClassDefFoundError` came back a third time, but through yet another code
   path — this time inside **AGP's own** built-in-Kotlin initialization
   (`BuiltInKotlinServicesKt.initBuiltInKotlinSupport` →
   `KotlinBaseApiPlugin.createKotlinAndroidExtension`), not KGP's auto-apply convenience. Three
   different Kotlin versions (`2.2.10`, `2.3.20`, `2.4.10`) across two different code paths had
   now all hit the identical failure inside the identical class
   (`org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget`) — a strong sign the Kotlin
   version was never the actual variable, and further doc-reading-based guessing had stopped
   being productive.

At this point the user pointed out they'd also updated Android Studio itself, with no
difference — worth recording, since it rules out "stale IDE" as a factor too. Rather than a
seventh guess from documentation, the user generated a real, disposable "Empty Activity" project
from **the same Android Studio installation** this project is actually being synced in, and sent
back its exact generated files — genuine ground truth instead of another inference:

- `gradle/libs.versions.toml`: `agp = "9.3.1"`, `kotlin = "2.2.10"`, `composeBom = "2026.02.01"`.
- `app/build.gradle.kts`'s `plugins {}`: **only** `android-application` and `kotlin-compose` —
  no `kotlin-android`, but also no `kotlin-serialization`, confirming this project's separate
  `org.jetbrains.kotlin.plugin.serialization` application (which the template has no equivalent
  of at all) was the one remaining structural difference from a build that's known to work.
- `gradle-wrapper.properties`: Gradle `9.5.0`.
- `gradle.properties`: no `android.builtInKotlin` line at all (relying on AGP 9's own default,
  same conclusion this file already reached, just without needing the flag spelled out).

6. Sixth attempt, working entirely off that diff instead of more research: `:app` only applied
   `org.jetbrains.kotlin.plugin.serialization` for one reason — `GithubReleaseDto` (the update
   checker's response DTO, `app/src/main/kotlin/com/routy/app/update/GithubReleaseClient.kt`)
   was declared directly in `:app` with `@Serializable`. Moved it to
   `logic/src/main/kotlin/com/routy/app/logic/api/GithubReleaseModels.kt` instead — `:logic`
   already applies the serialization compiler plugin (for its own `api/*Models.kt`) and isn't an
   Android module, so it can never hit this AGP/KGP conflict regardless of what AGP does. `:app`
   dropped `org.jetbrains.kotlin.plugin.serialization` from its plugins block entirely, keeping
   only the `kotlinx-serialization-json` *runtime* dependency (still needed for `Json {}` and the
   Retrofit converter factory — the compiler plugin is only needed by the module that *declares*
   `@Serializable` classes, not one that merely serializes them). Also matched every version
   exactly to the working template rather than re-deriving anything: AGP `9.3.0` → `9.3.1`,
   Gradle `9.7.0` → `9.5.0` (with `gradle-wrapper.properties`'s `distributionSha256Sum` copied
   from the template's file too), Kotlin `2.4.10` → `2.2.10` for the plugins `:app` still uses
   (`kotlin.plugin.compose`) as well as `:logic`'s (`kotlin.jvm`, `kotlin.plugin.serialization`).

`./gradlew :logic:test` passed clean against Gradle 9.5.0 + Kotlin 2.2.10 in this sandbox after
this sixth attempt (39 tests, 0 failures, `GithubReleaseDto` compiling correctly in its new
location). **Still didn't fix it** — a real Android Studio sync hit the *exact same*
`NoClassDefFoundError: com/android/build/gradle/api/BaseVariant`, through the *exact same*
`initBuiltInKotlinSupport` → `createKotlinAndroidExtension` → `KotlinAndroidTarget` path as
attempt 5, despite AGP/Gradle/Kotlin and the plugin set now matching the verified-working
template *exactly*. That ruled out plugin choice and version pinning as variables entirely —
before writing a seventh guess, it was worth confirming a premise that had gone unchecked: did
the disposable Empty-Activity template actually **sync successfully itself**, or had its files
only been extracted without confirming a clean sync? Asked, and confirmed: yes, that template
synced clean, with these same exact versions — which rules out a broken local Gradle/AGP cache
or environment as the explanation, and confirms this is genuinely something about
*routy-android's project structure*, not a bad local setup.

7. Seventh attempt: with plugins and versions now identical to a project that's confirmed to
   sync, the one structural difference left was that routy-android is a **multi-module** build
   (`:app` + `:logic`) while the template is single-module — and `gradle.properties` had
   `org.gradle.configureondemand=true` set, a flag Gradle itself prints `Configuration on demand
   is an incubating feature.` for on every single build, specifically because it changes how a
   *multi-project* build orders and scopes subproject configuration. It had been added purely
   for this sandbox's benefit (`./gradlew :logic:test` needs it to avoid configuring `:app`,
   whose `com.android.application` plugin can't resolve at all with `dl.google.com` blocked) —
   not something CI or a real Android Studio install needs, since both have full internet access
   and (per `.github/workflows/ci.yml`'s own comment on its `app-build` job) `ubuntu-latest`
   ships a real Android SDK already. Removed it, along with `org.gradle.parallel=true` (also
   unset in the template) and added `org.gradle.configuration-cache=true` (present in the
   template, a *stable*, non-incubating feature). `:logic:test` still runs fine in this
   sandbox — just needs the same flag passed explicitly on the CLI now instead of defaulted from
   the properties file: `./gradlew :logic:test --configure-on-demand`. Updated
   `.github/workflows/ci.yml`'s `logic-test` job comment to match (it no longer *avoids*
   configuring `:app` by design — it just happens not to need to fail there, since
   `ubuntu-latest` has a real SDK and network access either way).

`./gradlew :logic:test --configure-on-demand` passed clean against Gradle 9.5.0 + Kotlin 2.2.10
in this sandbox after this seventh attempt (39 tests, 0 failures). **Still didn't fix it** — a
real Android Studio sync hit the *exact same* `NoClassDefFoundError: BaseVariant`, through the
*exact same* `initBuiltInKotlinSupport` → `KotlinAndroidTarget` path as attempts 5 and 6, despite
AGP/Gradle/Kotlin, the plugin set, *and* now `gradle.properties` all matching the verified-working
template closely. Seven consecutive changes to `:app`'s and the project's build configuration had
now each individually failed to change the outcome at all.

8. **Eighth attempt: a deliberate diagnostic, not a fix.** With plugin choice, every version, and
   now `gradle.properties` all ruled out one at a time, the one remaining structural difference
   between routy-android and the verified-working single-module template is that routy-android is
   **multi-module** (`:app` + `:logic`) at all. A web search independently turned up other people
   hitting this exact class of failure — `KotlinAndroidTarget`/`BaseVariant`-related
   `NoClassDefFoundError` during Android-target class generation — specifically when modularizing
   a Kotlin project, with reports of "no issues when using only a single module." That + the
   crash consistently happening during Gradle's **plugin-application phase** (before any script
   body or Kotlin source — including `:app`'s own — ever gets touched) makes for a clean,
   conclusive test: temporarily remove `:logic` from the build entirely
   (`settings.gradle.kts`'s `include(":logic")` and `app/build.gradle.kts`'s
   `implementation(project(":logic"))`, both commented out rather than deleted) and check whether
   `:app` alone reaches a successful sync. `:app`'s Kotlin source still references `:logic`
   classes and will fail to *compile* — irrelevant to this test, since compilation is a separate,
   later phase that a sync attempt doesn't need to reach to answer the one question this is
   asking: does the `BaseVariant` crash disappear once `:logic` is gone?

   This is intentionally **not** presented as a fix — restoring real multi-module support (moving
   business logic back out of `:app`, however that ends up working once the actual mechanism is
   understood) is deferred until the diagnostic result comes back.

**Result: the identical crash still happened with `:logic` completely gone from
`settings.gradle.kts`.** Same `NoClassDefFoundError: BaseVariant`, same
`initBuiltInKotlinSupport` → `KotlinAndroidTarget` path, `CONFIGURE FAILED in 360ms` — failing on
the very first plugin, same as every attempt since the fourth. This looks like it rules out
multi-module-ness definitively... except the eighth attempt's diagnostic was actually incomplete,
caught only on rereading it after this result: the root `build.gradle.kts` still declared
`org.jetbrains.kotlin.jvm` and `org.jetbrains.kotlin.plugin.serialization` (both `apply false`)
for `:logic`'s benefit, *even with `:logic` itself excluded from the build*. Root-level
`plugins {}` entries get resolved onto the build's shared plugin classpath regardless of whether
any project actually applies them — so the eighth attempt never actually tested ":app, completely
alone." It tested ":app, plus two extra Kotlin Gradle Plugin artifact declarations resolving
against a module that no longer exists to consume them" — which is still, structurally, a
multiple-Kotlin-Gradle-Plugin-declaration scenario, just with the consuming module gone.

9. **Ninth attempt: close that gap, still as diagnostic.** Removed
   `org.jetbrains.kotlin.jvm`/`org.jetbrains.kotlin.plugin.serialization` from the root
   `build.gradle.kts` (temporarily, alongside the eighth attempt's `:logic` exclusion — restore
   all three together), leaving only `org.jetbrains.kotlin.plugin.compose` declared at root —
   matching the verified-working template's root file, which only ever declared
   `android-application` + `kotlin-compose`, byte-for-byte. While at it, closed the two other
   remaining `settings.gradle.kts` gaps noted since the sixth attempt but never acted on: added
   the template's `google { content { includeGroupByRegex(...) } }` filter (restricts `google()`
   to the artifact groups it's actually authoritative for — `com.android.*`/`com.google.*`/
   `androidx.*` — so Kotlin Gradle Plugin artifacts, not covered by any of those regexes, always
   resolve unambiguously from `mavenCentral()`/`gradlePluginPortal()` instead of potentially
   racing against `google()` first), and applied `org.gradle.toolchains.foojay-resolver-convention`
   (JDK auto-provisioning for `jvmToolchain(...)` requests — present in the template, currently
   unused by `:app` alone, added anyway to close the gap completely rather than partially).

   With this, `settings.gradle.kts`'s `pluginManagement`/`dependencyResolutionManagement`, root
   `build.gradle.kts`'s plugin declarations, and `app/build.gradle.kts`'s `plugins {}` block are
   now a structural match for the verified-working template in every respect except the
   cosmetic one (direct `id(...) version "..."` calls here vs. version-catalog `alias(...)`
   there, which resolve to identical artifact coordinates either way). If the crash *still*
   reproduces after this, multi-module-ness and root-level plugin co-declaration are both
   genuinely ruled out, and whatever's left differs from the template only in namespace/
   applicationId/signing/SDK-level specifics inside `android {}` — none of which should be
   reachable by a crash that happens this early, which would mean the actual cause is something
   about the *local machine* after all (matching Android Studio's own Gradle JDK, or a stale
   Gradle/plugin cache specific to this project's history of having been synced — and failed —
   under half a dozen different AGP/Kotlin/Gradle version combinations already) rather than
   anything left to change in the repository's files.

**Result: the identical crash reproduced.** Same `NoClassDefFoundError: BaseVariant`, same
`initBuiltInKotlinSupport` → `KotlinAndroidTarget` path, `CONFIGURE FAILED in 2s`, on a
single-module build whose plugin declarations, `settings.gradle.kts`, and Gradle/AGP/Kotlin
versions matched the template in every way that had actually been *verified* against the
template's real files. This looked at the time like it closed the repository-configuration side
of the investigation entirely, pointing at local machine/environment state instead: Android
Studio's Gradle JDK setting (was on JBR 25 — an unusually new JetBrains Runtime, switched to
JBR 21), and Gradle caches specific to a project directory that had now been synced (and failed)
under six different AGP versions and four different Kotlin versions in quick succession.

**Both of those were tested and ruled out too.** JDK switched from JBR 25 → JBR 21: identical
crash. All Gradle caches (project-local `.gradle/`, global `~/.gradle/caches`, Android Studio's
own IDE-level cache via "Invalidate Caches / Restart") cleared, project freshly re-cloned into a
brand new directory (`routy-android-2`) entirely: identical crash. At this point a fresh
Empty-Activity template was regenerated *again*, under this now-cleaned environment (post
cache-clear, post-JDK-switch) — **and it synced clean**, immediately. That's the crucial fact:
it rules the environment back *in* as fine, and rules the repository's files back *in* as the
real remaining variable — contradicting the "closed, must be local machine" conclusion above.

10. **Tenth attempt: the actual gap.** Re-reading the previous nine attempts' reasoning turned up
    something that had been asserted but never actually verified: every claim of a "byte-for-byte
    structural match" to the template's root `build.gradle.kts` was based on an *assumption* of
    what that file contained — its literal content was never seen. Only its
    `app/build.gradle.kts`, `settings.gradle.kts`, and `gradle/libs.versions.toml` had been sent.
    This repository's root `build.gradle.kts` had, since the very first M0 scaffold, deliberately
    kept `com.android.application` *out* of the root and declared it only in
    `app/build.gradle.kts` with an explicit version (`id("com.android.application") version
    "9.3.1"`) — reasoning at the time being that AGP is hosted on Google's Maven, and keeping it
    off the root's `plugins {}` block meant `:logic:test` never needed that repository. Standard
    Android Studio templates do it differently: *every* plugin, including
    `com.android.application`, gets declared at the root with `apply false`, then applied *bare*
    (no version) in each subproject that needs it — a different plugin-resolution path than a
    subproject requesting an explicit version directly, even though both resolve to the identical
    final artifact coordinates. This had been the actual, real, unverified gap in every previous
    "matches the template" claim.

    Moved `com.android.application version "9.3.1" apply false` into the root `build.gradle.kts`
    (alongside the already-present `org.jetbrains.kotlin.plugin.compose`), and changed
    `app/build.gradle.kts`'s `plugins {}` block to apply it bare
    (`id("com.android.application")`, no version) — matching the standard template pattern for
    real this time, on the theory that the resolution *path* itself, not just the final resolved
    version, might matter to whatever produces the `BaseVariant` class-generation failure.

**Confirmed fixed.** Android Studio sync succeeded — ten attempts, but the actual cause really
was that specific, previously-unverified difference: `com.android.application` needs to be
declared at the root with `apply false` and applied bare in `app/build.gradle.kts`, not requested
with an explicit version directly in the subproject. Restored `:logic` to the build afterward
(`settings.gradle.kts`'s `include(":logic")`, `app/build.gradle.kts`'s
`implementation(project(":logic"))`, and root `build.gradle.kts`'s `org.jetbrains.kotlin.jvm` +
`.plugin.serialization` declarations — all three had been temporarily pulled for attempts 8-9's
now-resolved diagnostic) — multi-module-ness itself was never the problem, only ever a red
herring the diagnostic had to rule out along the way.

One real, permanent cost of the actual fix: with `com.android.application` now declared at the
root, *every* Gradle invocation in this project — including `./gradlew :logic:test` — needs
Google's Maven to resolve AGP, even though `:logic` itself has no Android dependency at all. That
specifically breaks self-verification in this sandbox (`dl.google.com` blocked), which is why
`:logic:test` was kept working here for as long as it was. CI's `logic-test` job
(`.github/workflows/ci.yml`) is unaffected — `ubuntu-latest` has both a real SDK and full
internet access — so `:logic` is still verified on every push, just not from this sandbox anymore.

## `:logic` — verified via CI, not this sandbox

Plain Kotlin/JVM module, no Android dependency in its own code, builds and tests with plain
`gradle` regardless of SDK availability — but as of the fix above, the *build's* root-level
plugin declarations now require Google's Maven regardless of which module's tests are being run,
so `./gradlew :logic:test` can no longer run in this specific sandbox (see the "confirmed fixed"
paragraph). Last **actually run** here before that: `./gradlew :logic:test` passed, 39 tests,
0 failures — and it continues to run on every push via `.github/workflows/ci.yml`'s `logic-test`
job, which has no such network restriction. Covers:

- `geo/Geo.kt` — haversine distance, bearing, 8-point compass.
- `route/VoiceCueTracker.kt` — the 50m-trigger, sequential-next-station voice cue algorithm
  ported from `RouteGenerator.tsx`.
- `recording/NodeMatching.kt`, `recording/RecordingSession.kt` — candidate-junction matching
  and the recording-wizard state machine ported from `RecordTrackWizard.tsx`, plus
  `shouldRecordPoint()` (the ≥3m GPS dedup filter RecordingForegroundService applies before
  ever calling `RecordingSession.addPoint()`, added for M4).
- `api/*Models.kt` — every request/response shape, traced field-by-field against the actual
  Zod schemas and route handlers in `Emil007/routy` (not guessed) — e.g. `SaveFavoriteRequest`
  was initially modeled wrong and fixed after checking `src/app/api/favorites/route.ts`
  directly; `GpxCommitRequest`'s `start`/`end` union is modeled as one nullable-fields class
  relying on `explicitNulls = false` at the JSON layer, matching the Zod union's two shapes.

If a future change touches the server's API contracts, this is the module to check first —
and it's the one place a broken assumption would actually show up as a red test.

## `:app` — first real compile happened, two real bugs found and fixed

Everything Android-framework-dependent (Compose UI, `Application`/`Activity`, WebView,
manifest, Gradle plugin wiring). After the Gradle sync saga above finally resolved,
`./gradlew :app:assembleDebug` ran for the first time ever — both locally and in CI — and
compilation failed on real, previously-unverifiable-in-this-sandbox errors. Two genuine
dependency-version bugs, exactly the kind this file's original "worth double-checking first"
list predicted, plus a couple of Compose-specific code bugs no amount of source-reading would
have caught without a compiler:

- **`org.maplibre.gl:android-sdk:10.2.0` → `12.3.1`**: every single `org.maplibre.android.*`/
  `org.maplibre.geojson.*` import came back `Unresolved reference`. Root cause: MapLibre
  renamed its entire package from `com.mapbox.mapboxsdk.*` to `org.maplibre.android.*` at
  v11.0.0 — 10.2.0 predates that rename and still ships the old namespace. The code was written
  assuming the *new* namespace (correct for any version ≥11.0.0, wrong for the 10.2.0 actually
  pinned) — grounding the API surface against real fetched source wasn't enough, since the
  specific dependency *version* was never itself verified against what package it publishes.
  12.3.1 picked deliberately as the latest stable release before v13.0.0's Vulkan-becomes-default
  + `GeoJsonSource` sync-API changes, verified by downloading the actual AAR and confirming
  every class this module imports is present in it.
- **`com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0` →
  `com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0`**: `retrofit2.converter.
  kotlinx.serialization.asConverterFactory` unresolved in both `ApiClientProvider.kt` and
  `GithubReleaseClient.kt`, despite the import line being correct and the old artifact
  resolving/downloading fine. That project was folded into Retrofit itself as of 2.10.0 — the
  old third-party artifact apparently doesn't expose its classes to `compileDebugKotlin`
  correctly under this toolchain even though it packages fine (likely a Gradle Module Metadata
  gap; never fully diagnosed since the fix is unambiguous either way). Same package name in the
  new artifact (verified directly against the published jar), so no import changes needed.
- **`compose-bom:2024.12.01` → `2026.06.01`**: fixed several errors outright, but not
  `RouteScreen.kt`'s `NodeDropdown` — bumping the BOM alone left one `Unresolved reference
  'ExposedDropdownMenu'`, at the *import* line specifically, not the actual call site further
  down. Turned out to be a second, more precise version issue layered on top of the first:
  Compose Material3's own release notes confirm `ExposedDropdownMenu` only became a
  top-level-importable *extension function* on `ExposedDropdownMenuBoxScope` in `1.5.0-alpha26`
  (Aug 12, 2026) — current *stable* Material3 is `1.4.0`, where it's still a plain *member*
  function of that same scope, resolved automatically inside `ExposedDropdownMenuBox`'s trailing
  lambda with no import needed at all (and no importable top-level symbol to import in the first
  place). Fix was deleting the now-invalid `import androidx.compose.material3.ExposedDropdownMenu`
  line — the call site itself needed no change. This file's own "worth double-checking" note
  above flagged the exposed-dropdown-menu anchor API as a risk area for exactly this reason and
  turned out to be right twice over, about two different parts of the same API.
- **Two Compose-specific code bugs, not dependency issues**: `RouteScreen.kt`'s station-list
  text called `stringResource()` inside the `transform` lambda of `joinToString` — illegal,
  since that lambda is non-inline (nullable function types can't be inlined) and therefore not a
  valid `@Composable` context, unlike a genuinely-inline lambda (`buildString`/`forEachIndexed`,
  both used in the fix). The same expression also tried to smart-cast a nullable `val` declared
  in a different Gradle module (`:logic`'s `RouteDisplayPayload.viaSegmentName`), which Kotlin
  doesn't allow across module boundaries — fixed by copying it to a local `val` first. Both
  `RouteScreen.kt` and `RecordingScreen.kt` also used `FlowRow` without the
  `@OptIn(ExperimentalLayoutApi::class)` its opt-in-required status still needs.

Given the pattern above — dependency *versions*, not API-surface reasoning, being the actual
recurring bug — the remaining unverified areas below deserve the same skepticism if they turn
out to be wrong: not "the API might not exist," but "the pinned version specifically might not
have it yet."

- **Still worth a second look if something new comes up**: exact artifact coordinates/versions
  for `androidx.security:security-crypto` (still alpha upstream — `1.1.0-alpha06` was current
  when written, may have moved) and `com.google.android.gms:play-services-location`. Neither
  flagged an error on this first compile, but neither had every code path exercised by a
  compiler error the way MapLibre/the retrofit converter did either.
- `Style.getSourceAs<GeoJsonSource>(id)` in `map/RoutyMapView.kt`: confirmed to exist in
  `android-sdk:12.3.1` too, but a known upstream issue throws if called while a *different*
  style is mid-transition — not a concern here since each `RoutyMapView` only swaps styles on
  its own `style` param changing.
- **Found and fixed while writing M3, applies retroactively to M1's `logout()` too**: OkHttp
  throws `IllegalArgumentException: method POST must have a request body` at call time (not
  compile time) for any Retrofit `@POST` method with no `@Body` at all — GET/HEAD prohibit a
  body, but POST/PUT/PATCH require one. `ApiService.kt` now gives every body-less POST
  (`logout`, `completeRoute`, `discardRoute`, `acceptFavorite`, `deleteFavorite`) a shared
  `EMPTY_JSON_BODY` default parameter — the server handlers for all of these never read the
  request body anyway, so it only needs to exist, not mean anything. Worth grepping for this
  pattern (`@POST` with no `@Body`) before adding new endpoints in M4+.
- **Definitely not exercised**: anything requiring an emulator/device — permission dialogs,
  WebView cookie behavior in practice, EncryptedSharedPreferences actually round-tripping on
  a real Keystore, edge-to-edge insets, dark theme.
- **Known minor issue**: every `mipmap-*/ic_launcher*.png` is the same 512×512 export (no
  image tooling was available in the sandbox to generate proper per-density sizes) —
  cosmetically fine since Android downscales it, just larger than necessary in the APK.
  Worth regenerating from `public/icons/icon-512.png` with Android Studio's Image Asset tool
  at some point. `RecordingForegroundService`'s notification also reuses `R.mipmap.ic_launcher`
  as its small icon (`setSmallIcon`) — works, but a proper monochrome/alpha-masked icon is what
  notification icons are supposed to use; some OEM skins may render the full-color launcher
  icon oddly (typically just a white silhouette, not broken, just not polished).
- **Deliberately not ported**: `NamePartsInput.tsx`'s OSM-text/nearby-name-parts autocomplete
  (`POST /api/nodes/suggest-name-parts`) for the "create new junction" fields in both the
  recording confirm step and (if ever added) network editing — the native recording screen's
  part1/part2 fields are plain text entry, no suggestion chips. `recording/RecordingScreen.kt`'s
  candidate-node picker is also a plain expand/collapse list rather than
  `ExposedDropdownMenuBox` (already used once in `RouteScreen.kt`'s `NodeDropdown` — deliberately
  not reused here, to avoid stacking two use sites on the same not-fully-confirmed Material3 API
  in one uncompiled milestone). Both are cosmetic/UX gaps, not functional ones — worth revisiting
  once the app actually compiles.
- **Real limitation, not just an unverified-code risk**: `RecordingForegroundService` holds all
  recorded points in memory only (`:logic`'s `RecordingSession`, never persisted to disk). A
  foreground service is high-priority and Android rarely kills one outright, but if the OS *does*
  kill the process mid-recording (e.g. severe memory pressure) and later restarts the service via
  `START_STICKY`, the restart begins a **new, empty** recording with no warning — everything
  recorded before the kill is silently lost. Given how rarely this actually happens to a running
  foreground service, it wasn't worth adding real persistence (e.g. streaming points to a local
  file/DB as they arrive) to an already-large, entirely uncompiled milestone — but it's the one
  thing here that isn't just "might need a small fix," it's a real edge case to eventually close.

## What's implemented so far (milestones, see the plan for the full M0-M7 list)

- **M0** — Gradle/Kotlin project scaffold, `:logic`/`:app` module split (done specifically to
  work around the SDK blocker — see above).
- **M1** — Retrofit + `AuthInterceptor` (bearer token from `EncryptedSharedPreferences`),
  onboarding (server URL entry, validated via `GET /api/health`), login (username/password +
  TOTP field that appears on `totp_required`/`invalid_totp`, matching the web client's own
  fixed behavior of keeping the field visible after a wrong code), session restore/validation
  via `GET /api/auth/me` on launch.
- **M2** — WebView shell: bottom nav across Route/Map/Stats/Settings/Admin (Admin tab hidden
  for non-admin accounts), the bearer token injected into `CookieManager` as the
  `routy_session` cookie so WebView pages authenticate exactly like a browser tab, and
  WebView navigation to `/login` (the web app's own signal for "session is gone", covering
  token expiry, a device being revoked from Settings, and the web UI's own sign-out link)
  treated as a sign-out signal that routes back to the native `LoginScreen` rather than
  rendering the web login page inside the WebView.
- **M3** — native map + native Route screen, replacing the WebView Route tab (the other four
  tabs are still WebView). Needed one new server endpoint, `GET /api/route/state`
  (`Emil007/routy` PR #22) — same gap as `/api/segments`: the web app gets the active route,
  its nickname, and favorites server-side in `/route`'s RSC, and there was no REST equivalent
  yet. `route/RouteViewModel.kt` ports `RouteGenerator.tsx`'s suggesting/active state machine
  handler-for-handler (generate/widen/adjust/accept/nickname/cancel/complete/discard,
  favorites take/save/delete/share) — same REST calls, Kotlin instead of `fetch`.
  `map/RoutyMapView.kt` wraps a MapLibre `MapView` in Compose: three baked-in raster style JSON
  assets (`app/src/main/assets/styles/*.json`, same OSM/OpenTopoMap/ArcGIS tile sources as the
  web's `MapView.tsx`), with the full segment network drawn faint, the active/suggested route
  highlighted on top, and an optional live-position dot from `FusedLocationProviderClient`
  (foreground-only here — M4 adds the backgroundable version for actual GPS recording). Voice
  guidance's on/off toggle is deliberately not in this screen yet — that's M5.
- **M4** — background GPS recording. Reachable via a new "Record a path" button at the bottom
  of the Route tab's suggesting-mode form. Needed one more server endpoint,
  `GET /api/gpx/config` (`Emil007/routy` PR #22) — same RSC-only gap pattern as M3's
  `/api/route/state`: the web recording wizard gets `merge_radius_m` and the effective walk
  speed as props from the `/map` page's server component.
  - `recording/RecordingForegroundService.kt`: a started+bound `Service`
    (`foregroundServiceType="location"`) holding `:logic`'s `RecordingSession` — bound so
    `RecordingScreen` can drive start/pause/resume/finish/discard and observe live
    phase/points/distance directly, started so it survives the screen (or the whole app)
    backgrounding. Notably, this does **not** request `ACCESS_BACKGROUND_LOCATION` — per
    Android's own docs, a foreground service the user starts while the app is in the foreground
    keeps receiving location after the app backgrounds without it; that permission is only for
    location access initiated by something that isn't already "foreground" (WorkManager, a
    plain background service, etc). The plan document written before M3/M4 assumed that
    permission would be needed — building the service surfaced that it isn't, so it was removed
    from the manifest rather than requested for nothing.
  - `recording/RecordingViewModel.kt` + `RecordingScreen.kt`: the confirm-step wizard, porting
    `RecordTrackWizard.tsx`'s `save()` and endpoint-decision UI (`EndpointFields.tsx`) using
    `:logic`'s already-tested `findNodeCandidates`/`initialEndpointDecision` — same
    existing-node-vs-new-junction choice, same `markStartAsHome` toggle, same
    `POST /api/gpx/commit` payload shape.
  - `map/RoutyMapView.kt` gained an optional `routeColor` parameter (M3 hardcoded the brand
    green) so the recording screen's live track renders in the same reddish-brown
    (`#9a3b29`) the web's `RecordTrackWizard.tsx` uses to distinguish "being recorded" from
    "the network" or "a suggested route."
- **M5** — native voice guidance, wired into the Route screen's active mode as the same on/off
  toggle the web has (next to "Show my location" — voice cues only fire while location is being
  watched, matching `RouteGenerator.tsx`'s own gating). No server changes needed; entirely a
  client-side feature.
  - `route/VoiceGuidance.kt`: `VoiceGuidanceController` wraps Android `TextToSpeech` +
    `AudioManager` audio-focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with
    `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` attributes — the standard turn-by-turn-nav pattern
    for ducking Spotify/music rather than pausing it), releasing focus itself via
    `UtteranceProgressListener.onDone` once each cue finishes speaking. TTS language follows
    `Locale.getDefault()` (the device locale) — same source the rest of the UI's strings follow,
    not the account's server-side locale preference (that's the M7 gap already noted below).
  - `RouteResultCard` in `route/RouteScreen.kt` wires `:logic`'s already-tested
    `VoiceCueTracker` to `uiState.myLocation`, careful about one real Compose correctness
    trap: `VoiceCueTracker.onLocationUpdate()` mutates internal state and must run exactly once
    per genuinely new location fix, so it's called from inside `LaunchedEffect(location,
    voiceActive)`, never directly in the composable body (which Compose can re-invoke for
    unrelated reasons). The resulting cue's spoken text needs `stringResource()`, which only
    works in composable context, not inside a `LaunchedEffect` coroutine — so the cue is staged
    into a `pendingCue` state, resolved to text in the composable body, and *then* handed to a
    second `LaunchedEffect(cue)` to actually call `speak()`. The tracker itself is
    `remember(route.nodeChain)`-scoped, giving a fresh announcement sequence per accepted route
    for free (mirrors the web's explicit `announcedStationIndexRef.current = 0` reset on
    accept/takeFavorite) without needing matching ViewModel-side reset logic.
  - Fixed in passing: `route_station_fallback` (added speculatively back in M1, unused until
    now) held generic placeholder text ("Station") rather than the web's actual fallback
    ("die nächste Station"/"the next station", used when an unnamed station's name is needed in
    a voice cue) — corrected to match `src/lib/i18n/{de,en}.json`'s real `route.station` text.
- **M6** — CI + a signed-release pipeline. **This is the one milestone with a real action item
  for you** — see "Before the first release" below, nothing here works until you do it.
  - `.github/workflows/ci.yml`: two jobs on every push/PR. `logic-test` runs `:logic:test`
    (works anywhere, including this sandbox). `app-build` runs `:app:assembleDebug` +
    `:app:lintDebug` — this is the actual **first real compiler check** all the `:app` code in
    this repo gets, since GitHub-hosted `ubuntu-latest` runners ship a working, pre-licensed
    Android SDK (unlike this project's own dev sandbox, which blocks `dl.google.com`
    entirely — see the top of this file). Whatever CI flags on the first push is the truth about
    everything marked "compiler-unverified" above.
  - `.github/workflows/release.yml`: pushing a tag matching `v*` builds a **signed** release
    APK and attaches it to a new GitHub Release via `softprops/action-gh-release`.
    `versionName`/`versionCode` come from the tag and the run number
    (`-PappVersionName=... -PappVersionCode=...`, read in `app/build.gradle.kts`) rather than
    the placeholders checked into `defaultConfig`.
  - `app/build.gradle.kts` gained a conditional `signingConfigs { release { ... } }` reading
    from a `keystore.properties` file at the repo root (gitignored, never committed) —
    deliberately *not* using AGP's `-Pandroid.injected.signing.*` Gradle-property mechanism
    some CI guides use instead, because I could not pin down its exact property names (they
    vary between guides — `android.injected.signing.key.alias` vs
    `android.injected.signing.store.key.alias`) closely enough to be confident writing it
    blind. `keystore.properties` is the same file-based pattern Android's own official signing
    guide recommends, and every field in it is a plain Kotlin property name I control directly
    rather than a magic CLI flag string — much lower risk for code I can't compile-check.
  - `app/build.gradle.kts` also turned on `buildFeatures.buildConfig` (off by default in
    AGP 8+) so `BuildConfig.VERSION_NAME` is readable at runtime.
  - `logic/update/UpdateCheck.kt` (+ tests) and `update/GithubReleaseClient.kt` +
    `update/UpdateBanner.kt` port the plan's suggested "update available" check
    (`src/lib/updateCheck.ts`'s `parseVersion`/`isNewer`, ported and tested in `:logic`) against
    `Emil007/routy-android`'s own releases instead of the server's. Shown to any signed-in user
    (not admin-gated like the web's version, which checks the *server's* own updates — an
    out-of-date sideloaded APK is everyone's problem, not just the sysop's), as a dismissible
    banner above whichever tab is open. Dismissal is session-only, not persisted.

### Before the first release — what you need to do that I can't

Generate a release keystore and add these four repo secrets (Settings → Secrets and
variables → Actions, on `Emil007/routy-android`) before pushing a `v*` tag — `release.yml`
will fail without them:

- `ANDROID_KEYSTORE_BASE64` — a release `.jks`, base64-encoded (`keytool -genkeypair ...` to
  create one, then `base64 -w0 your.jks` to encode it; keep the original `.jks` somewhere safe
  outside git — losing it means every future release needs a new signing identity, and Android
  won't accept an update signed by a different key over an existing sideloaded install).
- `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` — whatever you set
  when generating the keystore.

Once those exist, `git tag v0.1.0 && git push origin v0.1.0` should produce a signed
`app-release.apk` on a new GitHub Release. First one will also be the first real end-to-end
proof this whole app compiles.

- **M7** — polish. Three of the plan's four items landed; the fourth is a deliberate, documented
  punt (see below) rather than a risky blind change this late in an uncompiled session.
  - `recording/BatteryOptimizationPrompt.kt`: a dismissible banner on the recording screen's
    idle state (shown before the user starts a recording, the moment it matters) when the app
    isn't yet exempted from battery optimization, linking straight to
    `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. This intent is Play Store policy-restricted
    for most app categories, but this app is sideload-only by design, so nothing blocks using
    it — and several OEMs (Xiaomi/Samsung/Huawei/OnePlus) kill even a proper foreground service
    within minutes of the screen going off without this exemption, which is exactly the failure
    mode M4 exists to avoid. Re-checks on every `ON_RESUME` (the settings screen it opens has no
    `ActivityResult` callback to rely on) so the banner disappears once granted.
  - `map/MapStyleSwitcher.kt`: the base-layer picker the plan called out as deferred — a row of
    `FilterChip`s over the same three styles `RoutyMapView` already had (M3), mirroring the
    web's `LayersControl`. Wired into both `RouteScreen`'s and `RecordingScreen`'s map cards;
    each keeps its own `remember`-scoped selection (not persisted across screens or restarts,
    same "polish, not a new subsystem" scope as everything else in M7).
  - **Network-drop retry during recording**: already effectively covered, not a new feature.
    `RecordingViewModel.save()` never clears `points`/`startDecision`/`endDecision` on a failed
    `POST /api/gpx/commit` — only `saving`/`isError`/`messageRes` change — so a network drop at
    the end of a recording leaves the confirm screen exactly as it was, "Save path" still
    enabled, ready to retry with one more tap. No automatic retry/backoff was added on top of
    that: this sandbox can't exercise real network failure conditions to verify one, and the
    existing manual retry already prevents the actual bad outcome (losing a recorded walk to a
    flaky connection).
  - **Locale following the account's server-side setting**: deliberately not implemented.
    Doing this properly needs `androidx.appcompat`'s `AppCompatDelegate.setApplicationLocales()`
    backport (native per-app language support only exists from API 33; this app's `minSdk` is
    26), which in turn typically wants `MainActivity` to extend `AppCompatActivity` rather than
    plain `ComponentActivity` — a real change to the app's Activity base class and possibly its
    theme inheritance (`Theme.Routy` currently parents `android:Theme.Material.Light.NoActionBar`,
    not an AppCompat theme), touching code that's already shipped across every earlier milestone,
    entirely uncompiled, with no way to verify the change doesn't break something else. That
    risk/value trade looked wrong this late in a fully blind session for what's ultimately a
    cosmetic mismatch (the app follows the *device* locale instead of the account's configured
    one — every string and M5's TTS language both do this consistently, just not what the plan
    originally asked for). Left as the one real to-do for whoever picks this up next: add
    `androidx.appcompat:appcompat`, switch `MainActivity` to `AppCompatActivity`, call
    `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(user.locale))`
    once `GET /api/auth/me` returns (`ShellViewModel`/`RouteViewModel` already have `SessionUser.locale`
    on hand), and read the same override in `VoiceGuidanceController` instead of
    `Locale.getDefault()`.

This is the full M0-M7 milestone list from the original plan. Everything is pushed to `main`;
nothing is waiting on a decision from me — the keystore secrets above are the only remaining
blocker, and that's yours to do.

## First things to do in Android Studio

1. Let Gradle sync. Fix whatever it flags — most likely a version bump on one of the
   "worth double-checking" dependencies above. Or just push to `main` and let CI (M6) tell you
   first, if you'd rather not wait on a local sync.
2. Run on an emulator or device, walk through onboarding → login → each tab once, then a short
   real walk exercising Route (accept a route, voice cues, map style switch) and Record (start,
   pause/resume, stop, confirm with both existing-node and new-junction choices, save). Try
   backgrounding mid-recording (screen off, switch apps) — that's the one thing this whole
   rewrite exists to get right.
3. Generate a release keystore and add the four repo secrets above, then tag a release.
