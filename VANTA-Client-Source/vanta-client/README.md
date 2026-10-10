# VANTA Client — Stage 1

A customizable client experience inside one Fabric mod for **Minecraft Java 1.21.11**:
a launcher-style home screen, a RIGHT SHIFT client menu with search, a module system,
draggable HUD widgets, a theme system shared by every screen, and live performance stats.

## Versions (checked against Fabric's official 1.21.11 template)

| Thing | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.141.6+1.21.11 (required) |
| Java to play | 21 or newer |
| Mappings | Mojang official (same as Fabric API 1.21.11) |
| Build | Fabric Loom 1.18 (`fabric-loom-remap`), Gradle 9.7.1, JDK 25 in CI |

## Getting the .jar (GitHub builds it for you)

1. Create a new GitHub repository (e.g. `vanta-client`).
2. Upload **everything in this folder**, including the hidden `.github` folder,
   `gradle/`, `gradlew` and `gradlew.bat`. (Easiest: unzip, then drag the
   contents into the repo's "Add file → Upload files" page. If `.github`
   doesn't upload on your system, create `.github/workflows/build.yml` by hand
   and paste in the file's contents.)
3. Open the **Actions** tab. The `build` workflow runs automatically on each push
   (you can also start it with "Run workflow").
4. When it finishes with a green check, open the run and download
   **VANTA-Client-1.21.11** under *Artifacts*. Unzip it to get
   `VANTA-Client-1.21.11.jar`.
5. If the run is red, open the failed **build** step, copy the error lines, and
   send them back to get them fixed.

## Installing

1. Install Fabric Loader for 1.21.11 (fabricmc.net/use).
2. Put `VANTA-Client-1.21.11.jar` **and** Fabric API (1.21.11 build) in your `mods` folder.
3. Launch the Fabric 1.21.11 profile. The VANTA home screen appears instead of the title screen.

## Controls

| Key | Action |
|---|---|
| Right Shift | Open / close the VANTA menu (rebind in Options → Controls → Key Binds → VANTA Client) |
| (unbound) | Open the HUD editor directly — set a key in the same place |
| Tab | Focus search in the menu |
| ↑ / ↓ / Enter | Pick a search result |
| Esc | Clear search → leave module page → close |

HUD editor: drag to move, scroll to resize, right-click to show/hide, arrows nudge,
R resets the selected widget, double-click opens its settings.

## What's in this build

- **Home screen** replacing the title screen: sidebar (left/right, labels/icons/hidden),
  Play page, Singleplayer, Multiplayer (vanilla safety warning and account
  restrictions respected), Modules, Customize, HUD editor, Options (video, controls,
  language, accessibility), Mod Menu button if installed, About, **Vanilla menu**
  (always available, for Realms etc.), Quit. Four background modes (vanilla panorama,
  minimal dark, accent gradient, subtle motion), dimming, button size/spacing, logo
  position, welcome text with `{name}`, info cards.
- **Client menu (Right Shift)**: Home dashboard, Modules, HUD, Theme, Settings, About.
  Global search across pages, modules and settings that jumps to and highlights the
  result. Grid or list layout, three densities, favorites, recently changed modules,
  footer explanations for everything you hover.
- **Settings framework**: every setting has a unique id, default, validation, name,
  description and a matching control (toggle, slider with typed number and scroll,
  choice, color swatches with hex entry, text). Per-setting reset, per-module reset,
  reset theme, reset everything (with confirmation).
- **Theme** shared by every VANTA screen: 6 palettes, accent/secondary/background/panel/
  border/text colors, panel opacity, corner radius, interface scale, heading scale,
  menu position/width, sidebar width, background dimming, animation speed/intensity,
  reduced motion, logo and menu-FPS toggles.
- **HUD widgets**: FPS (optional 1% low), frame time (optional worst frame),
  coordinates (optional decimals and Nether/Overworld conversion), direction, ping,
  memory, keystrokes, CPS. Each has scale, background, opacity, text color, accent
  labels, shadow and lock. Positions are stored relative to the screen so they stay put
  at any window size or GUI scale. Hidden widgets are not drawn or updated.
- **Real performance stats**: one timestamp per frame from Minecraft's frame loop,
  fixed-size history, stats recomputed 4×/second: FPS, average frame time, worst frame,
  1% low, memory, and a frame-time graph.
- **Safe settings storage** in `config/vanta/vanta.json`: versioned, human-readable,
  saved automatically (debounced, atomic writes), backup on every good startup
  (`vanta.json.bak`), restore-backup button, broken files copied aside
  (`vanta.json.broken-<time>`) and replaced with defaults instead of crashing,
  invalid individual values reset with a warning on the dashboard.
- **Compatibility detection** for Sodium, Lithium, FerriteCore, EntityCulling,
  ImmediatelyFast, Dynamic FPS, BadOptimizations, Iris and Mod Menu (shown on the
  dashboard and About), plus a warning if a known title-screen mod is installed.
  The home screen can be switched off in Modules → Interface.

## Not in this build yet (next stages)

- Crosshair editor
- Performance presets (Max FPS, Competitive, Recording, Balanced, Potato) and adaptive mode
- Particle and animation controls for the game itself
- Profiles with create/rename/duplicate/import/export
- Theme import/export, custom background images
- Armor status and potion effect widgets, HUD layer ordering

## Tested vs. not tested — honest status

Tested here:
- All VANTA code compiles against stubs of the exact 1.21.11 Minecraft and Fabric API
  signatures, which were checked against Fabric API's 1.21.11 source, Sodium's
  1.21.11 branch, Mod Menu's 1.21.11 branch and Fabric's official 1.21.11 template.
- Automated logic tests passed: save/load round trip, malformed-file recovery,
  invalid-value reset, clamping, step snapping, old-format migration, hex parsing,
  text sanitizing, backup restore, FPS/1% low math, mod detection, favorites sorting.

Not tested yet:
- A real Gradle/Loom build (the build servers weren't reachable from where this was
  written — GitHub Actions does the first real build).
- Launching Minecraft: title screen swap, menus, HUD drawing, and input have not been
  run in-game yet. Testing with/without Sodium, on integrated graphics, multiplayer,
  and window resizing is still to do.

## Privacy and fair play

VANTA works offline, needs no account, sends no telemetry, and stores settings only in
your config folder. It changes visuals and menus only: Keystrokes and CPS display input;
nothing clicks, aims, or changes reach or hitboxes.

## Project layout

```
src/main/java/vanta/
  client/       entry point, version info
  branding/     Theme (shared look for every screen)
  config/       settings framework + ConfigManager (load/save/backup/migrate)
  modules/      Module base, registry, categories, home-screen module
  hud/          HUD widgets, manager, drag-and-drop editor
  gui/          client menu, dialogs, menu items (cards, rows, buttons)
  menu/         VANTA home screen + title-screen hook
  metrics/      frame-time and click statistics
  input/        key bindings
  compat/       installed-mod detection
  ui/           drawing primitives, icons, colors, animation
  mixin/        one mixin: title-screen swap + per-frame timestamp
```
