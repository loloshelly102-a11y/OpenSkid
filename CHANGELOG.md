# OpenSkid changelog

## v2.1+4, OpenSkid rename.
Renamed the whole client from Myau to OpenSkid: package openskid, modid openskid,
rebranded GUI styles (Legacy, Legacy+, Nova, Cards), sound sets (Skid, Ghost, Macro),
animation and HUD style names, watermark art, capes, config folder, docs.
Added 6 modules from donor research: Freecam, Spider, AutoRespawn, BurstClicker,
ArmorBreaker, EntityCulling. Now 201 modules, every one with a hover tooltip in all
6 ClickGUI styles. First public repo release.

## Earlier history (as PerfectClient, kept as-is).
Base: OpenMyau-Plus 2.1.4, 115 modules. Now: 158 modules plus new managers and services.
Format per entry: Added, Rewrote, Fixed, Removed.

Phase 0, hygiene.
Added Hotbar to the ClickGUI. It was registered but missing, which crashed the GUI on open.
No removals. Baseline jar saved before any change.

Phase 1, config core.
Removed FileProperty. It had zero users and never saved.
Fixed property lookup and the chat module command to answer cleanly on bad names instead of crashing.
Verified mode options save by name, so option order changes never break configs.

Phase 2, combat.
Added 6 Velocity modes: Intave, GrimAC, Matrix, PolarJump, Legit, Delay.
Added 3 KillAura autoblocks (GrimAC 1.8 and 1.12, Watchdog) plus Polar and Intave rotations plus KeepRange, SmoothAim, and gapple pause.
Rewrote Criticals from 11 to 15 modes with Hypixel, Lag, Matrix, and Timer.
Added LowCPS and Record clicker modes, Manual and Legit BlockHit, SprintReset and MoreKB WTap.
Fixed the new aura target getters and a Velocity field clash at integration.

Hotfix, invisible options.
Fixed Modern GUI showing only the keybind row. It looked settings up by class instead of by instance.

Phase 3, lag core.
Added LagCore hub with inbound plus outbound queues, per-owner hold and release, and server position tracking. Blink, Lag, and Delay managers now delegate to it.
Rewrote BackTrack with a new BUFFER mode, LagRange with real-position box and flush gates, FakeLag with PULSE mode, Blink with HOLD mode.
Rewrote Displace scan as tunable sliders (rings, dirs, range, fade) routed through the hub.
Skipped TPAura on purpose. Raw teleport is a ban magnet.

Phase 4, movement plus ghost.
Added Speed modes (Polar, Vulcan, GrimAC, Intave, BlocksMC), NoSlow variants (GrimAC, Hypixel, Intave, OldGrim, Vanilla), LongJump modes (GrimBoat, GrimVelocity, Hypixel, Fireball, Vulcan), Sprint Legit/Omni plus bypass, Timer hop and balance modes.
Rewrote Fly to 11 modes, Jesus to 4, NoFall with Legit/Vulcan/Matrix, AntiVoid to 7.
Added BridgeAssist, Clutch, SmartClicking, Phase, and Step as new modules.
Expanded Eagle, SafeWalk, and NoJumpDelay with edge and timing options.
Fixed every GUI style listing the new modules, after finding each style keeps its own list.

Fixes round, from live testing.
Removed Eagle, merged into BridgeAssist with its sneak ticks and direction, jump, pitch, and sneak-only checks.
Removed the old Clutch, it aimed but never placed.
Added WaterClutch (bucket MLG with pickup) and LadderClutch (block and wall placement) with real hotbar swap, right-click, and swap-back.
Rewrote Fly AirPlace to place blocks below while gliding.
Kept HitSelect and SmartClicking separate after proving they work on different layers.
Deferred true Vulcan, Matrix, Bow, and TNT fly ports. They need a collision event that does not exist yet.

Clutch port plus key.
Added Clutch as its own module from Raven bS-16: block scoring, fall prediction, smooth aim, alignment-gated placement, slot save and restore, auto-clutch on hurt, aim snapback.
Added clutch-key hold-to-arm. Empty means the sneak rule decides.

