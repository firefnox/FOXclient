# TESTING — FoxClientv2.0 (build 3.4.0, Minecraft 26.1.2)

Nothing in this list can be verified by a compiler: these are the checks that need a real
game launch. Work top to bottom — each block assumes the previous one passed.

Test setup: **singleplayer, creative, a fresh world**. Never test a render mod on a server you
care about.

---

## 0. Install

1. Fabric Loader 0.20.1+ on Minecraft 26.1.2, Fabric API 0.155.3+26.1.2 in `mods/`.
2. Drop `foxclient-1.4.0.jar` in `mods/`.
3. Launch, open the log.

**Pass:** these lines appear, with no `Mixin apply failed` and no crash:

```
[FoxClientv2.0] initialising
[FoxClientv2.0] ready — 7 modules, ClickGUI Right Shift, panic END, render backend GIZMOS
```

If it says `render backend LINES` or you see
`gizmo backend unavailable ... falling back to depth-tested lines`, the through-wall features
will not show through terrain — note it and continue, everything else still works.

---

## 1. ClickGUI (liquid glass, centred)

| Step | Expected |
|---|---|
| Press **Right Shift** | Centred glass panel opens over a blurred world; game keeps running |
| Look at the panel edges | Rounded corners, hairline border, specular top edge, soft shadow |
| Click the search field, type `esp` | List filters live; caret blinks; ESC clears focus |
| Type nonsense (`zzz`) | `no match` hint, no crash |
| Click a module name | Toggle switch slides, module appears in the top-left HUD list |
| Right-click a module name | Settings panel opens on the right |
| Drag a slider | Value updates live while dragging |
| Click an enum row | Cycles forward; right-click cycles back |
| Click a colour row | Cycles through 10 presets, swatch updates |
| Click a list row (`Blocks` / `Entity types`) | Picker opens |
| In the picker: type `diamond` | List filters live |
| Click an entry | Toggles (orange bar appears), count in the header increases |
| Scroll | List moves; ESC returns to the ClickGUI, not to the world |
| Press **Right Shift** again | GUI closes |
| Press **END** | Everything disables at once, log shows `panic key: N module(s) disabled` |

## 2. Config persistence

1. Enable BlockRadar, change Range to 160, add `minecraft:iron_ore`, close the GUI.
2. Wait ~1 second (writes are debounced), then quit the game cleanly.
3. Check `config/foxclient/config.json` exists and shows your values.
4. Relaunch.

**Pass:** module is still enabled, range still 160, block list still has iron ore.

**Corruption test:** with the game closed, replace `config.json` contents with `{{{`.
Launch → the file is moved to `config.json.bak-<timestamp>`, a warning is logged, and the client
starts on defaults instead of crashing.

---

## 3. Fullbright

| Step | Expected |
|---|---|
| Go to a dark cave, note the darkness | Baseline |
| Enable Fullbright | World gets bright, no green night-vision tint, no HUD effect icon |
| Strength slider → 0.5 | Bright, but dimmer than full |
| Disable | Returns to exactly the vanilla darkness |
| Toggle it 10x rapidly | No stuck brightness, no flicker after the last toggle |
| Toggle it while in a boss fight | No darkening overlay while enabled |
| Check Options → Video → Brightness | Your slider is untouched (we do not modify the gamma option) |

---

## 4. BlockRadar

Setup: `/setblock ~3 ~ ~3 minecraft:diamond_ore`, another 20 blocks away, plus a chest.

| Step | Expected |
|---|---|
| Add `diamond_ore` + `chest` in the picker, enable BlockRadar | Boxes appear around both |
| Range 96, stand still | First boxes appear within ~1–2 s, then fill in (scan is budgeted) |
| HUD line | `Blocks: N` and `(scanning)` while it works |
| Look at a box from the other side of a wall | Visible through terrain (gizmo backend) |
| Enable Tracers | Lines from your eyes to each box |
| Set Max rendered to 100 in an ore-rich area | Boxes cap at 100, FPS stays smooth |
| Set Range to 16 | Ripple: hits outside range stop drawing |
| Walk 300 blocks away and back | Old hits are dropped, new ones found |
| Force a chunk unload (`/tp` far away) | No stale boxes left behind, no memory growth |
| Disable | Everything disappears immediately |

**Performance gate:** with 500+ hits rendered, FPS drop should stay under ~5%. If not, lower the
scan budget first, then the range.

## 5. ESP

| Step | Expected |
|---|---|
| In a village, add `minecraft:villager` to entity types | Villagers boxed, others not |
| Add `minecraft:player` and join LAN with a second account | That player is boxed |
| Type their name into `Friends` | Box and tracer disappear for that player only |
| Walk sideways around a mob | Box follows smoothly, no 20 Hz stutter |
| Enable Tracers | Lines end at each box centre |
| HUD target list | Shows up to 8 entries, nearest first, `name hp ❤ distance m` |
| Range 8 | Only adjacent entities remain |
| Enable Skip invisible, then `/effect give @e invisibility` | Invisible entities stop rendering |
| Disable | All boxes gone |

## 6. Sound Radar

Setup: place a piston clock (`/setblock` + lever) about 30 blocks away, behind a wall.

| Step | Expected |
|---|---|
| Enable Sound Radar, HUD compass on | Strip at the top centre reads `sounds: N` |
| Trigger the piston | Ticks appear on the strip toward it; markers appear in the world |
| Wait ~6 s | World markers fade out (marker lifetime) |
| Keep the clock running | A `BASE? N% ↑ 30m period 20t ...` line appears in the cluster list |
| Turn 180° | Arrow flips to point behind you, ticks sweep across the strip |
| Stop the clock, wait 60 s (window) | Cluster decays and disappears |
| Open a chest next to you | A CONTAINER-adjacent event appears but confidence stays low (isolated event ≠ base) |
| Build two piston clocks 100 blocks apart in opposite directions | Two separate clusters, opposite arrows, both with periods |
| Play for 10 minutes | `Max events` never exceeded (default 20000), RAM stable, audio unchanged |
| Confirm audio | Pistons and every other sound still play exactly as vanilla — we only read packets |

## 7. Freecam

| Step | Expected |
|---|---|
| Enable Freecam | Camera stays where it is (no snap) |
| Hold W | Camera flies forward; **your body also walks** — this is the documented limitation |
| Hold space / shift | Camera rises / sinks |
| Hold sprint + W | 2.5x faster |
| Fly 200 blocks away | Terrain around the camera is loaded and visible (frustum re-captured, no holes) |
| Look around | Camera rotation still follows your player's view |
| Disable | Camera snaps back to your body instantly |
| Fly, then press END | Freecam disabled, camera restored |

## 8. Waypoints, Info

| Step | Expected |
|---|---|
| Press **B** | Log line `waypoint added at …`, a teal box + beam appears where you stood |
| Enable Waypoints | HUD line: `↑ wp1 34m`, arrow flips when you turn around |
| Walk 100 blocks away | Beam still visible (through terrain), distance updates |
| Enable Info | `xyz …`, `<fps> fps`, dimension id under the module list |
| All 5 HUD modules on | Lines stack, none overlap |

## 9. Sound Radar — noise rejection

| Step | Expected |
|---|---|
| Stand near water with fish, or in rain | **Nothing** appears on the compass (`sounds: 0`) |
| Open a chest yourself | Nothing recorded (inside the 4 block self radius) |
| Trigger a piston 30 blocks away | Recorded, tick on compass, `MECH` in the cluster line |
| Break stone 20 blocks away | Recorded as `PLACE`, low confidence on its own |
| Open a chest 20 blocks away (second account) | `CONT` vote, higher confidence |
| Check cluster line | Reads like `↑ BASE? 62% 30m period 20t MECH+PLACE piston.extend` |

