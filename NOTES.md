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

**Three attempts failed before the actual fix landed** (kept below, struck through in spirit
rather than deleted, because the reasoning in each is real and the eventual fix builds directly
on what they ruled out):

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

**What actually fixed it**: attempts 1–3 all shared one bug, invisible until reading AGP 9's own
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

Rather than juggle a third flag combination, the actual fix is the one
[developer.android.com/build/migrate-to-built-in-kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
describes: **remove `org.jetbrains.kotlin.android` outright** and let AGP 9's built-in Kotlin
support (on by default) own the Kotlin-Android integration — there's no second integration left
to conflict with it, so neither `BaseVariant` nor `BaseExtension` ever needs to resolve. Landed
on:

- AGP → `9.3.0` (current stable as of Aug 2026, per
  [developer.android.com/build/releases/gradle-plugin](https://developer.android.com/build/releases/gradle-plugin)
  — no longer guessing a version, this is the one documented as current).
- Gradle wrapper → `9.7.0` (current stable; AGP 9.3.0's own documented minimum is 9.5.0).
- `org.jetbrains.kotlin.android` **removed** from both `app/build.gradle.kts`'s plugins block
  and the root `build.gradle.kts`'s version declarations — no longer needed or wanted.
- `org.jetbrains.kotlin.plugin.compose`/`.serialization` (still needed as separate compiler
  plugins — the migration guide confirms these aren't part of built-in Kotlin) and `:logic`'s
  `org.jetbrains.kotlin.jvm` → `2.2.10`, the version
  [AGP 9.0's release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
  name as AGP 9's own runtime-dependency floor ("if you use a KGP version lower than 2.2.10,
  Gradle will automatically upgrade your KGP version to 2.2.10") — picked to match what AGP
  itself is built and tested against, rather than the newest available patch.
- `gradle.properties` gained one explicit line, `android.builtInKotlin=true` — redundant with
  AGP 9's own default, but spelled out on purpose: attempt 1 set the *sibling* flag to `false` by
  mistake, and an explicit `true` here makes sure that particular regression can't come back
  silently.
- `app/build.gradle.kts`'s `kotlin { compilerOptions { jvmTarget.set(...) } }` block **kept
  as-is** — AGP's built-in Kotlin support registers the same top-level `kotlin` extension the
  traditional plugin used to, so this needed no changes despite the plugin it used to belong to
  being gone.

Verified for real again after this fourth attempt: `./gradlew :logic:test` passed clean against
Gradle 9.7.0 + Kotlin 2.2.10 in this sandbox (39 tests, 0 failures). Whether AGP 9.3.0's
built-in Kotlin support actually syncs clean in Android Studio is, as always, the one thing only
a real sync (yours) can confirm — this sandbox has never been able to compile `:app` at all. If
this is still wrong, the next thing worth checking is whether Android Studio's own bundled
Gradle/AGP version (visible in its "AGP Upgrade Assistant" or a fresh scratch project) disagrees
with what's pinned here — that would be IDE-side ground truth no amount of documentation
cross-referencing from this sandbox can substitute for.

## `:logic` — fully verified

Plain Kotlin/JVM module, no Android dependency, builds and tests with plain `gradle`
regardless of SDK availability. **Actually run**: `./gradlew :logic:test` passes, 39 tests,
0 failures. Covers:

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

## `:app` — written carefully, compiler-unverified

Everything Android-framework-dependent (Compose UI, `Application`/`Activity`, WebView,
manifest, Gradle plugin wiring). This has never been through `./gradlew assembleDebug` or
any IDE inspection. What to expect on first sync:

- **Likely fine**: package/import structure, Compose code (it's ordinary Material 3 —
  `Scaffold`, `NavigationBar`, `OutlinedTextField`, standard `ViewModel` + `StateFlow`
  wiring via a manual `viewModelFactory` — no exotic APIs). Dependency versions in
  `app/build.gradle.kts` were chosen as well-established stable releases as of this
  writing, not bleeding-edge, specifically to reduce the odds of something having moved
  out from under this.
- **Worth double-checking first**: exact artifact coordinates/versions for
  `androidx.security:security-crypto` (still alpha upstream — `1.1.0-alpha06` was current
  when written, may have moved). `org.maplibre.gl:android-sdk` and
  `com.google.android.gms:play-services-location` are now actually used (M3, see below) —
  their API surface was grounded against real fetched source (MapLibre's own Android test app
  on GitHub) rather than guessed wholesale, but two specific call sites are still worth a
  second look: `Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)` in
  `route/RouteScreen.kt`'s `NodeDropdown` (Material3's exposed-dropdown-menu anchor API moved
  around across 1.2→1.3, the type name may have changed again since `compose-bom:2024.12.01`),
  and `Style.getSourceAs<GeoJsonSource>(id)` in `map/RoutyMapView.kt` (confirmed to exist, but
  a known upstream issue throws if called while a *different* style is mid-transition — not a
  concern here since each `RoutyMapView` only swaps styles on its own `style` param changing).
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