Phase 5, world.
Rewrote Scaffold from one 1319-line file into session, planner, executor, and rotation helpers with identical behavior, plus BlockIn surround option.
Added Tower with 18 modes plus TunnelEngine auto-tunneling with stuck, back, and turn states, plus slave-to-Scaffold.
Added protect awareness to BedNuker, tiers plus swap-back to AutoBedDef, and new BedDefender.

Phase 6, disablers.
Added Disabler preset selector with Custom default plus Polar, Intave, GrimExtreme, GrimSpec, Mineland, Hypixel, HypixelMotion, KKCraft, and BlocksMC. Old toggles untouched. All presets untested on live servers.
Added ModSpoofer, ExploitFixer, AntiFalseFlag, GhostBlock, and PingSpoof.

Phase 7, safety.
Added CheatDetector with 8 per-player violation checks, decay, and chat or report alerts.
Added StaffDetector with tablist, chat, and vanish watch plus optional auto-leave.
Added MurderDetector with item plus chat role hints.
Added BedProximityAlert with anchor, radius, and cooldown.
These are heuristics. Expect false positives in messy fights.

Phase 8, visuals.
Added BedPlates, FKCounter, ClosestPlayerHUD, LeapModeHUD, MegaWallsDetector, and FireBallPredict overlays, plus approaching filter on Indicators.
Added 3 TargetHUD styles and Jello, Raven, Vape TargetESP styles.
Added DamageTags, TNTTimer, and PotionHUD.
Added ClickGUI frame cap, lowercase, and team theme options.

Phase 9, minigames plus misc.
Added AutoRequeue, AutoWho, BedWars alerts, BridgeInfo, DuelsStats, MurderMystery, and SumoFences.
Added AutoPlay, AutoGG, AutoReconnect, Panic, KillSults, KillMessage, ViewPackets, AutoBuy, PartyDetector, and PlayerList.
Skipped SkyWars, WoolWars, SpeedBuilders, SkywarsAlerts, and ThePitUtils for a later round.

Phase 10, accounts plus scripts.
Added AltManager with cracked and Microsoft device-code login, local store, ban tracker, alt screen, and .alts command.
Added script runtime locked to local files with a string-only API and a written threat model. Only load scripts you wrote.
Added shareable configs with applied-versus-failed counts plus .onlineconfig and .userconfig.

Backlog round, ten auditors plus seventeen builders.
Fixed crash and leak class: click assist init, BlockHit roll, suffixes, Criticals arming, WTap gate, hotbar range, movement timer restores, void cache and predicate, KeepSprint handler, clutch sneak/pitch/aim/slot states, MCF bind, SmartClicking rate, HUD null guards, Ambience gating, Xray cap, Spammer floor, ViewPackets buffer, FullBright expiry, FreeLook restore, AutoAuth once-per-world, DelayManager release, disconnect release-all, FakeLag send-through, JNDI brand removal, IntaveFly drain, detector thresholds, NoRotate gate, Denick guards, flag throttle plus resume, MurderDetector cross-check, NoClickDelay port.
Fixed stability class: hub synchronization, bypass queue routing, timeout watchdog plus caps, BackTrack allowlist, Stasis rebuild, TickBase reentrancy, BedNuker restore, shared slot helper, ChestAura yield, Scaffold-Tower arbitration, AutoBlockIn gate, render caps and hoists, DynamicIsland cost, chat gating, reconnect deny-list, BedTracker single owner, ExploitFixer scope, PingSpoof warning, BedDefender unify note, TargetHUD unify, slot helper, WaterMark cleanup, SafeWalk authority note, strafe helper note, WTap note, NoRotate, Denick, MurderDetector.
Added SkyWars, WoolWars, SpeedBuilders, SkywarsAlerts, ThePitUtils, AutoRod, Nuker, AutoPlace, LegitScaffold, NoClickDelay, BlockLadder, AutoBed, Arrows, BlocksESP, MobESP, ItemTags, FallIndicator, InventoryHUD, and KeyStrokes.
177 modules registered. Clean build green.

