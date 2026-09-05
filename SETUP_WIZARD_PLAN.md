# Plan: OpenSkid first-launch setup wizard

## What it does

On the first launch with the client installed, the main menu offers a one-time Setup screen.
The wizard scans `mods/` and `resourcepacks/`, silently installs must-have entries (OptiFine),
lists the remaining recommended entries with tick boxes, downloads the ticked ones, drops them
in the right folders, activates the chosen packs, then asks for a restart. Never shows again
unless reset.

One fixed list for everyone. No per-user recommendations.

## Input needed (from Dario)

Per entry: display name, one-line why, file name to detect, direct HTTPS download URL, and for
packs the internal `pack.mcmeta` description (used to match an installed pack to its entry).
Mark which entries are `auto` (silent, e.g. OptiFine); the rest are optional tick boxes.
No URLs are invented. Placeholders ship until the list arrives.

## Entry kinds

- `auto` entries: never rendered in the GUI. If missing, they download and install silently in
  the background. Footer status line only (e.g. "Installing OptiFine"), no tick box, no choice.
  OptiFine is detected by prefix match (`OptiFine_1.8.9_HD_U_*.jar`), since upstream renames builds.
- Normal entries: rendered with tick boxes.

## New files (all in `openskid.setup`)

1. `SetupEntry.java` – data holder: section (MODS or PACKS), name, blurb, fileName, url,
   expectedBytes (0 skips the check), packId (packs only, matched against `pack.mcmeta`),
   auto flag.
2. `SetupCatalog.java` – the hardcoded list. Two lists out: mods, then packs. Auto flags set here.
3. `SetupScanner.java` – detects what is already there. Mods: file name in `mods/`
   (case-insensitive), `name (1).jar` duplicates count as present. OptiFine by prefix match.
   Packs: file or folder in `resourcepacks/`, plus `pack.mcmeta` description matching for
   renamed zips.
4. `SetupDownloader.java` – download worker modeled on `OnlineConfigFetcher` (timeouts, user
   agent, max-bytes cap raised for jars/zips, e.g. 64MB). Targets `.minecraft/mods/` and
   `.minecraft/resourcepacks/`. Writes to `name.download` temp then atomic rename, so aborted
   runs never leave half jars. Skips re-download when the file already exists with matching size.
   Two queues: auto starts immediately when the wizard opens (downloads while the user reads),
   optional starts on Download Selected. Runs off the render thread with progress callbacks.
5. `SetupState.java` – first-launch flag. Plain marker file `./config/OpenSkid/setup_done` (not
   inside the main config JSON, so config wipes and profile switches never retrigger the wizard).
   Skip and Finish both write it. A `.setupwizard` chat command deletes it for testing.
6. `SetupScreen.java` – the GUI. Vanilla `GuiScreen` with its own buttons and checkbox rows (no
   dependency on any ClickGUI style): title, two section headers, scrollable entry rows (tick box,
   name, blurb, status: missing / installed / downloading with percent / failed with retry),
   footer with Download Selected, Skip, Select All, progress bar, plus silent-install status line
   for auto entries. Esc closes without marking done only if nothing was installed.
7. `SetupHook.java` – one static call from `OpenSkidMainMenu.initGui()` next to
   `playStartupOnce()`: if the marker is absent, show a "First-time setup" button on the menu
   (button, not forced, so the menu jingle and menu stay intact).

## Resource pack activation (1.8.9 specifics)

- After packs land, update `mc.gameSettings.resourcePacks`: prepend `file/<zip>` entries for the
  ticked packs, preserving existing entries and order. Save with `mc.gameSettings.saveOptions()`.
- Call `mc.refreshResources()` so packs apply without a restart for the pack side. Mods still
  need the restart, which is why the flow ends there anyway.
- Guard: only touch the list on the wizard path, never on normal startup, so user pack order is
  otherwise untouched.

## Finish flow

- When downloads complete: dialog with Restart Now (closes the game via `mc.shutdown()`, user
  relaunches from the launcher) or Later (back to menu, marker written). No auto-relaunch:
  self-restarting Forge in 1.8.9 is fragile and fails differently per launcher. Dialog text
  covers both paths ("Installed OptiFine plus N selected items. Restart to load the new mods.").
- Failures never block: per-entry error with Retry, and a summary line of what installed vs
  what failed.

## Edge cases handled

- Offline first launch: scan still runs, everything shows as missing, download buttons report
  offline, Skip always available.
- Auto download fails: wizard still opens with a footer warning, optional entries unaffected,
  retry happens next launch since the marker is only written on Skip or Finish.
- OptiFine present but older build: any `OptiFine_1.8.9_HD_U_*` counts as present, never
  overwrite the user's own file. No version comparison, too brittle against upstream naming.
- Duplicate jars (`mod (1).jar`): detected as installed, plus one cleanup line offering to delete
  the older duplicate.
- Reopened wizard later: installed entries show ticked-and-present, unticking a pack removes it
  from the active pack list (never deletes user files except the temp downloads).
- Non-zip packs (folders): supported by the scanner, activation uses the `file/` prefix the same way.

## Verification

- Build green, then a scripted pass: fresh `./config/OpenSkid` (marker absent, wizard offered),
  tick all with a local-file test list, confirm files land, packs activate after
  `refreshResources`, marker written, second launch stays quiet, `.setupwizard` reopens it.
- Real second-profile confirmation with the real URLs.

## Build order

Catalog plus state plus scanner, then downloader, then screen, then hook plus command, then the
test pass. Six small units, each ending in a compile.