---

## 9b. World render — the 1.1.0 corruption fix (do this first)

1. Enable BlockRadar with `diamond_ore`, ESP for players, Waypoints, Sound Radar.
2. Fly or walk around for 30+ seconds, day and night, surface and underground.
3. **Pass:** boxes/lines/points appear through terrain and the world is untouched. No giant
   flat triangles, no garbage lines across the sky or ground, no flicker beyond the boxes.
4. **Pass (window retry):** force the failure case by disabling whatever binds the gizmo
   collector (easiest: `grep BEFORE_GIZMOS` a debug build, or simply watch the log on a hot
   reload). Log shows exactly one
   `no gizmo collector in BEFORE_GIZMOS — moving world drawing to END_MAIN` line, and boxes
   either keep drawing or stop drawing. **The world must never corrupt in either case.**
5. Also check the panel is open while boxes draw — the glass panel must stay opaque-ish and
   the world behind it blurred, not black.

## 9c. Per-block colours (BlockRadar)

1. BlockRadar → right-click the module → click the **Block colours** row.
2. The editor lists every tracked block with a swatch and `#RRGGBB`.
3. Left click a row: swatch steps to the next preset and the **box in the world changes colour
   immediately**.
4. Right click a row: swatch returns to the module colour and the row text goes dim again.
5. Close and reopen the GUI, then restart the game: overrides must persist in
   `config/foxclient/config.json` under `block_colors` (a JSON object of id → integer).
6. Hand-edit the config to `"block_colors": {"minecraft:diamond_ore": "nonsense"}` and restart:
   the bad entry is skipped, the module still loads, no crash.
7. Open ESP's settings while BlockRadar's are open: the colour row must **not** appear there.

## 9d. Scrolling, tooltips, reset

1. Shrink the game window so the panel clamps (e.g. 500x400): lists must scroll, nothing draws
   outside the glass, and clicks above/below the clip must not toggle hidden rows.
2. Hover every module and setting: a tooltip appears and stays on screen near the right/bottom
   edges.
