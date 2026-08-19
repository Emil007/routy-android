# Status notes for whoever opens this next (probably future-you, in Android Studio)

This file exists because most of this repo was written in a sandbox with **no Android SDK
and no way to install one** — the sandbox's outbound network policy blocks `dl.google.com`
entirely, which backs both the `google()` Gradle repository and every `androidx.*`/Play
Services artifact. So: some of this is real, tested code, and some of it is careful,
traced-against-the-server-source Kotlin that has never been compiled. This file says which
is which, so the first Android Studio sync isn't a surprise.

## `:logic` — fully verified

Plain Kotlin/JVM module, no Android dependency, builds and tests with plain `gradle`
regardless of SDK availability. **Actually run**: `./gradlew :logic:test` passes, 30 tests,
0 failures. Covers:

- `geo/Geo.kt` — haversine distance, bearing, 8-point compass.
- `route/VoiceCueTracker.kt` — the 50m-trigger, sequential-next-station voice cue algorithm
  ported from `RouteGenerator.tsx`.
- `recording/NodeMatching.kt`, `recording/RecordingSession.kt` — candidate-junction matching
  and the recording-wizard state machine ported from `RecordTrackWizard.tsx`.
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
  at some point.

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

## Not started yet

M4 (background recording foreground service), M5 (native voice guidance — the ported
algorithm in `:logic` is ready, just not wired to `TextToSpeech`/`AudioManager` yet), M6 (CI +
signed releases — needs a keystore you generate and add as repo secrets, not something I can
do), M7 (polish: battery-optimization prompt, network-drop retry during recording, locale
following the account setting, base-map-style switcher UI for the native Route screen — the
three style assets exist but `RoutyMapView` is currently hardcoded to `BaseMapStyle.STREETS`).

## First things to do in Android Studio

1. Let Gradle sync. Fix whatever it flags — most likely a version bump on one of the
   "worth double-checking" dependencies above.
2. Run on an emulator or device, walk through onboarding → login → each WebView tab once.
3. From there, M3 onward can continue.