Skid round, four scouts plus six porters.
Added combat AutoBlock with NoSlow and smart-unblock integration.
Added rod aim to AutoRod with FOV, prediction, teammate filter, and NewPacket mode.
Rewrote BedNuker with wired Instant break, cover-first, spawn anchor, pair validation, and KillAura yield.
Added JumpReset with Standard and Polar modes plus reduce and pause logic.
Added 8 Velocity modes (Tick, Zip, Karhu, MatrixReverse, MatrixFull, Intave14, XZSwitch, OldGrim), now 18 total.
Added AutoWeapon with scored selection, plus registered the unregistered AutoArmor, AutoPot, and AutoSoup.
183 modules registered. Clean build green.

Visuals round, user-approved picks.
Added Glow outline style to TargetESP and ESP with passes, width, and expand sliders.
Added KillEffect with lightning, sound, and particles on kills.
Expanded ViewClip with camera distance, no-clip intent, and fall tilt plus dolly. Fall math lives in a plain helper, not a dead module.
Modern GUI gained row hover fade, frame hover fade, open slide, scroll easing already present, search bar filtering by name, and hover descriptions with 400ms delay.
184 modules registered. Clean build green.

Aim round, user-approved picks.
Added ForwardTrack spacing tracker, KeepRange standalone keeper, SmartBlinker gated auto-blink, and LegitReach subtle control.
Added ProjectileAimBot with KillAura and Closest modes plus prediction.
Added shared aim helpers (aim at target, line-of-sight check, motion lead) to RotationUtil.
Upgraded ThrowAura with Aimed mode using the shared helpers. HitSelect gained Move-speed, KB-reduction, and Critical-hits presets.
189 modules registered. Clean build green.

Approved extras round, user-picked.
Added LagRange Repel mode: holds packets inside range, dumps the burst on distance, timeout, or your own hit.
Added AutoClicker Extra and Extra+ tiers: gaussian shaping plus micro-breaks, then CPS drift plus fatigue pauses. Emulation only.
Added PearlSaver: auto pearl at the nearest surface on void falls, one throw per fall.
Finished NoHitDelay: hit-only click-delay removal gated on crosshair target.
190 modules registered. Clean build green.

Advanced round, user-picked.
Added KillAura humanizer (miss chance, target-switch pause) and Threat target sort (weapon, armor, missing health).
Added NoWeb with Normal and Sprint web-escape modes.
Added Parkour with ledge jump and sprint options.
Added loot humanizer toggles to ChestStealer and InvManager.
Added Scaffold place jitter timing.
192 modules registered. Clean build green.

Sound plus capes round, user-picked files.
Added startup sound playing once at launch.
Added toggle sounds with Sigma, Rise, and QuickMacro sets plus 0 to 100 volume at 50 default. Replaced the old vanilla click.
Added KillSounds with seven selectable kill sounds plus volume.
Bundled 49 capes from both Ravens plus the minecraft-capes folder, picked from the existing Capes module.
193 modules registered. Clean build green, jar 28MB.

Combat depth round, user-picked.
Added BlockHit Combo mode (blocks inside trade windows) and FakeBlock mode (client-side pose, placements never leave).
Added AutoClicker double-click emulation plus fatigue scheduler (CPS sags in long fights, recovers on pause).
Added MoreKB LegitSneak, Fast, LegitBlock, LegitInv, and STap modes.
Added BridgeAssist Telly mode with jump rhythm timing.

Repel plus click humanization, user-asked.
Added LagRange Repel mode: holds packets while the opponent is inside range, releases the burst on distance, timeout, or your own hit for double hits.
Added AutoClicker randomization tiers: Normal untouched, Extra with gaussian shaping plus micro-breaks, Extra+ with CPS drift plus fatigue pauses. Emulation only, proves nothing against model-based checks.
