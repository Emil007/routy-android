# Handover: Routy + Routy-Android (Cloud Agent → IDE Agent)

**Date:** 2026-08-19  
**From:** Cloud Agent run [bc-e378951f-2019-475b-a787-eb019db7ac91](https://cursor.com/agents/bc-e378951f-2019-475b-a787-eb019db7ac91)  
**For:** Emanuel (Operations) — new IDE agent with access to **both** repos  
**User preference:** Server version bumps on every server change (`package.json` → `0.2.0` displays as **0.2** via `src/lib/version.ts`). Separate commits for behavior vs version when possible.

---

## 1. Repos & branches (start here)

| Repo | GitHub | Branch | Status |
|------|--------|--------|--------|
| **routy-android** | `Emil007/routy-android` | `cursor/android-stats-api-me-weekly-ac91` | **Pushed.** Draft [PR #1](https://github.com/Emil007/routy-android/pull/1). CI: `logic-test` green; `app-build` was failing (fix in progress — see §6). |
| **routy** (server) | `Emil007/routy` | `cursor/routy-embedded-userbar-0-2-ac91` | **Local only** on previous cloud VM. **You must push this branch** from your IDE agent clone, then open PR to `main`. |

**Android commits on branch (newest first):**
- `a58d62e` — native Stats tab, bootstrap API, `SessionUser.client`
- `6856ca5` — TTS account locale + native logout icon in shell top bar
- `bb97939` — Stats DTOs + Retrofit endpoints

**Server commits on branch (7 ahead of `main`, newest first):**
- `7cc7e93` — `/api/app/bootstrap`, ETag on nodes/segments, `networkVersion`
- `c58dcbe` — `/api/app/stats/me`, `/api/app/stats/leaderboard/weekly`
- `7a9f563` — Phase 0 hygiene (purge, health, rate-limit generate, quick_check, restore doc)
- `65e5c54` / `ce4aac7` / `1bf5f04` / `000f75a` — layout fix, version 0.2.0, embedded NavBar

---

## 2. Architecture (30 seconds)

- **Hybrid app:** Native Route + Recording + Stats (growing); Map/Settings/Admin stay WebView tabs reusing the web UI.
- **Auth:** Android uses `Authorization: Bearer <token>`; WebView gets the same session via `routy_session` cookie (`CookieBridge.kt`).
- **Embedded mode:** Login sets `session.client = "app"`. Server `(app)/layout.tsx` passes `embedded={user.client === "app"}` to `NavBar.tsx`.
  - **Option B (implemented):** Hide entire userbar (Hello + logout) when embedded **except** on `/settings`. Native logout icon on non-route tabs (`RoutyShellScreen.kt` top bar).
- **Modules:** `:logic` (pure Kotlin, API DTOs, geo, recording, route logic, tests) + `:app` (Compose, MapLibre, Retrofit, services).
- **API contract rule:** Every Android DTO in `logic/.../api/*Models.kt` must match actual server handlers in `routy/src/app/api/...` — grep server first, don't guess.

Read **`NOTES.md`** in routy-android for Gradle saga, compile pitfalls, device-tested bugs, and M0–M7 history.

---

## 3. What’s done (by phase)

### Phase 0 — Server embedded UI + v0.2 + hygiene ✅ (server branch, unpushed)

| Item | Files |
|------|-------|
| Embedded userbar only on Settings | `src/components/NavBar.tsx` |
| Version 0.2.0 → displays 0.2 | `package.json`, `src/lib/version.ts` |
| Activity log retention 180d + expired session purge | `src/lib/purgeSchedule.ts` |
| PRAGMA quick_check on boot | `src/lib/startupChecks.ts` |
| Rich `/api/health` | `src/app/api/health/route.ts` |
| Rate-limit `/api/route/generate` | `src/app/api/route/generate/route.ts` |
| Restore runbook | `RESTORE_FROM_BACKUP.md`, `README.md` |
| LayoutProps TS fix | `src/app/layout.tsx` |

Server tests pass: `npm test`, `npm run lint`, `npx tsc --noEmit`.

### Phase 1 — Native gamification API + embedded shell + bootstrap ✅ (mostly done)

**Server (branch):**
- `GET /api/app/stats/me` — stats, streak, achievements, recent walks (`src/app/api/app/stats/me/route.ts`)
- `GET /api/app/stats/leaderboard/weekly` — weekly leaderboard (`src/app/api/app/stats/leaderboard/weekly/route.ts`)
- `GET /api/app/bootstrap` — user + nodes + segments + routeState + networkVersion (`src/app/api/app/bootstrap/route.ts`)
- ETag / `If-None-Match` on `GET /api/nodes`, `GET /api/segments` (`src/lib/networkVersion.ts`, `src/lib/conditionalJson.ts`)

**Android (branch, pushed):**
- `StatsModels.kt`, `BootstrapModels.kt`
- Retrofit: `appStatsMe()`, `weeklyLeaderboard()`, `bootstrap()`, optional `If-None-Match` on nodes/segments
- `StatsScreen.kt` + `StatsViewModel.kt` — native Stats tab (replaces WebView)
- `RouteViewModel.loadInitial()` uses bootstrap with fallback to 3 legacy calls
- `SessionUser.client` in `AuthModels.kt`
- Native logout in shell top bar; TTS uses account locale tag (partial — full UI locale still device-default; see NOTES.md M7 gap)

**Not done in Phase 1:**
- Persisted ETag cache on Android (headers wired, no disk cache yet — planned Phase 7)
- Full AppCompat locale override for entire UI (NOTES.md documents how)
- Native Map/Settings (still WebView by design for now)

---

## 4. What’s next (your roadmap)

Implement in order. **Deploy server PR before or with Android PR** — Android bootstrap/stats calls need server branch on production/staging.

### Phase 2 — Shared favorite deep links
- Server: ensure `/share/[token]` works for app (may already exist)
- Android: intent filter / deep link handler to open shared route in native Route screen
- Files to inspect: `routy/src/app/share/[token]/page.tsx`, Android `MainActivity.kt`, favorites flow in `RouteViewModel.kt`

### Phase 3 — Native route tracking + gamification (big one)
User wants this to feel like “proving you walked the route”:
- **Track button** when route chosen + GPS active
- **Waypoint progress:** completed vs next station, map markers
- **Sounds** on waypoint arrival (extend `VoiceGuidance.kt` or separate cue player)
- **Achievements/stats** from tracked GPS (server already has `walk_log`, `achievements.ts`, `stats.ts` per user)
- **Save tracked route to history** (likely new server endpoint or extend complete flow)
- **Points:** length, elevation, completed routes, streak multipliers, **leaderboard** (weekly API exists; may need all-time / points formula on server)
- UI: dense, less scrolling — user hates excessive scroll; prefer chips/compact cards like new `StatsScreen.kt`

### Phase 4 — Persist recording sessions (process death)
- `RecordingForegroundService` + `RecordingSession` currently in-memory only (NOTES.md documents data loss on kill)
- Stream GPS points to disk; restore on service restart
- Files: `RecordingForegroundService.kt`, `logic/recording/RecordingSession.kt`, new persistence layer

### Phase 5 — Map + UI usability overhaul
- **Fullscreen map** (Google Maps style) with overlays behind menu button
- Fix zoom/fit: route should zoom to chosen route, not full network
- Fix scroll vs map pan conflicts
- Recording screen usability
- Offline map tiles (major gap)
- Connectivity state UI

### Phase 6 — Recording “paused” behavior
- Define UX when GPS pauses / user pauses / app backgrounds
- Align with foreground service notification state

### Phase 7 — WebView tab state + network resilience
- Per-tab WebView state (back/scroll position)
- Retry/backoff for API calls
- Offline cache for nodes/segments using ETag + local store
- Bootstrap 304 handling on Android

### Phase 8 — Final polish
- UI consistency across native tabs
- Android: icons, crash reporting, widgets
- Server: `routing.ts` tests, API contract generation if desired

### Phase 9 — Product extensions (later)
- Path condition reports, per-user avoid list, surprise suggestions, household leaderboard, automatic path discovery

---

## 5. Server conventions (don’t forget)

1. **Version bump** on every server behavior change → next minor in `package.json` (currently `0.2.0` on branch).
2. **`APP_VERSION_DISPLAY`** trims trailing `.0` — user wants “0.2” not “0.2.0” in UI.
3. **PR workflow:** feature branch → PR to `main`; prefer separate commits for behavior vs version.
4. **Gamification is per user AND per account** — read `src/lib/stats.ts`, `src/lib/achievements.ts`, stats page before adding endpoints.
5. **Embedded predicate:** centralize in `NavBar.tsx` — `showUserbar = !embedded || pathname === "/settings"`.

---

## 6. Known issues / immediate fixes

### Android PR CI failure (fix before merging)
Last `app-build` failed with:
1. `RouteScreen.kt:284` — `accountLocaleTag` used inside `RouteResultCard` without being passed → **fix: add param to `RouteResultCard`**
2. `RoutyShellScreen.kt` — `TopAppBar` needs `@OptIn(ExperimentalMaterial3Api::class)`

Cloud agent applied these fixes locally; **verify CI green after push.**

### Android `.gitignore`
Cloud agent added `routy/` to android repo `.gitignore` so nested server clone isn’t committed by mistake. Server work belongs in **Emil007/routy** only.

### Server branch not on GitHub yet
Previous cloud agent could not push to `Emil007/routy` (403 — run scoped to routy-android only). **Your IDE agent should push `cursor/routy-embedded-userbar-0-2-ac91` immediately** if the branch doesn’t exist on origin yet.

If the branch is missing locally, recreate from the file list in §3 or cherry-pick from cloud agent transcript.

---

## 7. Key files cheat sheet

### Server (`routy`)
| Area | Path |
|------|------|
| Nav / embedded | `src/components/NavBar.tsx`, `src/app/(app)/layout.tsx` |
| Session / client | `src/lib/session.ts`, `src/app/api/auth/login/route.ts` |
| Stats / achievements | `src/lib/stats.ts`, `src/lib/achievements.ts` |
| Native APIs | `src/app/api/app/**` |
| Nodes/segments ETag | `src/app/api/nodes/route.ts`, `src/app/api/segments/route.ts` |
| Route state | `src/app/api/route/state/route.ts` |
| Version | `package.json`, `src/lib/version.ts` |

### Android (`routy-android`)
| Area | Path |
|------|------|
| Shell / tabs | `app/.../webview/RoutyShellScreen.kt`, `ShellViewModel.kt` |
| Route native | `app/.../route/RouteScreen.kt`, `RouteViewModel.kt` |
| Stats native | `app/.../stats/StatsScreen.kt`, `StatsViewModel.kt` |
| Recording | `app/.../recording/*` |
| Map | `app/.../map/RoutyMapView.kt` |
| API | `app/.../core/network/ApiService.kt`, `logic/.../api/*Models.kt` |
| DTOs | `logic/src/main/kotlin/com/routy/app/logic/api/` |

---

## 8. Testing checklist

**Server (after push):**
```bash
cd routy && npm ci && npm test && npm run lint && npx tsc --noEmit && npm run build
```

**Android:**
```bash
cd routy-android && ./gradlew :logic:test :app:assembleDebug :app:lintDebug
```

**Manual (device):**
1. Login → Route tab loads (bootstrap or fallback)
2. Stats tab shows native screen (needs server deployed)
3. Map/Settings still WebView, no duplicate top nav
4. Logout icon on Stats/Map/Settings works (`POST /api/auth/logout`)
5. Settings WebView still shows server logout on `/settings`
6. Voice guidance language matches account locale

---

## 9. User context (important)

- **Emanuel** prefers working directly in code; wants **usable UI** (dense info, less scroll), not fancy oversized buttons.
- **Map experience** on Android is a pain point — fullscreen map with overlays is high priority (Phase 5).
- **Tracking/gamification** is the product heart: prove you walked the route, waypoints, sounds, points, leaderboard, history.
- Switched from Claude due to credit limits; frustrated when agents forget server version bumps.
- GitHub App has both repos; android cloud runs may still be single-repo scoped — IDE agent with both repos should push server directly.

---

## 10. Suggested first actions for you (IDE agent)

1. **Clone/checkout both repos** on branches above.
2. **Push server branch** `cursor/routy-embedded-userbar-0-2-ac91` → open **draft PR to `main`** on `Emil007/routy`.
3. **Fix Android CI** if still red → update PR #1.
4. **Merge server first** (or deploy branch to staging) so Stats/Bootstrap APIs exist.
5. **Continue Phase 2** (share deep links) unless Emanuel reprioritizes.

Good luck — the hard Gradle/device debugging is documented in `NOTES.md`; you're building features on a working base.
