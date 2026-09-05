# OpenSkid

Forge 1.8.9 ghost client. 201 modules, 6 ClickGUI styles, hover tooltips on every module, toggle/kill/startup sounds, 49 built-in capes, script support.

![Preview](/images/image3.png)

## Install

1. Install Forge 1.8.9.
2. Drop `build/libs/OpenSkid.jar-2.1+4.jar` (or the latest release jar) into `.minecraft/mods`.
3. Launch. Right Shift opens the ClickGUI.

## Features

- Combat, movement, render, minigame (BedWars, SkyWars, Duels, Pit, WoolWars, Murder Mystery and more), and utility modules.
- Every module shows a one-line description on hover in all six GUI styles.
- Notifications with selectable toggle-sound sets, KillSounds, main-menu startup jingle.
- Cape picker with 49 bundled capes plus custom PNGs from the `keystrokes/customCapes` folder.
- Online configs and a script loader (see below).

## Building

Requires JDK 17 to run Gradle (the mod itself targets Java 8):

```bash
export JAVA_HOME=/path/to/jdk17
bash gradlew clean build --console=plain
```

Output: `build/libs/OpenSkid.jar-2.1+4.jar`.

## Scripting

Script support comes from [`rsl/`](rsl/), a vendored copy of Raven Script Loader, bundled into the
client jar. One jar registers two Forge mods (`openskid` and `rsl`) under
`mixins.openskid.json,mixins.rsl.json`. See [`rsl/UPSTREAM.md`](rsl/UPSTREAM.md) for provenance.

## Disclaimer

Cheating violates most servers' rules, including Hypixel's, and can get accounts banned.
Use at your own risk, preferably never on an account you care about.

## License

GPL-3.0, see [LICENSE](LICENSE).