3. Pick a module, change three settings, click **reset** in the settings header: all three
   return to defaults (and the module's own default colour comes back).

## 9e. Startup splash (fox catching a fish)

1. Launch the game with the mod installed and watch the title screen.
2. **Pass:** within ~0.2 s a dark snow backdrop appears, then a glass card with `FoxClientv2.0`, a line
   of flavour text, a scene with ice, a fox and a fish, and a progress bar.
3. Timeline to check: fox sits -> leaps (0.85 s) -> splash droplets (1.35 s) -> surfaces with a fish
   in her mouth (1.75 s) -> sparkles -> card fades out by ~2.6 s.
4. **Pass:** the splash disappears on its own and never comes back during the session (it is once
   per launch, not once per screen).
5. **Pass:** no crash when a screen other than the title screen is up (open the mods list or the
   language screen immediately after launch).
6. If nothing appears at all, the mixin did not apply: check the log for
   `ScreenMixin` / `extractRenderStateWithTooltipAndSubtitles` in the mixin apply list.

## 9f. Keybinds

1. Right Shift -> right-click a module (e.g. Fullbright) -> click the **Keybind** row -> the footer
   changes to `press a key`.
2. Press `F`. The row shows `F`, the module row shows a small `F` chip, and pressing ESC leaves the
   GUI without changing anything.
3. Close the GUI, press `F`: Fullbright toggles. **Hold `F`**: it toggles once, not every tick.
4. Right-click the Keybind row: it returns to `unbound` and the chip disappears.
5. Bind two modules to the same key: both toggle together (expected — binds are polled, not routed).
6. Restart the game: binds persist. Then hand-edit a bind in `config/foxclient/config.json` to
   `999` and restart: the client still loads and the bind simply never fires.

## 9g. BuildAssist (no packets)

Setup: copy `sample-schematics/fox-den.foxbuild` into `config/foxclient/schematics/`.

1. Right Shift -> PLAYER -> right-click **BuildAssist** -> **Schematic file…** -> click
   `fox-den.foxbuild`.
2. Hit **anchor here**, then ESC. **Pass:** ghost boxes appear where the den should be, one bright
   box sits on the lowest missing block, a line runs from your eyes to it, and the block you must
   click is outlined in white.
3. Place that block. Within `Rescan interval` ticks (default 10 = 0.5 s) the highlight moves to the
   next one by itself. **No key press required** — that is the automation.
4. HUD reads: `build fox-den  0%`, remaining count, `next: <block> -> x y z · click <face> of …`,
   and which hotbar slot holds the block.
5. Turn on **Layer only**: only the current layer's ghosts remain, and the layer number on the HUD
   only advances when that layer is finished.
6. **Pass (the important one):** watch your packet count / a server console while doing all of this.
   Nothing is sent — no rotation, no use-item, no placement. Standing still and not clicking means
   the build does not progress.
7. Real file: drop an actual `.litematic` in the folder, rescan, click it. **Pass:** the HUD shows
   `loaded <name> (N blocks, X x Y x Z)` with a plausible N. If it logs
   `decoded 0 blocks … the packing guess is probably wrong`, send me the log line — that is the one
   thing I could not verify here.
8. Break a placed block on purpose: it comes back as a ghost within half a second.

## 9h. Snow-glass theme

1. Open the GUI in daylight, at night and underground: the panel must stay readable over all three
   and you should be able to see the world through it (no opaque black rectangle).
2. Every accent is ice-cyan / white; there is no orange left anywhere except the `Snow Block`
   colours you choose yourself.
3. Hover a module row: tooltip; hover the info card: the full explanation; risk tag colours are
   green (low), amber (medium), pink (high).

## 9i. The render artefacts - isolate them first

The frame is now filtered, so a bad shape cannot corrupt the world any more. Run this in the same
world and with the same modules as the screenshot:

1. Open **About** in the sidebar and read *World draw diagnostics*.
   * If it lists rejects, the module named there is the culprit - send me that line.
   * If it says `no rejected geometry`, the shapes you are seeing are within limits, so they are
     real geometry with colours you chose, not corruption.
2. Turn **Freecam** off and fly normally for a minute. If the artefacts stop, freecam's frustum
   re-capture is involved: turn freecam back on and switch **Refresh culling** off. That trades
   distant chunks for the artefacts, which tells us which side of the trade you are on.
3. With freecam off, toggle BlockRadar, then Sound Radar, then ESP, one at a time. The screenshot
   had BlockRadar + Sound Radar on: black block-sized patches at ground level would be filled boxes
   whose colour resolved dark, and a long straight line is a tracer. `reset` on the module's settings
   restores the ice-cyan palette.
4. Send the log lines containing `[FoxClientv2.0] world draw` - there are at most a handful per session.

## 9j. Combat modules (read the warning first)

**These send packets. Test in a singleplayer world you do not care about, or your own server.**

1. SpeedCrystals: needs obsidian, end crystals, and a second player (or any target) nearby. Hold
   right click. **Pass:** crystals appear next to the target and pop, and the HUD counts cycles.
2. AnchorMacro: needs respawn anchors + glowstone. **Pass:** the HUD walks `place -> charge ->
   detonate` and the anchor explodes.
3. AutoTotem: put a totem in your inventory, empty your offhand. **Pass:** within a second the totem
   is in the offhand. Kill yourself near a mob: the totem must actually trigger - if it does not, the
   offhand swap button is wrong and I need to know.
4. Turn each off and confirm nothing else happens (no stray packets, no leftover state).

## 9k. New UI layout

1. Right Shift: sidebar with four categories + Keybinds/Config/About, cards with chevron and toggle,
   `N modules · M enabled` subtitle, search field, All/Enabled chips.
2. Click a card: settings open on the right; click the same card again: it closes and the empty state
   returns.
3. Search "crystal": the results replace the category list and the title becomes `Search results`.
4. Keybinds pane: click a chip, press a key, close, press the key -> the module toggles.
5. Resize the window small (e.g. 900x600): the panel clamps and both lists still scroll.

## 9l. Anchor macro — the 1.5.0 fix (do this before anything else in Combat)

Setup: **Nether** (anchors only explode there), a target dummy or second player standing on the
ground next to a wall, respawn anchors + glowstone in the inventory.

1. Enable AnchorMacro, do **not** hold right click.
   **Pass:** within a couple of seconds an anchor appears on the ground beside the target, the HUD
   reads `place -> charge (n) -> detonating`, and the anchor explodes. Cycles counter increases.
2. Read the HUD between steps. It must name a blocker rather than idle silently. Try each:
   * no anchors -> `no respawn anchors`;
   * no glowstone -> `no glowstone`;
   * target in a sealed 1x1 hole -> `no free cell`;
   * stand in the Overworld with a charged anchor -> `charged · Overworld anchors do not explode`.
3. Try a target floating in mid-air with nothing nearby: the macro must **not** place into the air
   next to them and must report `no free cell` (a cell needs a solid neighbour and no entity in it).
4. If the server refuses, the HUD must say `the server rejected the placement` — that is the
   diagnostic that used to be a silent loop.
5. Switch `Hold right click` on: the macro must now wait for the key and say `waiting for right
   click`.

## 9m. Spotify, top bar, World Lyrics

1. Open ClickGUI -> Spotify. Paste your **client ID** into the `Spotify client ID` row (click the
   row, type, Enter), press `Link Spotify`. **Pass:** the browser opens the consent page, approving
   it lands on `127.0.0.1:8890/callback` showing "Linked", and the panel status flips to
   `linked · playing`. If your browser says the redirect is invalid, the redirect URI is missing in
   the Spotify app settings - it must be exactly `http://127.0.0.1:8890/callback`.
2. Play a track. **Pass:** the top bar appears within a poll interval with title, artist and a
   moving progress track; `Open current track` and **J** both open the same track in the browser.
3. Pause Spotify: the bar must show `(paused)` and the progress must stop advancing.
4. Enable World Lyrics. **Pass:** a lyric line appears centred over the world, changes with the
   song, and the next-line preview is fainter. Adjust `Offset Y` and `Colour` and confirm both apply
   live; the colour row opens the RGB/alpha picker.
5. Play an instrumental. **Pass:** no lyric line, and the module's status line says
   `no synced lyrics for this track`.
6. Turn off Spotify while lyrics are on: the bar disappears, the lyric line disappears, no
   exceptions, no lingering polling thread (`logs/latest.log` should not repeat poll failures).

## 9n. Discord presence

1. Discord desktop app running, signed in. Create an application at discord.com/developers, copy the
   Application ID into `Spotify -> Discord app ID`, switch `Discord presence` on.
   **Pass:** your profile starts showing the track, and the panel status says `connected · showing:
   <track>`.
2. Quit Discord. **Pass:** status becomes `Discord not running` after a retry; the game is
   unaffected; restarting Discord reconnects within ~10 s.
3. Switch `Discord presence` off. **Pass:** the presence clears.

## 9o. New UI (2.6.2 shape) and AI Assistant

1. Right Shift: rail with `FEATURES` (Favourites + four categories with counts) and `OTHERS`
   (Keybinds, Config, AI Assistant), your player name and an `Online` dot at the bottom.
2. Search field: full width, live `N modules` count, `All` / `Only on` chips work; typing filters.
3. Cards: two columns. Click the star -> the module appears under Favourites and the star fills.
   Click the switch -> the module toggles without opening the panel. Click the body -> the settings
   panel opens on the right.
4. Settings panel: `Enabled` row toggles; descriptions wrap; sliders drag; right-click a row resets
   just that row; `Reset module settings` resets all; `×` closes.
5. Footer: `Packet modules off` disables every `packets` module in one click; the profile chip
   flips between `default` and `peak`; the hint line matches the keys that actually work (Space
   toggles, Tab cycles category, Enter reports the selected module's settings).
6. AI Assistant pane: ask `where is esp`, `which modules send packets`, `why is anchor macro idle`,
   `what am I listening to`, `link discord`. **Pass:** each answer names real modules and settings,
   the referenced module is selected in the background, Undo removes the last exchange, Clear empties
   the log, Copy puts the transcript on the clipboard.
7. Connection tab must state plainly: local matcher, model none, network none.
8. Restart the game: favourites and the last category are restored from `ui.json`.

## 9p. Cosmetics (render-only)

1. PrismHalo: enable, stand still in third person (F5). **Pass:** two thin inclined bands float above
   your head, rotating against each other, with motes riding the outer band on a colour gradient - and
   nothing resembling a cone or a disc. Set **Motes** to 0: the bands stay, the dots go.
   head, follows you, and does not flicker at distance. Set `Segments` to 8: it becomes visibly angular
   (proves the setting is wired). Turn `Self` off: it disappears from you but not from a second player
   in range.
2. JumpRings: jump. **Pass:** a ring spawns at your feet, expands to `Radius`, fades over `Lifetime`,
   and stays where it was created while you move away. Spam-jump: never more than 12 rings.
3. Trail: walk forward. **Pass:** particles drop behind you at the rate you set. Stand still with
   `Only while moving` on: no particles. Confirm with F3 that they are particles, not entities, and
   that a second player sees none of them.
4. HitSpark: hit a mob/player within 12 blocks. **Pass:** crit sparks burst at the target; your own
   hits taken spark if `Self sparks` > 0. Nothing happens when nothing is damaged.
5. All four with F3 open: watch the frame time. They should be noise on a modern GPU; if not, note the
   module and drop the spawn rate.

## 9q. Fog, sky and the HUD editor

1. Read the log for the optional mixin config: it must say applied or skipped, never a crash.
2. NoFog: go under water or into the Nether. **Pass:** the fog wall is pushed far out (or gone) and
   the ClickGUI status line for NoFog reads `active · N blocks`. Toggle it off: fog returns
   immediately. If the status says `hook not applied in this build`, the mixin did not load on this
   version — that is the designed degradation, not a bug; report it and I will retarget the hook.
3. SkyTint: enable, look up. **Pass:** the sky disc takes your colour, the sun/moon/stars/clouds keep
   drawing, world lighting is unchanged. Set brightness to 0.3: darker but still a sky.
4. HUD editor: press **P**. **Pass:** the HUD block is outlined and draggable. Drag it to the
   bottom-right and release: it snaps to the bottom-right corner. Close, resize the window: the block
   stays in its corner. Rejoin: the position persists (check `ui.json`). Right-click resets it.
   `Edit HUD…` in ClickGUI → Config opens the same screen.
5. Turn on five HUD modules, open the editor, confirm the outline matches what is actually drawn and
   that the drag moves all of it together.

## 9r. HUD pack (Watermark, Keystrokes, ItemCounter, TargetHUD, ArmorHUD, TargetEsp)

1. Watermark: enable. **Pass:** one line with the brand, ping, fps and clock; each piece disappears
   when you switch its setting off. On a server ping is a real ms value.
2. Keystrokes: walk and jump. **Pass:** W/A/S/D and SPACE light up as you press them and go dim on
   release; LMB/RMB cells follow the mouse buttons. Toggle `Mouse buttons` and `Space bar` and watch
   the block change height.
3. ItemCounter: pick up a totem, a pearl, obsidian. **Pass:** the counts change immediately, and
   entries at zero disappear with `Hide empty` on.
4. TargetHUD: look at a mob. **Pass:** name, health bar, hp number and distance appear. Damage it: the
   bar shrinks and changes colour. Aim at nothing: the nearest player within the radius is shown —
   confirm this matches the documented fallback, not a wallhack.
5. ArmorHUD: wear a full set, take damage. **Pass:** four rows with bars that drain and percentages
   that drop.
6. TargetEsp: look at an entity. **Pass:** a box pulses around exactly that entity and disappears when
   you look away. Aim at a wall: no box (vanilla did not pick an entity, so neither do we).
7. All six plus Spotify/lyrics on: drag the HUD in the editor (P) and confirm everything moves
   together and nothing overlaps vanilla's hotbar or chat.

## 9s. New ClickGUI and the now-playing card

1. Right Shift: rail with counts, category name in caps with `N modules · M on`, search on the right,
   two columns of pills.
2. **Left click** a pill: the module toggles and the status chip confirms it. **Right click**: the
   settings drawer opens over the grid with the grid dimmed. `×` closes it; so does Esc.
3. Star a module: it appears under Favourites with a filled star and survives a restart.
4. In the drawer: drag a slider, flip a toggle, cycle an enum, open a colour, type into a text row
   (Spotify client ID), bind a key — then `Reset module settings` puts them all back.
5. Footer: `Packet modules off` disables every `packets` module; the hint line is under the panel.
6. Spotify: with a track playing, the card shows disc, title, artist, lyric line (or `no lyrics`) and
   the **bar underneath** with elapsed / total. Switch `Lyric line` off: the card gets shorter.
7. World Lyrics → `Position`: `Under the Spotify card` puts the line directly under the card and moves
   with it; `Centre of the screen` puts it mid-screen at the Y offset you set.
8. Disable the Spotify module but keep World Lyrics on: the lyrics fall back to the centre position
   instead of vanishing (the card is not there to hang from).

## 9t. Freecam (the 2.2.0 rebuild) — do this before anything else

1. Enable Freecam in a flat world. **Pass:** the view detaches, the body stays where it was, and the
   **sky is normal** — no giant triangles, no black seams, no holes in the world. This is the exact
   bug from the screenshot; if any of it is still there, stop and send the log.
2. Also test with **NoFog on** at 192 blocks: the world stays clear, the sky keeps its horizon gradient
   and no dome edges appear. If you see triangles with NoFog on and freecam off, that is the sky-dome
   artefact, not freecam — send that case separately.
3. Press W/A/S/D. **Pass:** the camera moves and **your body does not**. Look at the F3 coords: they
   must not change. Release the keys: the camera glides to a stop.
3. Move the mouse. **Pass:** the camera turns, your body's facing stays where it was (check the F3
   `facing:` line, and the hand in third person).
4. Hold W for five seconds. **Pass:** the camera stops at `Distance` metres from the body and the
   ClickGUI drawer says `at the distance limit`; no chunk unload, no world holes.
5. Click while flying. **Pass:** nothing is attacked or used; opening a GUI still works.
6. Open the ClickGUI while freecam is on and read the Freecam status line: it must say
   `look ok · freeze ok`. If it says `look —`, the input mixin did not apply on your mappings — report
   the log and the version.
7. Disable Freecam. **Pass:** the view snaps back to the body, movement and mouse look return to
   normal, and no keys stay "stuck" down.
8. Set `Freeze body` off: the body walks again while the camera flies — that is the documented
   fallback, not a bug.

## 9u. Freecam, take three (2.3.0) - run this before anything else

The 2.2.0 freeze hook was attached to `ClientInput#tick`, which `KeyboardInput` overrides, so it never
ran. 2.3.0 hooks `KeyboardInput#tick` and backs it with a position write-back. Test in a **flat
world, no plugins, standing on grass**, with chat fully visible.

1. Write down your F3 `XYZ` and `facing:` values.
2. Enable **Freecam**. **Pass:** the view detaches; the F3 numbers do not change just by enabling.
3. **Hold W for five seconds.** **Pass:** the camera flies forward and the F3 `XYZ` stays exactly
   where it was. **Fail (the 2.2.0 bug):** the numbers move - note by how much and whether the body
   walked in the direction you were looking.
4. While still holding W, press F5 (third person). **Pass:** your body is standing still, oriented
   exactly as it was when you enabled freecam, while the camera is somewhere else.
5. **Sweep the mouse hard left then hard right.** **Pass:** the camera turns at the same speed as
   normal play, in the same direction, and a full-screen width of mouse travel gives roughly a
   270-360 degree turn - not a slow crawl. **Fail:** turning is much slower than normal - say so, the
   scale factor needs re-deriving from `Options.sensitivity`.
6. Fly to the distance limit (hold W ~20 s). **Pass:** the camera stops and the ClickGUI drawer's
   Freecam status line ends with `at the distance limit`.
7. Open the ClickGUI (Right Shift) with freecam on, read the status line. **Pass:** it contains
   `look ok` and `freeze ok`. As of 2.4.0 `freeze ok` comes from the input substitution, which needs no
   mixin, so it should be there on every build. `freeze held` means only the position layer is holding -
   the body still will not move, but report it.
8. Jump off a small ledge, enable freecam mid-air. **Pass:** you hang in place instead of falling.
   Disable freecam: **pass:** you resume falling from the same spot.
9. Turn freecam off. **Pass:** the view snaps back to the body, WASD walks normally again, and F3
   `facing:` is exactly what it was in step 1 if you never re-looked the body.

## 9v. StaffWatch (2.3.0)

1. Enable **StaffWatch** (ClickGUI -> Misc, default off). **Pass:** `config/foxclient/staff-watch.txt`
   appears with its comment template.
2. Join any server (or your own with a second account). **Pass:** with an empty watch file and no
   rank-tagged names, nothing is drawn on the HUD and the status line reads `0 online / N watched`.
3. While the game is running, add your own second account's name to the file and save. **Pass:**
   within a second or two the HUD shows a red block with that name - no restart needed.
4. Remove that name, save. **Pass:** the block disappears.
5. On a server where tab entries are drawn with `[MOD]` / `[ADMIN]`-style prefixes, **pass:** those
   entries are listed. A player whose *name* merely contains a staff word (`Modzilla`) must **not** be
   listed - the match is whole-word only.
6. Turn it off. **Pass:** the HUD block goes, and nothing else changes.

## 9w. StaffWatch 2.0 (2.4.0)

1. Enable **StaffWatch** (ClickGUI -> Misc). **Pass:** `config/foxclient/staff-watch.txt` exists with its
   comment template, and the status line ends with `N watched / M pinned`.
2. Join a server with a second account online, add that account's name to the file, save. **Pass:** the
   HUD row appears within a second or two, labelled `listed`.
3. Walk within `Nearby` blocks (default 32) of that account. **Pass:** the row becomes `near` with a
   distance, and moves to the top of the block. Drink an invisibility potion on that account: **pass:**
   the row reads `near ... invisible` in the alert colour.
4. Reconnect the second account while staff mode (or spectator if your server allows it) is on. **Pass:**
   the row reads `spectator`.
5. Rename that account (Mojang name change). **Pass:** after relogging, the row still appears although the
   new name is not in the file - the pinned UUID is matching it.
6. Create `config/foxclient/staff-watch.<server>.txt` with one different name and save. **Pass:** only
   that name is watched on that server; delete the file and the global list is used again.
7. Turn the module off. **Pass:** the block goes and nothing else changes. No chat output, no
   disconnection, no packet - check the log if you are curious, there should be a single `StaffWatch:`
   info line per new match and nothing else.

## 9x. Fair play page and the presets (2.5.0)

1. Delete `config/foxclient/ui.json` (or set `fairPlayAcknowledged` to false), launch, join a world.
   **Pass:** about three seconds in, the **Before you play** page appears over the game, and it never
   appears on a loading or title screen.
2. Scroll the page with the wheel. **Pass:** all three sections are readable, the scrollbar moves, and
   nothing is cut off at GUI scale 1, 2 and 3.
3. Read section 1. **Pass:** every one of the twelve names has `BLATANT` on its line and one line of
   reason under it.
4. Read section 2. **Pass:** it lists only modules whose risk is above *visual only*, each with its label
   and its current on/off state - and it includes BlockRadar/ESP/Sound Radar if you have them.
5. Open any module in the ClickGUI and look at the drawer header. **Pass:** a coloured banner sits under
   the name: `BLATANT - sends packets - servers ban for this` for packet modules, `BLATANT - very obvious
   on screen` for HIGH, `INFO ADVANTAGE - ...` for MEDIUM, nothing for visual-only. The pills in the grid
   carry the same colours as a left edge.
4. Press **Recommended setup**. **Pass:** the message reports how many were switched off, the four HUD
   modules are on, and every radar/ESP/packet module is off. Check the ClickGUI to confirm.
5. Press **Everything off**. **Pass:** every module is off.
6. Press **Close**. **Pass:** you are back in the game, `ui.json` now contains
   `"fairPlayAcknowledged": true`, and relogging does **not** show the page again.
7. Open the ClickGUI, click the **Fair play…** footer chip. **Pass:** the page reopens, and closing it
   returns to the ClickGUI rather than the game.

## 9y. Sound Radar wording (2.5.0)

1. Enable **Sound Radar** with a base nearby (or open a chest repeatedly). **Pass:** cluster lines read
   `↑ BASE? some clues 22m period 20t MECH chest.open · actor unknown` - **words, never a `%`**.
2. Trigger a cluster made only of mechanical/machine sounds. **Pass:** it says `actor unknown`. Add a
   placement or container sound to the same cluster: **pass:** it switches to `player-made?`.
3. Check the settings drawer: the threshold row is called **Min score** and its tooltip says it is not a
   probability.

## 9z. BuildAssist materials (2.5.0)

1. Load a schematic, anchor it, enable **BuildAssist**. **Pass:** under the next-block lines there is a
   `materials (est.)` block with `need / have / miss` per block type, highest count first.
2. Pick up some of a listed item. **Pass:** `have` goes up and `miss` goes down on the next tick.
3. Load a schematic containing water or fire (no placeable item). **Pass:** that row is red and ends with
   `not an item` instead of pretending you need to carry it.
4. Change **Material rows** to 1 and then to 8. **Pass:** the table grows and shrinks and the HUD below it
   is not overdrawn.

## 9aa. NameHider (2.6.0)

1. Enable **NameHider** (Misc). **Pass:** `config/foxclient/hide-names.txt` appears with its template.
2. Join a world with a second account (or a friend on your own server). **Pass:** your own name is
   replaced by `Player` in the **tab list**, in the **name tag** above you, in the **scoreboard sidebar**
   if it lists you, and in **chat** you send.
3. Add the second account's name to `hide-names.txt` and save while the game runs. **Pass:** within a
   second their name is replaced everywhere too - no restart.
4. Check a rank prefix on a server that shows one. **Pass:** the `[RANK]` keeps its colour and the name
   after it becomes the placeholder - the whole line must not turn one colour.
5. Add `Alex` to the file and watch a player named `Alexander`. **Pass:** `Alexander` is untouched.
6. Change **Placeholder** to `???`. **Pass:** every hidden name becomes `???` immediately.
7. Open the module in the ClickGUI and read the status line. **Pass:** it ends with the hooks that ran -
   `hooks: tab tags text` when all three are live.
8. Turn the module off. **Pass:** real names come back everywhere.

## 9ab. OrePredict (2.6.0)

1. **On a remote server**, enable **OrePredict**. **Pass:** the HUD line says
   `ores 0 · no local world - this client is not hosting` and the status line says
   `local worlds only — no integrated server in this session`. It must not stall or spam the log.
2. Open a **singleplayer** world, enable it. **Pass:** within a few seconds boxes appear around ores in
   the chunks it generates, and the HUD counts them up.
3. Fly away and back. **Pass:** already-scanned chunks are not rescanned (the count does not jump
   repeatedly) and new chunks are added nearest-first.
4. Set **Radius** to 1 and then 6. **Pass:** the smaller radius stops adding markers, the larger one
   resumes; the game does not freeze while it works.
5. Change dimension. **Pass:** markers clear, then refill for the new dimension.
6. Turn it off. **Pass:** every marker disappears immediately.

## 9ac. Themes and cosmetics (2.7.0)

1. Open the ClickGUI. **Pass:** the panel, cards, switch, drawer and rail all use one palette - deep
   plum with an orchid accent, not ice-blue - and the footer chip reads **Theme: Orchid**.
2. Click the chip three times. **Pass:** the whole interface recolours **live** (Orchid -> Glacier ->
   Ember -> Orchid), including the panel glow, the switch when on, the drawer and the search border.
   Nothing overlaps or jumps position, because only colours and two radii change.
3. Close and reopen the game. **Pass:** the theme you left it on is still applied, and it is applied
   before the first frame - no flash of the default palette.
4. `ui.json` should contain a `"theme"` key. Set it by hand to something silly (`"nope"`). **Pass:** the
   client starts on Orchid instead of failing.
5. Enable **PrismHalo**. **Pass:** two inclined bands above your head counter-rotate, motes ride the
   outer band with a tail, colours blend from Colour A to Colour B along the arc. **Fail:** anything
   cone-shaped - the hat module no longer exists.
6. Enable **RunicRing** with **Others** on and a second player nearby. **Pass:** both players get a
   ground ring of radial ticks, every fourth longer, with the outer dot ring turning the other way.
7. Turn both off. **Pass:** every line and dot disappears immediately, with no leftover marks in the
   world and nothing in the log.

## 9ae. Fair-play page layout (2.7.1)

1. Set **Options > Video Settings > GUI Scale** to **Auto** (or the highest value). Open the page from
   the ClickGUI footer. **Pass:** every line starts at the same left margin, inside the panel, with no
   horizontal cut through any glyph and no text outside the panel.
2. Repeat at GUI scale **1** and **2**. **Pass:** identical layout apart from size - this was the bug:
   the old code multiplied the clip rectangle by the GUI scale, so at scale 2 the clip sat inside the
   text.
3. Scroll to the bottom with the wheel. **Pass:** lines appear and disappear one whole line at a time
   at the top and bottom of the body; the scrollbar thumb tracks the position; the hint at the bottom
   only says "Scroll for more" while there is more.
4. Shrink the window until it is very short (drag the corner). **Pass:** the panel stays on screen, the
   header and the three buttons remain readable, and nothing is drawn outside the window.
5. Switch theme to Ember and reopen the page. **Pass:** the panel, border and corner radius match the
   rest of the interface.

## 9af. Macro Studio (2.8.0)

1. Enable **MacroStudio**. Bind **Record key** to something free (say `F7`) and **Play slot 1** to
   `F8`. **Pass:** the module shows a HUD line after each step.
2. Press F7. **Pass:** the HUD reads `REC slot 1 - 0 keys - 0.0s` in red.
3. In game, press `1`, wait about half a second, press `2`, then jump twice, then press F7 again.
   **Pass:** the HUD announces `slot 1: N step(s)`, and `config/foxclient/macros.json` exists with a
   `slots` array. Open it and confirm every `k` is a key you actually pressed.
4. Press F8. **Pass:** the same hotbar swaps and jumps happen again at the same spacing, one step per
   the recorded delay. Nothing clicks.
5. While a macro is playing, open your inventory. **Pass:** playback stops immediately with
   `playback a screen opened`, and no key is left held down (walk around: you do not keep sneaking).
6. Record a macro that sneaks, then play it and take a hit. **Pass:** playback stops with
   `playback damage taken` and you are not stuck sneaking.
7. Type letters in chat while recording. **Pass:** none of them are captured - only inputs bound to a
   vanilla control are recorded, and recording stops when the chat screen opens.
8. Close the game and reopen. **Pass:** the log shows `macro: loaded N step(s)` and F8 still plays.

## 9ag. Fight Telemetry (2.8.0)

1. Enable **FightTelemetry** and hit a mob or a player. **Pass:** the HUD shows `hits 1 - 0 miss - N/s`
   and a `reach` value near the distance you were standing at.
2. Swing at nothing ten times. **Pass:** the miss counter rises and `reach` does not change.
3. Hit twice as fast as the weapon's cooldown. **Pass:** the `gap` average drops below the cooldown
   time - that is the point of the number.
4. Strafe around a target while hitting it. **Pass:** `strafe` rises toward 100 as your movement
   becomes perpendicular, and drops when you walk straight at it. The straight-in count rises too.
5. Stop hitting anything for the configured **Fight gap**. **Pass:** one line appears in the log
   starting `[FoxClientv2.0] fight:` with hits, gap, reach, strafe and knockback, and the HUD shows
   `last: ...` for a few seconds.
6. Take a hit. **Pass:** the `kb` line shows a non-zero value and its peak updates.

## 9ah. Ghost Replay (2.8.0)

1. In a **singleplayer** world, enable **GhostReplay**, bind **Record key**, place yourself somewhere
   flat and press it. **Pass:** the HUD shows `GHOST REC slot 1 - N tick(s)` in red.
2. Walk a few blocks, turn, jump, then press the record key again. **Pass:** `ghosts.json` appears
   with three arrays, the first populated, and each entry has five fields.
3. **Pass (draw):** a thin ground path appears along the route you walked, in the grounded colour
   where you were on the ground, and a marker cross with a facing arrow moves along it at roughly the
   speed you walked. The HUD shows `ghost 1 - t/total - N.N blocks`.
4. Switch **Loop** off and let it reach the end. **Pass:** the marker stops at the last sample.
5. Join a **multiplayer** server with the module still enabled. **Pass:** the record key refuses with
   `record only works in a world you host`, and the path from step 3 still draws (it is your own data).
6. Cycle the slot to 2 and 3. **Pass:** empty slots draw nothing and say `no runs recorded`.

## 9ai. Macro Studio - blocks and speed (2.9.0)

1. Record a macro: hold **right click** on a wall for about a second, release, then press a hotbar key.
   **Pass:** `macros.json` contains steps with `"m": true` for the right button plus the hotbar key.
2. Stand where you recorded and play the slot. **Pass:** blocks are placed **where you are looking now**,
   held for about the same length of time, then the hotbar switches. **Fail to expect:** it does not
   place at the recorded coordinates - it replays input, not position.
3. Turn to a different wall and play it again. **Pass:** the blocks go on the new wall. That is the
   documented behaviour, not a bug.
4. Left click while recording. **Pass:** nothing is recorded for the attack button, at all.
5. Set **Speed** to 2.0 and play. **Pass:** the same sequence runs in half the ticks, and the HUD shows
   `2.00x`. Set 0.25 and it crawls. At 4.0 with 1-tick gaps, steps collapse into the same tick without
   an error.
6. Play a macro that is holding right click and open your inventory mid-way. **Pass:** playback stops,
   `playback a screen opened`, and you are not left holding the use button.

## 9aj. Custom theme editor (2.9.0)

1. Open the ClickGUI and click the footer chip three times. **Pass:** Orchid -> Glacier -> Ember ->
   Custom, and the last one is labelled `Theme: Custom`.
2. Right click the chip. **Pass:** the theme editor opens, drawn with the palette it is editing.
3. Drag **Accent hue** across the track. **Pass:** the whole interface recolours live as you drag, and
   the hue track itself shows the sweep.
4. Drag **Accent saturation** to 0, then **Accent brightness** down. **Pass:** the accent goes grey, then
   dark, and the text stays readable - the inks are derived from the accent family, not the sliders.
5. Drag **Body darkness** and **Corner radius**. **Pass:** the panel body darkens and every rounded
   corner in the interface changes radius together. At **0** every corner is square, there are no
   corner specks and no stray line along any edge; 0 is also the default for a fresh custom theme.
6. Close the game and reopen. **Pass:** the same custom palette is applied before the first frame, and
   `ui.json` holds `theme`, `themeHue`, `themeSat`, `themeBright`, `themeBodyHue`, `themeBodyDark`,
   `themeRadius`.

## 9ak. Glass pass (2.9.0)

1. Open the ClickGUI. **Pass:** the panel has a soft accent glow, a gradient body, a diagonal sheen
   across the top third, a bright top rim, an accent-tinted bottom rim, and four bracketed corners.
2. Open a module's drawer. **Pass:** the same glass stack, without brackets, and the grid behind it is
   dimmed.
3. Look at the module pills. **Pass:** each has a gradient body and one bright pixel row along its top
   edge; the selected one is brighter.
4. Switch to Glacier and Ember. **Pass:** every layer follows the palette - glow, sheen rim and brackets
   all recolour.
5. Open the fair-play page. **Pass:** same glass panel, and its header rule has an accent head.

## 9al. Anchor macro (2.9.1)

1. In the **Nether**, with respawn anchors and glowstone, stand about 3 blocks from a mob or a second
   player, enable **AnchorMacro** and watch the HUD. **Pass:** it reads `switching to anchors`, then
   `placed - confirming`, then `confirming placement n/20`, then `anchor down`, then `charging -
   confirming`, then `charged`, then `detonated - 1 cycle(s)`, and the target takes explosion damage.
2. Repeat on a server with real latency (a public server, or your own with `Network latency` added).
   **Pass:** the same sequence completes, just with the confirm counters climbing higher. **Fail to
   expect:** no immediate "no anchor appeared" after 3 ticks - that was 2.9.0.
3. Turn **Auto switch** off while holding something that is not an anchor. **Pass:** the HUD says
   `hold respawn anchors` and **nothing is placed** - no dirt, no wrong blocks.
4. Put glowstone in a main-inventory slot and play the cycle. **Pass:** it swaps the glowstone into your
   held slot, waits a tick or two (`switching to glowstone`), and only then clicks.
5. Stand in the **Overworld** with a charged anchor. **Pass:** `charged - Overworld anchors set spawn,
   they do not explode` and it stops clicking instead of spamming packets.
6. Break the anchor mid-cycle. **Pass:** the macro notices the block is gone and returns to `PLACE`
   without spinning, and `Retries`/`Settle timeout` are visible in the drawer.
7. Take away all anchors. **Pass:** `no respawn anchors` with `craft or grab anchors`, no clicks.

## 9am. AppleUI pack (2.10.0)

1. Start the game and open **Options > Resource Packs**. **Pass:** two new entries, `FoxClient AppleUI`
   and `FoxClient AppleUI (bar)`, both listed as compatible (not "incompatible"), both off.
   The log also has `AppleUI packs registered: apples=true bar=true`.
2. Enable the module **AppleUI** in the Misc list (default style **Apples**). **Pass:** without a
   restart, you hear/see resources reload and: hearts are red apples, absorption hearts are golden
   apples, hunger is a wheat sheaf, and the groove behind the hearts is a dark red bar.
3. Poison yourself. **Pass:** the apples turn green. Wither (a wither skull or `/effect give @s wither`)
   turns them grey; powder snow turns them cyan. Hardcore hearts are still apples.
4. Set **Style** to **Red bar**. **Pass:** the row becomes a plain red bar with gold segments for
   absorption; hunger stays wheat.
5. Turn the module off with **Hand back on disable** on. **Pass:** resources reload again and the
   vanilla hearts are back; in Options the pack is unticked again.
6. Turn **Hand back on disable** off, then disable the module. **Pass:** the pack stays selected (that is
   what the setting promises) and the icons stay.
7. Place a respawn anchor in the Nether. **Pass:** it looks like a composter, writes a composter fill
   sound when charged, and still explodes. Glowstone looks like a hay bale; obsidian looks like
   mycelium; an end crystal looks like a golden dandelion.
8. Check the HUD at half a heart and at one hunger point. **Pass:** half apples and half wheat line up
   with the vanilla positions - if they are offset, the sprite is the wrong size (they must be 9x9).

## 9an. Apple and grapes (3.0.0)

1. Enable **SelectionApple** in the RENDER list. **Pass:** a green apple outline sits around the
   selected hotbar slot, the item inside it is still fully visible, and pressing `1`-`9` moves the apple
   with the selection.
2. Change **GUI Scale** to 2 and 3. **Pass:** the apple stays centred on the slot at every scale; if it
   drifts, the hotbar geometry is wrong (it must come from `width/2 - 91`, `height - 22`, 20 px a slot).
3. Turn on **Pulse**, then set **Size** to 8 and to 15. **Pass:** the pulsing is visible but not
   distracting, and the ring never covers the item icon.
4. Press **F1** to hide the HUD. **Pass:** the apple disappears with the rest of the HUD.
5. Enable **GrapeCrosshair**. **Pass:** the crosshair becomes a purple bunch of grapes with a stem and a
   leaf, there is **no vanilla cross** behind it, and resources reload without a restart. Confirm
   `FoxClient grape crosshair` is listed in **Options > Resource Packs** and ticked.
6. Aim at a block face and place a block. **Pass:** the placement lands where the bright pixel of the
   bunch points - the aim point is unchanged.
7. Disable it. **Pass:** the vanilla crosshair is back and the pack is unticked.
8. Enable AppleUI and GrapeCrosshair together. **Pass:** apples for hearts *and* the grape crosshair;
   the two packs do not fight (each owns different sprites), and toggling one does not remove the other.

## 9ao. Faster crystals and anchors (3.0.0)

1. With **SpeedCrystals**, stand next to a target on obsidian and hold right click. **Pass:** with
   **Turbo** on you see `placing x2` / `detonating x2` in the HUD and crystals pop noticeably faster
   than with it off; the two placements of a tick land on *different* obsidian blocks.
2. Set **Place per tick** to 4. **Pass:** up to four bases per tick, never the same base twice.
3. Turn **Turbo** off. **Pass:** behaviour is the old one action per **Interval** tick.
4. With **AnchorMacro**, watch the HUD in the Nether. **Pass:** the cycle completes with the step delay
   default of **1** - `anchor down` follows `placed` within a tick or two - and the confirm phases still
   protect against a slow server (`confirming placement n/20`).
5. Check the drawer: **Step delay** minimum is 1, **Settle timeout** is still 20 and **Retries** still 2.
   **Pass:** no step is skipped - place, confirm, charge, confirm, detonate, all visible in sequence.

## 9ap. HUD, per element (3.1.0)

1. Enable four modules, press `P`. **Pass:** each module has its own handle with its name and a
   `PINNED`/`STACK` tag, and handles for stacked modules are in a column that does not overlap.
2. Drag one handle to the top right, close the editor. **Pass:** that module draws from the new spot and
   the others stay in the stack where they were.
3. Resize the window (or change GUI scale) and reopen. **Pass:** the pinned module is in the same relative
   place - positions are stored as fractions, not pixels.
4. Right-click the handle. **Pass:** the module falls back into the stack, and the stack closes up.
5. Hold a handle near a screen edge or a third line. **Pass:** it snaps; `S` turns snapping off; `R`
   clears every pin at once. Restart the game: pins are still there (`ui.json` -> `hudElements`).

## 9aq. Macro studio and the sandbox (3.1.0)

1. Record a short macro (hotbar swaps plus a slightly held right click) into slot 1, then open the studio
   with the `Open studio` key. **Pass:** the timeline shows bars for every edge, wider where you paused,
   with a tick grid every second and a hover readout naming the key.
2. Set slot speed with `Speed +` to 2.00x and press `SPACE`. **Pass:** the playhead crosses the timeline
   at twice the rate; the clock reads `t = ... / ... at 2.00x` and the total duration halves.
3. Set `Loops +` to 3. **Pass:** the sandbox wraps the playhead three times, the warning
   "repeats 3x" appears, and playback (the real one, via the play key) also runs three passes.
4. Set speed to 0.50x. **Pass:** the playhead is *slower than real time* - this is the fractional-tick
   path, so if it still runs at 1x the credit accumulator is wrong.
5. Drag the timeline, click a bar, press `DEL`. **Pass:** scrubbing moves the playhead and updates
   "now ->" and "held ->" without playing anything; `DEL` removes exactly the selected step and the
   timeline re-scales.
6. Check the warning list against a macro that ends with a held key. **Pass:** it says which keys are
   still held, and no keys are actually pressed by the sandbox (watch the world: nothing moves).

## 9ar. Waypoints, capture, packs (3.1.0)

1. Press `B` in the Overworld, go to the Nether, press `B`. **Pass:** each HUD line shows only the
   waypoint for the dimension you are in; enabling `Other dimensions` shows both, and the beam in the
   world only draws for the ones it lists.
2. Stand below a waypoint. **Pass:** the line shows `▲` with the height difference; stand above and it
   shows `▼`.
3. Enable **BuildCapture**, walk to one corner of a small build, press the corner key (default: set it),
   walk to the opposite corner, press the second, press save. **Pass:** a green box tracks the selection,
   the HUD status reports `saved N block(s) - loaded into the build assist`, and BuildAssist shows the
   hologram and material list for what you just captured.
4. Try a huge cuboid (say 400x400x400) and save. **Pass:** it refuses with `too big: N blocks (max
   250000)` instead of freezing.
5. With **AppleUI** on: the hotbar is the dark plate, the selected slot has the apple-green frame with a
   stem and leaf, armor pips are gold apples and the XP bar is grape purple. **Pass:** with the pack on,
   `SelectionApple` draws nothing (it defers to the pack); switch the pack off and the module's own apple
   outline comes back.
6. Set the theme to **Nocturne**. **Pass:** near-black body, lime accent, square corners, and no leftover
   purple anywhere in the drawer, tooltips or the HUD block.

## 9as. Audio, shield, swap (3.2.0)

1. Enable **AmbienceMixer** and go into a dark cave below y=50. **Pass:** every few seconds a quiet cave
   sound arrives from a direction around you, not from inside your head; raising `Volume` makes it louder
   and setting it to 0 silences it completely.
2. Stand in rain, then dive underwater, then visit the Nether. **Pass:** each environment produces its own
   layer, and the layers you set to 0 never play.
3. Open the ClickGUI while a sound is due. With `Pause in menus` on, nothing plays. **Pass:** no sound
   while a screen is open.
4. Enable **ShieldStatus**, hold a shield and block. **Pass:** the shield's slot is washed green with a
   green border and the HUD reads `SHIELD UP`; release the block and both go red / `SHIELD DOWN`. Test with
   the shield in the off-hand too: the off-hand slot is the one that tints.
5. Enable **AttributeSwap** with `Slot 3` and `Swap back` on. Left-click. **Pass:** the selected slot jumps
   to 3 for the configured ticks, then goes back to where you were; the swap happens once per click and not
   repeatedly while the button is held. Empty the slot with `Require an item` on: nothing happens.

## 9at. Fire and transparency (3.2.0)

1. Enable **AppleUI** and set something on fire, or stand next to fire. **Pass:** the flames are **green
   grass-like blades** that animate, with no black boxes around them. If they still look like grey or
   black smears, the pack did not rebuild - re-run `tools/appleui_pack.py`.
2. Compare frames: the animation should cycle, not sit still (`frametime: 2`).
3. Open **Theme > custom**, drag **Panel transparency** to 100, then to 20. **Pass:** the panel body goes
   from solid to clearly see-through while text, borders and the accent stay crisp; nothing turns
   unreadable and the change applies to the prebuilt themes too.
4. Restart. **Pass:** the transparency value is remembered (`ui.json` -> `uiOpacity`).

## 9au. Enemy shields and tracers (3.2.1)

1. On a server (or with a second account), watch a player holding a shield. **Pass:** a box at their
   shield and a ring at their feet, **green** while they block and **red** when they lower it, flipping
   the moment the state changes.
2. Turn the shield holder sideways and walk around them. **Pass:** the box stays on the side they hold it
   (off-hand vs main hand), not through their chest.
3. Step back past `Range`, or behind a wall with `Through walls` off. **Pass:** the marker disappears at
   the range limit and when occluded - it never draws for players you cannot see with walls off.
4. Ask the other player to drop the shield. With `Only shield holders` on. **Pass:** nothing is drawn.
5. In first person, enable **ESP** with tracers and turn on the spot. **Pass:** tracers begin a block in
   front of you and rotate with the world instead of sweeping across the screen; `Tracer start` at 0 brings
   the old behaviour back, and 2.0 pushes them further out.
6. Repeat in third person. **Pass:** tracers still look sensible - the offset reads as a line from your
   head, and Block Radar's own tracers behave the same way.

## 9av. Density and Obsidian (3.3.0)

1. Open the theme editor (ClickGUI footer `Theme: ...` -> right click). **Pass:** a `Density: normal` chip
   sits in the header; clicking it cycles compact -> normal -> comfortable and the panel re-lays out live.
2. Set **compact**, close the editor, reopen the ClickGUI. **Pass:** module rows are visibly tighter (21px
   with 3px gaps), the category rail is shorter, and _more modules fit_ - roughly a third more than normal.
   Font size is unchanged; nothing overlaps and the scrollbar still reaches the last row.
3. Set **comfortable**. **Pass:** rows are 32px with 8px gaps, settings rows grow with them, and the drawer
   still scrolls correctly.
4. Restart the game. **Pass:** the density is remembered (`ui.json` -> `uiDensity`) and applied when the
   ClickGUI opens.
5. Cycle the theme to **Obsidian**. **Pass:** black body, bone-white accent, square corners everywhere
   (no rounded cards), risk badges still readable, and the HUD block picks up the same accent.
6. With Obsidian + compact + panel transparency 20, read the module list and the info card. **Pass:** text
   is still legible over the blurred world - that combination is the worst case for contrast.

## 9aw. Privacy switch (3.3.1)

1. Play a track with the Spotify module connected and **WorldLyrics** on. **Pass:** lyrics load and the
   module status reads `N lines ...`.
2. Turn **Look up online** off and change track. **Pass:** the status reads `online lookup off`, and with a
   network monitor open there is **no request to lrclib.net** at all - not a failed one, none. Already
   fetched lines keep rendering.
3. Turn it back on and change track. **Pass:** lookups resume immediately.
4. Confirm the claims in `PRIVACY.md` against the code: `grep -rn "HttpClient\|SocketChannel\|URI.create"
   src/client/java` should only show `media/SpotifyClient`, `media/LyricsClient`, `media/DiscordRpc`.

## 9ax. FightCoach (3.4.0)

1. Enable **FightCoach**, stand near another player and trade a few hits. **Pass:** a live line shows
   `fight: hits/swings landed ...` while it runs.
2. Walk away and stay out of range for the configured `End after` seconds. **Pass:** a **FIGHT REPORT** card
   appears with duration, swings, accuracy, damage, and (if you landed) distance and shield numbers. It
   disappears after `Card seconds`.
3. Swing at the air ten times with nobody in range. **Pass:** no card - swings outside a fight are not
   counted, so a stray click does not create a report.
4. Land three hits from as far as you can reach. **Pass:** the note reads that you are hitting at the edge of
   range, and the distance line shows a max near 3.5-4 blocks.
5. Check honesty of the damage number: hit once for a known amount and compare. **Pass:** dealt is within a
   point or two of the real hit (it is a health-delta estimate, not a packet read).

## 9ay. Replay theatre (3.4.0)

1. Enable **ReplayTheatre**, fight for ~10 seconds, then press the `Theatre key`. **Pass:** the panel opens at
   the bottom with a filled timeline, the playhead at the end, and `IN THE REPLAY` listing the players with
   health - the ghosts are boxes with facing arrows in the world behind the panel.
2. Drag the bar left/right. **Pass:** the ghosts jump to that moment, health bars move, nothing in the world
   actually changes (nobody takes damage, no packets).
3. Press `SPACE`, set speed to 0.25x, then 2x. **Pass:** the playhead crawls at a quarter speed and runs
   double at 2x; it stops at the end instead of wrapping.
4. Look for the marks. **Pass:** green ticks where damage was dealt and red where you took it; scrubbing to a
   mark lands on that hit.
5. Let the buffer fill past `Buffer seconds`, then open the theatre. **Pass:** only the last N seconds are
   shown, older marks are gone, and memory does not keep growing.
6. Enable Freecam and fly through the replay. **Pass:** you can orbit the ghosts; your body does not move, and
   turning the camera does not affect the recording.

## 10. Regression sweep

| Scenario | Expected |
|---|---|
| Resize window / toggle fullscreen with the GUI open | No crash, layout reflows |
| Change GUI scale 1 → 4 | Text and rows stay readable |
| Nether → Overworld dimension change | Radar rescans, no stale hits, no exceptions |
| `/reload` (F3+T) | No crash; modules keep working |
| Disconnect and rejoin | Sound Radar clears its log, no leaking world reference |
| Quit to title, then rejoin a world | Config intact, no duplicate HUD elements |
| Any exception in a module | Logged with the module name, that module disables itself, game keeps running |

---

## 11. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Boxes float offset from blocks | Pose stack in `END_MAIN` already camera-relative → remove the `translate(-camera…)` in `FoxClient#onRenderLines` (line path only) |
| Lines invisible, no crash | `RenderTypes.lines()` attribute mismatch → drop `setLineWidth` in `RenderUtils#line`, or switch to `linesTranslucent()` |
| Nothing draws through walls | Gizmo backend fell back to LINES; check the log for `gizmo backend unavailable` |
| World is blindingly bright after disabling Fullbright | Reload the world (F3+T) — should not happen, the render state is per-frame; report with the log |
| Sound Radar stays empty | Volume-0 packets are dropped by design (no position); check the sounds you test with are positional |
| `Mixin apply failed` in the log | Version mismatch — this jar is built for **26.1.2 only** |
