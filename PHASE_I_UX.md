# Phase I — Usability / Information Density

Gold standard: `StatsScreen.kt` — chips, tight spacing, primary facts visible without scrolling.

## Route — Suggesting (no active route)

```
┌──────────────────────────── MAP (full bleed) ────────────────────────────┐
│ [offline] [map style]                                                   │
│                                                                         │
│                                                                         │
├──────────────────────── FIXED STRIP (no scroll) ────────────────────────┤
│ Start ▾          ☐ Loop   ☐ Explorer                                    │
│ [Suggest] [Short] [Long]  [Record track]                                │
│ Favorites & more ▾                                                      │
└─────────────────────────────────────────────────────────────────────────┘
     └─ expanded sheet (scroll OK here): favorites list, destination, waypoint
```

**Android:** bottom `Surface` strip + optional expandable card above it.  
**Web:** desktop = map + side column; mobile = map + sticky bottom toolbar; favorites in `<details>`.

## Route — Preview (suggested route, not yet active)

```
┌──────────────────────────── MAP ─────────────────────────────────────────┐
│ [3.2 km] [45 min] [↗120m]                                               │
│ [Shorter] [Longer] [New] [Accept]                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

## Route — Active

```
┌──────────────────────────── MAP ─────────────────────────────────────────┐
│ [3.2 km] [45 min]   (optional: 3/12 → Next station)                     │
│ [Location] [Track] [Complete] [Discard]                                  │
│ Nickname [____] Save   Favorite [____] Save   ☐ Voice  ☐ Screen         │
└──────────────────────────────────────────────────────────────────────────┘
```

Max **two** action rows + chip row. No overlay scroll.

## Recording — Live

```
┌──────────────────────────── MAP (fullscreen) ────────────────────────────┐
│ ← back    [map style]                                                   │
│                                                                         │
│ ┌────────── floating bar ──────────────────────────────────────────────┐ │
│ │ 42 pts · 1.2 km  │  [Pause] [Stop]  (or Resume/Stop/Discard)        │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## Recording — Confirm

```
┌──────────────────────────── MAP (track overlay) ─────────────────────────┐
│ ← back    [map style]                                                   │
│ ┌──────── confirm bar (one viewport, no scroll) ────────────────────────┐ │
│ │ Start [Existing|New] ▾   End [Existing|New] ▾   ☐ Home               │ │
│ │ [Save track] [Discard]                                                │ │
│ └───────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

Endpoint fields compact (labelSmall, single-line dropdowns). New-junction names on one row.

## Done when

Primary Route + Recording actions visible **without scrolling** on a normal phone (~640×360 logical).  
Any remaining `verticalScroll` on map overlays must be justified (expanded favorites sheet only).
