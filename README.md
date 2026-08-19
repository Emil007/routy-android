# Routy for Android

Companion app for a **self-hosted [Routy server](https://github.com/Emil007/routy)** — the server is a separate project and **must be running before this app is useful**. Install and run the server first (Docker); then install this APK and enter your server URL at onboarding.

Same household network of paths, same accounts — but with a native map (MapLibre), GPS recording that keeps running while you walk, and cached data when the server is briefly unreachable. **There is no standalone mode:** no cloud backend, no bundled server.

| | [Server (routy)](https://github.com/Emil007/routy) | This app (routy-android) |
|---|---|---|
| Role | Hosts data, web UI, API | Client for phones |
| Required? | Yes — run this first | Optional (browser works too) |
| Version line | **0.33s** | **0.34a** |

---

## Install

**From CI (easiest):** every push to `main` publishes a signed APK under GitHub Actions → latest **Publish APK** run → Artifacts.

**From a tag:** push `v0.34a` (or whatever the tag is) — [release.yml](.github/workflows/release.yml) attaches an APK to the GitHub Release.

**From source:** open in Android Studio, set `sdk.dir` in `local.properties`, run **Run app** or:

```bash
./gradlew :app:assembleDebug
```

Release builds need signing secrets (see [.github/actions/build-signed-apk](.github/actions/build-signed-apk/action.yml)).

---

## First launch

**Prerequisite:** [Routy server](https://github.com/Emil007/routy) up and reachable (see that repo's README for Docker quickstart).

1. Enter your server URL (`https://…` — must match what you use in the browser).
2. If the server has **no users yet**, complete first-time setup (setup token from `docker compose logs`, same as the web).
3. Log in. The app stores a bearer token; your session shows up in server Settings like any other device.

Optional: open a shared route link (`routy://share/…`) — the app accepts it and loads the route.

---

## Tabs (what each one is for)

| Tab | What it does |
|-----|----------------|
| **Route** | Pick start/destination, generate a walk, accept it, follow with map + voice cues, complete or discard. |
| **Map** | View and edit the path network — draw, GPX import, rename/move/split, trash, segment locks, path proposals. |
| **Stats** | Your walks, streaks, achievements, household leaderboards, network usage. |
| **Settings** | Locale, theme, walk speed, avoid list, sessions, link to server-side account security (password / 2FA open in the browser). |
| **Admin** | Full server admin UI in a WebView (admins only). |

Recording lives on the map and route flows: start a track, walk with the screen on (foreground GPS service), then commit endpoints back to the network.

Map base layers match the web (street / hiking / satellite) with an optional **Waymarked Trails** overlay chip.

---

## Offline and updates

Bootstrap data (nodes, segments, your profile) is cached on disk. If the server is down, you can still browse the last sync and see an offline banner; edits wait until connectivity returns.

The app can check GitHub Releases for a newer **Android** tag and show an in-app update hint (optional Sentry crash reporting only if the APK was built with `-PsentryDsn`).

---

## Develop

Two Gradle modules:

- **`logic`** — API models, geo, routing helpers, recording types (unit tests here).
- **`app`** — Jetpack Compose UI, MapLibre, Retrofit, foreground recording service.

Server contract: [routy/docs/API.md](https://github.com/Emil007/routy/blob/main/docs/API.md). DTOs in `logic/.../api/` should match the server — grep the server repo, do not guess.

Gradle/Android Studio history and known device quirks: [NOTES.md](NOTES.md) (long; read when something breaks, not before first build).

---

## Version tags

Android uses tags like **`v0.34a`**. CI `apk-publish` on `main` uses the version in [app/build.gradle.kts](app/build.gradle.kts) (and [apk-publish.yml](.github/workflows/apk-publish.yml)). Tagged releases override via the tag name.

Server version (**0.33s**) is unrelated — deploy server updates on their own schedule. Server repo: [github.com/Emil007/routy](https://github.com/Emil007/routy).
