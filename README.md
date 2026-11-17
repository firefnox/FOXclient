FoxClientv2.0
=============

Client-side Fabric mod for Minecraft 26.1.2 (Java 25).

Scope: local rendering, local audio analysis, and your own media account. It never sends gameplay
packets - not even the build assist, which only ever tells you where to click. It never modifies
movement or combat. The only network traffic it can produce goes to Spotify's API and to lrclib for
the track you are already playing, plus your own local Discord socket - all of it off until you
link an account yourself. It is designed to be used in singleplayer, on your own server, or
anywhere the server's rules allow. On most public servers BlockRadar/ESP/radar features count as
cheating - that's your call, not the mod's.

The three combat modules are the one exception to "no packets": they are labelled `sends packets`
in the GUI and in this README, and they ship off by default. See "Combat" below.

Renamed from FOXCLIENT to **FoxClientv2.0** (2.0.0). What did not change: the mod id is still
`foxclient`, so `config/foxclient/` (modules, binds, `ui.json`, `spotify.json`) and the mixin config
names carry over untouched — drop the new jar in and your settings are there. What did change: the
name in the mod list, the ClickGUI rail, the splash, the log tag (`[FoxClientv2.0]`) and the jar
file name.

Requirements
------------

* Minecraft 26.1.2 (Java Edition)
* Fabric Loader 0.20.1 or newer
* Fabric API 0.155.3+26.1.2 or newer
* Java 25 (required by Minecraft 26.1+ — the game itself will not run on older JDKs)
* Gradle 9.8.2 (handled by the bundled wrapper — do not install Gradle manually)

Build
-----

    ./gradlew build

Output jar:

    build/libs/FoxClientv3.4.0.jar

Drop it in `.minecraft/mods/` together with Fabric API. There is no server-side component;
the jar is marked `"environment": "client"` and only declares a client entrypoint.

Run a dev client
----------------

    ./gradlew runClient

Expected log lines on startup (Phase 1 acceptance test):

    [FoxClientv2.0] initialising (Phase 1: core architecture)
    [FoxClientv2.0] ready — 1 module(s), ClickGUI on Right Shift, panic key END

    (first run)  [FoxClientv2.0] no config at .../config/foxclient/config.json, using defaults
    (later runs) [FoxClientv2.0] config loaded from .../config/foxclient/config.json

IDE setup
---------

IntelliJ IDEA 2025.3 or newer is required for mixins to work correctly with 26.1.
Set the Gradle JVM to Java 25 (Settings → Build Tools → Gradle → Gradle JVM), then import
the project as a Gradle project.

26.1 API reality check (read before Phase 1)
--------------------------------------------

Minecraft 26.1 is the first unobfuscated release, and Fabric API renamed large parts of
itself to match Mojang's official names. Several APIs from the original spec no longer exist
under the names given, so the plan below uses the 26.1 names:

The table below is **verified by disassembling the actual jars** this project compiles against
(`fabric-api-0.155.3+26.1.2` nested modules + `minecraft-clientonly-deobf-26.1.2`), not from
memory:

| Spec said                        | 26.1 reality (verified)                                             |
|----------------------------------|---------------------------------------------------------------------|
| `modImplementation` deps         | plain `implementation` — the new `net.fabricmc.fabric-loom` does not remap |
| `remapJar`                       | `jar` — there is nothing to remap anymore                            |
| Yarn mappings                    | gone; official Mojang names come from the unobfuscated jar           |
| `WorldRenderEvents.LAST`         | **gone** → `LevelRenderEvents` (`rendering.v1.level`), which no longer has `LAST` or `AFTER_ENTITIES`. Available: `START_MAIN`, `COLLECT_SUBMITS`, `AFTER_OPAQUE_TERRAIN`, `AFTER_SOLID_FEATURES`, `AFTER_TRANSLUCENT_FEATURES`, `BEFORE_BLOCK_OUTLINE`, `BEFORE_GIZMOS`, `END_MAIN`, `END_EXTRACTION` |
| `WorldRenderContext`             | `LevelRenderContext` — `poseStack()`, `bufferSource()`, `submitNodeCollector()` |
| `HudRenderCallback`              | **removed** in 26.1 → `HudElementRegistry`, `HudElement`, `VanillaHudElements` |
| `KeyBindingHelper.registerKeyBinding` | **gone** → `KeyMappingHelper.registerKeyMapping(KeyMapping)`    |
| `ClientWorldEvents`              | `ClientLevelEvents` (`AFTER_CLIENT_LEVEL_CHANGE`) — both still in `...event.lifecycle.v1` |
| `ClientTickEvents.END_WORLD_TICK`| `ClientTickEvents.END_LEVEL_TICK` (`END_CLIENT_TICK` unchanged)      |
| `ClientPlayNetworkHandler`       | `ClientPacketListener`                                               |
| `onPlaySound` / `onPlaySoundFromEntity` | `handleSoundEvent(ClientboundSoundPacket)` / `handleSoundEntityEvent(ClientboundSoundEntityPacket)` |
| `LightmapTextureManager`         | **gone** → `renderer.Lightmap`, `renderer.LightmapRenderStateExtractor`, `renderer.state.LightmapRenderState`, `renderer.UiLightmap` |
| `ResourceLocation`               | `net.minecraft.resources.Identifier`                                 |

Confirmed accessors for the Sound Radar input path
(`ClientboundSoundPacket`): `getSound() → Holder<SoundEvent>`, `getSource() → SoundSource`,
`getX/getY/getZ()`, `getVolume()`, `getPitch()`, `getSeed()`.
And for the ClickGUI keybind: `new KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)`,
where `Category` is a record with `Category.register(Identifier)` and constants
`MOVEMENT/MISC/GAMEPLAY/...` (no more `"key.categories.mymod"` string).

Two further notes that will bite in later phases:

1. **26.1 ships a new render engine** — extraction pass plus a "submit node collector"
   submission model (`LevelRenderEvents.COLLECT_SUBMITS`), `ChunkSectionLayer` instead of
   `RenderType` for terrain, and `BEFORE_GIZMOS` (the new gizmo/debug line system). The
   pre-26.1 pattern of grabbing an `VertexConsumerProvider` and pushing lines immediately is
   not the intended path anymore. The Phase 1 demo box will pin down the correct one for
   world-space lines, and I'll report which before Phase 3.
2. **26.1 rewrote the lighting/lightmap** (new algorithm, night vision now adds ambient light
   properly, block light tint is data-driven). `LightmapTextureManager` no longer exists, so
   Fullbright cannot reuse the classic mixin. Phase 2 targets `Lightmap` /
   `LightmapRenderStateExtractor` (note the static `Lightmap.getBrightness(DimensionType, int)`),
   with the `lightmap.fsh` shader as fallback.

Phased plan (agreed order)
---------------------------

* **Phase 0 — scaffold** ✅ *done*: Gradle, wrapper, `fabric.mod.json`, client entry
  point, mixin config, proof-of-life log.
* **Phase 1 — core** ✅ *done*: `Module` base class, `ModuleManager`, settings system
  (bool/int/float/enum/colour/list — block/entity pickers land with their modules), GSON
  config with corrupt-file fallback,
  ClickGUI (`Right Shift`, `shouldPause() == false`), `RenderUtils`, HUD manager
  (`HudElementRegistry`), keybinds + panic key, demo box render to validate the pipeline.
* **Phase 2 — Fullbright** OK *done*: lightmap/gamma mixin on the new 26.1 lighting, clean restore.
* **Phase 3 — BlockRadar** OK *done*: searchable block picker, incremental budgeted chunk scan, batched
  box rendering, chunk-unload pruning, hit cap.
* **Phase 4 — Entity ESP** OK *done*: type filter + friend list, `lerped` positions, projected boxes,
  name/health/distance tags, optional tracers with behind-camera handling.
* **Phase 5 — Sound Radar** OK *done*: `ClientPacketListener#handleSoundEvent` / `#handleSoundEntityEvent` mixins,
  sound-id → category rules, time-decayed event cloud, spatial clustering with a confidence
  score, redstone-period detection, HUD compass + world markers + optional ping.
* **Phase 6 — polish** OK *done*: `TESTING.md` QA matrix, README, packaging (profiling needs a GPU, see below).

What is new in 3.4.0
--------------------

**`FightCoach` — what your fight actually looked like.** Anyone within range means a fight is running; when
it goes quiet for a few seconds you get a card: swings vs hits, accuracy, crit rate, damage dealt and taken,
the distance your hits landed at (min / max / average) and how much of the fight your shield was actually up
— plus one plain-language note picked from the worst number, like *"you are hitting at the edge of range;
step in a little"*. Damage is attributed from health drops within a second of your own swing, so it is an
estimate, and the module says so on its card. It clicks nothing, aims nothing, sends nothing.

**`ReplayTheatre` — an instant replay you can scrub.** Like a sports replay rather than a settings screen.
The module keeps a rolling in-memory buffer (default 20s) of where every nearby player was — position,
facing, health — and never writes a file. The theatre key opens a timeline: drag to seek, `←`/`→` to step a
frame, `SPACE` to play, and the speed button walks 0.25x → 0.5x → 1x → 2x. Marks on the bar show where health
changed — green for damage dealt, red for damage taken — so the moment worth watching is visible before you
scrub to it. The panel sits low on the screen on purpose: the replay is drawn **in the world** as boxes with
facing arrows and health bars, so walk or use Freecam to get an angle on your own fight.

Ghosts are boxes, not skinned models, and that is a deliberate limit: rendering real entity models outside
the world tick needs the full render pipeline and is exactly the kind of change that crashes on someone
else's machine. It replays what the client saw; it records no input and replays none — the attack stream is
still not something this client stores.

What is new in 3.3.1
--------------------

**A privacy section, and one switch that makes it true.** `PRIVACY.md` now documents every socket this mod
opens — the Spotify API (yours, only if you connect it), a lyrics query to lrclib.net, and a *local* Discord
socket that never leaves the machine — and states what is never sent: no server address, no UUID, no
username, no hardware ID, no telemetry of any kind, no update pings. Config stays in `config/foxclient/`.

The switch: **WorldLyrics → "Look up online"**. Off means the lyrics client checks the flag before it builds
a URL, so no request exists at all, not even a rejected one; cached lines still render. On is the default,
because a lyrics feature that cannot look up lyrics is a blank box.

**Per-server profiles are not in this build and will not be.** Not for cheat reasons — because the client
would have to read and store the addresses you join, and that is the single piece of information here most
worth not keeping.

What is new in 3.3.0
--------------------

**Density.** A new appearance setting with three steps — **compact** (21px rows, 3px gaps), **normal**,
**comfortable** (32px rows). The ClickGUI derives every row, gap and setting row from those three numbers,
so compact fits roughly a third more modules on one screen without shrinking the font at all, and
comfortable is there for trackpads and high GUI scales. It is a chip in the theme editor header:
`Density: compact`, click to cycle. Saved in `ui.json` with the rest of the appearance.

**A fifth theme: Obsidian.** Black body with a bone-white accent and **zero corner rounding** — the dense
end of the family, where the only colour on screen is the accent and the risk badges, and rows read as a
list rather than as cards. Same palette record as the others, our own numbers.

**On copying Krown's model and shape:** not doing it, and it was your rule first — *"do NOT copy it
entirely in risk of dmca"*. What I will keep doing is building in that direction on our own parts: Nocturne
(near-Black + hot accent) and now Obsidian + density get you the dense, dark, low-chrome look without
anyone else's layout file, palette table or sprites in the jar.

What is new in 3.2.1
--------------------

**`ShieldStatus` points at other players now.** Blocking is the least readable state in a fight — a player
holding a shield looks the same raised or lowered, and that difference is whether your hit lands or gets
absorbed. So the mark goes on *them*: a box where their shield sits (chest height, on the side they hold it),
plus a ring at their feet so it is still legible at thirty blocks, **green while they are blocking and red
while they are not**. Range, colours, walls, line width and "only shield holders" are settings; your own
slot tint and HUD line are still there, both off by default. It reads entity state the client already has —
no packets, nothing for players outside your view distance, and it is labelled `BLATANT` on the card because
a green box over someone's shield is not subtle.

**Tracers no longer look janky in first person.** The cause was structural: the line started at
`getEyePosition()`, and in first person that *is* the camera, so the first stretch of every tracer sat
behind the near clip plane and got cut away — the visible part then swept across the screen as you turned
instead of rotating with the world. Tracers now begin a configurable **Tracer start** distance (default
1.0 block) along your view vector, in front of the near plane, in both ESP and Block Radar. Lower it to 0
to get the old behaviour back, raise it if your FOV or GUI scale still cuts the tail.

What is new in 3.2.0
--------------------

**Audio, honestly.** `AmbienceMixer` (Misc) plays a quiet local ambience bed: a cave rumble when you are
deep and roofed, rain while it rains, the underwater loop when you are submerged, the Nether loop while
you are there — each layer with its own volume, each sound placed a few blocks away at a random spot so it
arrives from a direction. It goes through `playLocalSound`, which is client-side by definition: nothing is
sent, nobody else hears it, and off means vanilla. It is ambience we *generate*. A spectrum visualiser is
the other half of your ask and needs to know what the client is outputting, which means capturing the
audio stream — a decoder and an audio thread of our own — so that stays out rather than be faked with
bars that move to nothing.

**Transparency.** The theme editor has a **Panel transparency** slider (20–100, default 66). It scales the
alpha of the big surfaces — body, cards, sidebar — and leaves text, borders and accents alone, because a
panel you cannot read is not a look. It applies to every theme, not just custom ones, and re-applies
through `Glass` without stacking up: the palette is the source, the slider is a multiplier.

**`ShieldStatus`** (Render): green while your shield is up, red while it is not. The colour goes where you
are already looking — a wash and a border on the shield's slot (hotbar or off-hand) plus an optional HUD
line. The sprite itself is not repainted: a per-frame item tint needs a render-pipeline hook 26.1 does not
expose, and a pack swap would cost a full resource reload per block. Slot and HUD are instant and cannot
break rendering, which is the whole reason for doing it there.

**`AttributeSwap`** (Combat): pick a slot; when *you* left-click, the client selects it, and after a few
ticks it returns to the slot you were holding. One swap per click, driven by your own attack key. It does
not click, aim or choose a target, and it sends nothing itself — a hotbar selection is what a number key
does. Labelled `INFO ADVANTAGE`, which is what it is: a keystroke you could make by hand, made reliably.

**The fire is fixed.** Your fire strip was a palette PNG (mode P) with partial `tRNS` alpha — and fire is a
32-frame vertical animation whose mcmeta has to agree with that shape. Both are now handled: the frames are
re-baked as plain RGBA with alpha snapped to on/off, the grey artwork is recoloured along a green ramp so
it reads as grass instead of colourless blades (luminance kept, only the hue applied), and each file ships
its own `.mcmeta` with `frametime: 2`. Preview: `fire_fixed.png`.

**NoBack stays out.** It is Velocity/AntiKnockback with two extra steps: a knockback multiplier, and
"increase your ping so you take no knockback", which is fake lag on the hit path. Those are two of the
twelve named on the fair-play page, and a slider does not change what either one does to the other player's
game. What the client does have is the honest half: `FightTelemetry` reports the knockback you actually
take, per hit, average and peak.

What is new in 3.1.0
--------------------

**The HUD is no longer one column.** Every enabled module has a handle of its own in the editor
(`P`). Drag a handle and that module is pinned where you drop it, stored as a fraction of the screen so
a resize does not fling it off the edge; right-click a handle to drop it back into the stack. Thirds and
edges are snap targets, `S` toggles snapping, `R` clears every pin. A pinned module leaves the stack, so
the stack still never overlaps itself.

**Macro studio, round two.** A real screen: four slot cards with their step count, speed and loop count;
a **timeline** where every recorded edge is a bar whose width is its wait (so a long pause is visible
instead of buried in a number); click a bar to select it, `DEL` removes it, the buttons step its delay.
Speed and loop count are **per slot** now (0.25x–4x each, the effective rate clamped to that same range),
and playback counts fractional macro ticks, so 0.5x actually plays slower instead of rounding to 1x.

**And a sandbox.** `SPACE` (or the Simulate button) runs the macro in *simulated* time: the playhead
crosses the timeline at the slot's effective rate, loops are honoured, and the panel prints what would be
true at that instant — which step fires, and which keys would be held. Drag the timeline to scrub; the
left/right arrows step a tick. It also lists the two mistakes you cannot see while recording: keys still
held when the pass ends, and runs of steps that all fire on the same tick. **It is timing only** — the
sandbox presses nothing, sends nothing, and makes no promise about what the inputs would do in a world.

**Waypoints know where they are.** The dimension is saved with each entry (older entries still load), so
the same coordinates in the Nether and the Overworld stay two waypoints, and other dimensions are hidden
unless you ask for them. Each line gains a height delta (`▲12` / `▼30`) next to the bearing arrow and
distance.

**BuildCapture — the missing half of the build assist.** The assist could read schematics but not make
one. Now: press the corner key where you stand, press it again at the other corner, press save, and the
cuboid becomes a `.foxbuild` in `config/foxclient/schematics/` loaded straight into the hologram, layer
stepping and material list. Air is skipped, the volume is capped at 250,000 blocks so a stray corner
cannot freeze the game, and it reads blocks and writes a text file — you still place every block yourself.

**AppleUI grows a hotbar.** Both packs now also replace `hotbar.png` (dark plate, framed cells),
`hotbar_selection.png` (an **apple-green frame with a stem and a leaf** around the selected slot — this is
the highlight itself, so it is the correct place for that idea), `armor_empty/half/full.png` (gold apple
pips) and the experience bar trough and fill. `SelectionApple` now steps aside by default when a UI pack
is drawing the apple, so you never get two.

**A fourth theme: Nocturne** — near-black chrome, one hot accent, square corners.

**Not in this build: the audio visualiser/ambience.** Capturing what the client is playing means
bundling an audio decoder and running our own capture thread; "per-biome ambience" is sound design rather
than code, and neither belongs in a rushed build. It is the one item from your list I am pushing to 3.2
rather than shipping half of.

**On the two reference clients:** the direction — darker body, denser rows, small-caps labels, a single
glowing accent — is what Nocturne is built from. The palettes, the layout code and the art are ours; no
asset, colour table or screen layout was taken from either of them.

What is new in 3.0.1
--------------------

**The fair-play page no longer opens itself.** It was pushed in front of you 60 ticks after the first
join; now nothing interrupts you - the page is still there, still lists what this client refuses and
what it ships that a server may call cheating, and is one click away in the ClickGUI footer
(`Fair play...`). Removing the interruption is not the same as removing the content, so the content
stayed.

The twelve refused modules stay refused, and so does Velocity. That list is not a settings screen: it is
the boundary of what this client is. Everything else on request - HUD, cosmetics, packs, themes,
macros, telemetry, the build tools, the analysis - is fair game and gets built.

What is new in 3.0.0
--------------------

**The health row has company: a green apple and a bunch of grapes.**

* **SelectionApple** (`RENDER`) draws a green apple *around* the hotbar slot you have selected - an
  outline, not a fill, so the item inside stays readable: an ellipse ring, a stalk and a leaf, laid out
  from vanilla's own hotbar coordinates (`width/2 - 91`, `height - 22`, 20 px a slot) so it lines up at
  every GUI scale. Colour, radius, stem and an optional pulse are settings.
* **GrapeCrosshair** (`COSMETIC`) replaces the crosshair with a 15x15 bunch of grapes. It is done as a
  **built-in resource pack**, not a HUD overlay: the HUD is extracted by vanilla and our elements are
  appended after it, so a crosshair drawn there would sit on top of the vanilla one and you would see
  both. Overriding the `hud/crosshair` sprite replaces it at the source - one texture, no mixin, and it
  goes away cleanly when the module is switched off. The bunch keeps one bright pixel exactly on the aim
  point.

Both ride the same pack machinery 2.10.0 introduced (now shared through `util/PackSelection`), so
enabling either one selects its pack and reloads resources in place - no restart - and disabling hands
the pack back.

**Faster where it was slow, honestly.** `SpeedCrystals` gets **Turbo** (default on): instead of one
action per interval it pops everything already in range and places up to *Place per tick* (default 2)
crystals each tick, skipping bases it has already used that tick so it never wastes a placement on the
same obsidian. `AnchorMacro` gets its step delay default down from 3 ticks to **1**, the post-detonate
cooldown down from +2 to +1, and the item-swap wait from 2 ticks to 1. Both are still packet/blatant
modules with their fair-play labels intact - faster is not the same as hidden.

**Velocity is not in this build, and will not be.** It is the same feature as AntiKnockback, which is
one of the twelve named on the fair-play page: it changes your movement as the server sees it, which is
exactly what movement checks are built to find, and nothing about how it is implemented changes that.
What the client does offer is the honest half - `FightTelemetry` reports the knockback you actually took
per hit (average and peak) so you can see the number instead of guessing at it.

What is new in 2.10.0
--------------------

**AppleUI - a resource pack in the Misc list.** Two packs ship inside the jar and one module switches
between them, so there is nothing to download, unzip or enable by hand:

* **Apples** - hearts are apples, absorption and golden hearts are golden apples, hunger is a wheat
  sheaf, and the groove behind the health row is a dark red bar, so a full row reads as a red bar with
  fruit on it. Status-effect hearts keep their colour (green poison, grey wither, cyan frozen) so a
  potion still tells you something.
* **Bar** - the same idea with the fruit removed: the health row is a plain red bar, gold for
  absorption.

Both carry the block, item and sound overrides from the upload as well: respawn anchors look and sound
like composters, glowstone is a hay block, obsidian is a mycelium cube, the end crystal is a golden
dandelion, the four spear items get their models, and fire gets the ground model.

How it works: the packs are registered with Fabric's resource loader as built-in packs living at
`resourcepacks/apple_ui` and `resourcepacks/red_bar_ui` inside the mod jar, with `pack_format` 84 (this
version's resource format) and a compatibility range so they are not flagged. Switching the module on
adds the chosen pack to the game's selected list and triggers the normal client resource reload -
immediate, no restart; switching it off hands the pack back unless you turn that setting off. They are
also visible in **Options > Resource Packs** like any other pack.

The HUD sprites are drawn from scratch for this client at 9x9, the size vanilla draws hearts and hunger
at: a lit apple with a leaf and one specular pixel, a golden variant, wheat with three pairs of kernels,
and a solid bar tile - which is what makes a row of them read as one continuous bar, because vanilla
lays the sprites out on an 8px pitch and neighbours overlap by a column. Sprite names are taken from the
26.1.2 client jar (`hud/heart/*`, `hud/food_*`), including the hardcore and `_blinking` variants.

One repair worth naming: the uploaded `fire.json` had inverted brackets in two places (`"multipart": ]`),
which made the file invalid JSON, so nothing in it would have loaded. It is fixed before packing, and
`tools/appleui_pack.py` regenerates the whole pack (including the sprites) from source if you want to
change a colour.

What is new in 2.9.1
--------------------

**The anchor macro actually runs a loop now.** Its state machine advanced on the tick it sent a packet
and only looked for the result *Step delay* ticks later - three ticks by default. On any server with
more than about 60 ms of latency the anchor had not arrived in the client world yet, so the macro
concluded "no anchor appeared", went back to `PLACE`, and tried to place into a cell the client still
believed was empty. The server rejected that, and the whole thing span in place. Three changes:

* every step now has a **send** phase and a **confirm** phase, and confirmation waits up to
  *Settle timeout* ticks (20 by default) before anything is retried;
* a step is retried *Retries* times (2 by default) at the same cell before the cell is thrown away;
* **the item swap is checked in your hand before the click goes out.** A container swap lands a tick or
  two later, so 2.9.0 could click while still holding the previous item - which placed the wrong block -
  and `Auto switch` off meant it clicked with whatever you happened to be holding. It now refuses to
  click unless the main hand holds an anchor (or glowstone, for the charge step).

The HUD names the phase (`confirming placement 7/20`), the retry count, and whether the anchor is in
hand, so a stall is legible instead of silent.

**Corner radius now goes to 0, and 0 is the default for the custom theme.** At small radii the glass
pass left a few 1-pixel artefacts: the second sheen streak had a hard vertical edge, the outline drew
four corner specks, and the rim light drew a degenerate line when the radius had eaten the edge. The
streak is gone, the specks are gated behind a real radius, and the rims are skipped when there is no
edge left to light. The custom theme starts at radius **0** - square, no corner marks - and the slider
runs 0-24 if you want the old look back.

What is new in 2.9.0
--------------------

**Macros can place blocks now, and run at any speed.** The recorder captures the two non-combat mouse
buttons as well as keys: **use / place** (right) and **pick block** (middle). So holding right click
along a wall, a bridging pattern, a potion-then-pearl sequence, a hotbar shove — all of it records as
you did it and replays through the same vanilla key-mapping state. A new **Speed** setting scales the
whole macro between 0.25x and 4x, so a slow careful recording can be replayed fast after you have it
right. Two things to know, both printed in the module's info card: a placement step acts on the block
**you** are looking at when it plays (it replays input, not position — it is not an auto-builder and
does not aim), and the **attack** button is still never captured or replayed, because a recorded
left-click stream is an autoclicker with extra steps.

**The palette is yours now.** The footer chip cycles Orchid / Glacier / Ember / **Custom**; right click
it and a small editor opens with six sliders — accent hue, saturation and brightness, body hue and
darkness, and the corner radius. Everything else is derived: borders, cards, the glow, the ten preset
swatches and all three text inks come from those numbers in `Theme.buildCustom`, so the palette cannot
be made unreadable by accident, and the editor draws itself with the palette it is editing, which makes
it a live preview. Values save to `ui.json` as you drag and are rebuilt at startup.

**A glassier interface.** `Glass.glassPanel` is now the one panel primitive: two accent-tinted glow
rings, a drop shadow, the frost border, a vertical gradient body, a diagonal sheen across the top
third (the layer that actually reads as glass), a rim light — bright specular on top, accent-tinted
underneath, faint white down the sides — and four corner brackets in the accent. Module pills get a
gradient body and a single bright pixel row along their top edge. The ClickGUI panel, its settings
drawer and the fair-play page all use it.

What is new in 2.8.0
--------------------

Three tools that make you better instead of fighting for you. All three are read-only with respect to
the game: none of them attacks, aims, moves you, or sends anything a normal client would not send.

**Macro Studio** (`MISC`) - record your own input sequence, play it back on a key. Four slots, exact
tick timing, saved in `config/foxclient/macros.json`. See 2.9.0 for the use/place button and the speed
control. Limits that are part of the design, not gaps: the **attack** button is never captured or
replayed; only inputs currently bound to a vanilla control are recorded (so typed letters never end up
in a macro); and no typed text is replayed, so chat commands are out - which is also why recording and
playback stop the moment a screen opens. Playback aborts on damage, on a screen, when the world goes
away, or when you switch it off, and releases everything it pressed on the way out.

**Fight Telemetry** (`COMBAT`) - the answer to "why did I lose that trade". It reads the moment
vanilla starts a swing, the entity vanilla's own pick ray chose, your velocity and your health, and
reports clicks per second, the gap between hits with its spread, the reach at each hit (eye to hitbox,
the distance a server measures), a strafe score from how perpendicular your movement was, and the
knockback you took. When a fight ends it writes one summary line to the log and shows it on the HUD
for a few seconds. It cannot change an outcome, only explain one.

**Ghost Replay** (`MISC`) - run a jump, a strafe pattern or a whole fight in a world you host, and the
client keeps one sample per tick (position, facing, grounded). Turn it on and the run draws back as a
thin ground path plus a marker that moves at the speed you moved, with your distance behind it on the
HUD - so "was that strafe faster?" becomes a number. Recording is restricted to worlds you host and
only your own player is ever sampled.

Behind them, one new helper (`util/JsonFile`) owns the read/write/backup for the two files, so a
corrupt `macros.json` or `ghosts.json` is moved aside instead of taking the client down.

What is new in 2.7.1
--------------------

A layout fix for the fair-play page. Its scroll region was the one place in the client that converted
its clip rectangle to framebuffer pixels before clipping, while everything else — and the text itself —
works in GUI coordinates. Above GUI scale 1 that put the clip window inside the text, so every line lost
its left two thirds and the paragraphs read like fragments. The page no longer clips at all: it culls
whole lines by range, so a line is either drawn in full or not drawn, and nothing can be sliced
mid-glyph at any GUI scale or window size.

Also in this build: the page draws the panel, border and radii from the active theme instead of two
hardcoded colours, the panel and its margins stay on screen in small windows, and the "scroll for more"
hint only appears when there is actually something to scroll.

What is new in 2.7.0
--------------------

**Three themes, and the interface now actually follows one.** Every colour the UI uses lives in
`Glass`, and `Theme` copies a palette into it: panel bodies, borders, the glow behind a panel, the
gradient, card fills, text inks, text inks' two dimmer steps, and even the two corner radii. The
ClickGUI footer chip **Theme: …** cycles them live and the choice is saved in `ui.json`; the palette is
applied at startup before anything draws. Since 2.9.0 the cycle has a fourth entry, **Custom**, built
from six slider values (accent hue/sat/brightness, body hue/darkness, radius) edited in the theme editor
that opens when you right-click the chip.

* **Orchid** (default) — deep plum glass, orchid accent, softer 12 px corners. Darker and more
  violet than the old look, with the same legibility rules.
* **Glacier** — the original ice-blue palette, kept as a choice rather than deleted.
* **Ember** — charcoal with a warm amber accent.

These are our own values, not sampled from another client. If you want a fourth, the palette record is
one line of colours and a name.

**Cosmetics rebuilt around original shapes.** The cone hat is gone — the module is deleted, not hidden —
and two original cosmetics replace it:

* **PrismHalo** — two thin elliptical bands at different inclinations, counter-rotating, with a
  configurable number of motes riding the outer band on a two-colour arc gradient, plus a short tail on
  each mote so the direction of travel reads. Lines and points only, so it never becomes a glowing blob
  in a fight.
* **RunicRing** — an angular ring of radial tick marks at a player's feet, every fourth one longer, with
  an optional counter-rotating ring of dots outside it. Reads as a thin ring at distance instead of a
  disc.

Both go through `WorldDraw` (vanilla gizmos in world space), cost a handful of coloured segments,
never touch matrices or buffers, and are client-side: nobody else sees them.

What is new in 2.6.0
--------------------

**NameHider** (Misc, default off). Replaces chosen player names before they reach the screen — for
streaming, and for not exposing whoever is in your party.

* **Three hooks, one job.** The tab list (`PlayerTabOverlay#getNameForDisplay`), floating name tags
  (`EntityRenderer#getNameTag`), and the `Component`/`String` overloads of `GuiGraphicsExtractor#text`,
  which is where the scoreboard sidebar, chat and this client's own HUD all end up. Each hook is in its
  own **optional** mixin config, so a rename degrades to one surface not being hidden instead of a crash.
* **Styles survive.** A component is rebuilt segment by segment with its style intact, so a `[MVP+]`
  prefix keeps its colour while the name becomes the placeholder.
* **Whole-name matching.** Hiding `Alex` leaves `Alexander` alone, and `[MOD] Alex` is still caught.
* Names come from **your own player entity** (so this works offline) plus
  `config/foxclient/hide-names.txt`, one per line, `#` comments, re-read on save. Placeholder is
  configurable, default `Player`.
* **Stated gap:** text a screen pre-flattens into a `FormattedCharSequence` is not covered. Vanilla does
  not do that for the tab list, sidebar, chat or name tags, which is why they are.

**OrePredict** (Render, default off) — the seed/ore item, built as the honest version.
It does **not** read blocks the server hides and does **not** derive terrain from a seed. It asks your
own **integrated server** for one chunk at a time at `ChunkStatus.FULL` (the status whose feature pass
writes the ores, including cross-chunk writes), scans the non-air sections for 19 ore/block types, and
draws the results. So:

* **singleplayer or a LAN world you host** — it works and the status line shows the world seed and how
  many chunks it has scanned;
* **any remote server** — there is no server level in your process, so it does nothing and says
  `no local world - this client is not hosting`.

Budget: one chunk in flight at a time, one per tick, nearest first, chunk scanned set so nothing repeats,
`Max markers` cap (1024 default), 120-tick timeout if generation never answers, markers cleared on a
dimension change or when the module is switched off. It is a map of a world you already have — not an
x-ray, and not something that can cost another player anything.

What is new in 2.5.1
--------------------

**Blatancy is now labelled everywhere, not just on the fair-play page.**

* Every module pill in the grid carries a coloured edge by risk: red `sends packets`, pink `very
  obvious`, amber `info advantage`, nothing for visual-only. The grid itself now reads as a risk map.
* Opening a module puts a banner under its name, before its settings: `BLATANT · sends packets · servers
  ban for this`, `BLATANT · very obvious on screen`, or `INFO ADVANTAGE · you could learn this by hand`.
* The fair-play page's first section is now headed **Blatant modules — not in this client, on purpose**,
  and every one of the twelve is tagged `BLATANT` on its own line.

That is the honest version of "tell the players it's blatant": the label goes on everything this client
actually contains, and the twelve it does not contain are named and tagged rather than quietly shipped
with a warning nobody reads.

What is new in 2.5.0
--------------------

**The fair-play page.** On first launch — three seconds after you join a world, so it never lands on a
loading screen — you get one page with three sections, and it does not come back once closed. It is
reopenable any time from the ClickGUI footer chip **Fair play…**.

1. **Not in this client, on purpose.** The twelve features that were asked for and are not here: `Reach`,
   `Backtrack`, `AntiKnockback`, `Criticals`, `AutoTrap`, `AutoWeb`, `AutoClicker`, `CrystalOptimizer`,
   `AntiTrap`, `AutoMace`, `AutoCart`, `AxeShieldBreaker` — each with the one-line reason. They are all
   either combat/movement changes or interaction packets, i.e. visible to the server by construction.
2. **In here, and still a risk on public servers.** Every module whose risk label is above *visual only*,
   with its label and whether it is on right now — including the honest note that BlockRadar, ESP and the
   radars read only what the server already sent but are still called cheating by most servers' rules.
3. **No setup is undetectable.** Stated plainly, because it is true: there is no button anywhere in this
   client that tunes a feature to be harder to notice.

**Two one-click setups on that page.** *Recommended setup* switches **off** every module that is more
than visual and switches **on** four read-only HUD elements (Watermark, Keystrokes, ItemCounter,
ArmorHUD). *Everything off* is the panic button. Neither of them "makes cheating safe" — they only remove
capability, and the page says so in as many words.

**Sound Radar tells the truth about what it knows.** Confidence was printed as a percentage, which read
like a measurement. It is a heuristic score, so the cluster line now says `strong clues` / `some clues` /
`faint clues`, and attribution is a **separate** label: `player-made?` only when the cluster contains
placement, combat or container sounds, otherwise `actor unknown`. The threshold setting is now called
*Min score* and says "not a probability".

**BuildAssist shows materials.** An item-level table per build: `need · have · miss` for the top N block
types, red when a block is not a placeable item at all. It is labelled an estimate because a double slab
needs two slabs and a bed/door/tall plant counts once — those depend on block *state*, which our reader
does not keep, and the module says so rather than being quietly wrong.

Not built this round, with reasons: **name hider** (it needs three separate render hooks — sidebar text,
tab list, nametags — and I want its own test pass rather than three untested mixins), and **seed-based ore
prediction** (large, and it is hidden-block reading; if it happens it will be prediction-only, off by
default, with a seed you supply).

What is new in 2.4.0
--------------------

**Freecam freeze, now with a layer that needs no mixin at all.** The freeze now works by *substituting
the player's input object*: `LocalPlayer#input` is a public field, so while the body is frozen it is
pointed at a private `ClientInput` subclass whose `tick()` writes `Input.EMPTY` and a zero move vector.
No injection, no reflection, no mapping risk — the local player reads its movement from that object every
tick, so no key press can become movement. The original input object is restored when freecam turns off or
when the player entity changes (respawn, dimension change). The mixin and the position write-back from
2.3.0 are still there underneath, so the body has three independent reasons not to move. The status line's
`freeze ok` now means the substitution is live.

*(That technique came from your upload — `BuilderNavigator` in the AutoBuilder set steers the body the
same way. The mechanism is the idea we take; the implementation above is ours, and their executor is
refused, see below.)*

**StaffWatch 2.0 — evidence labels instead of a name list.** The module now reports *how* it knows, and
pins identities:

* **Per-server lists** — `config/foxclient/staff-watch.txt` is the global list;
  `staff-watch.<server>.txt` is used instead on that one server when it exists and is not empty. Both
  re-read on change.
* **UUID pinning** — the first time a watched name is seen its UUID goes into
  `staff-watch-uuids.json`. Later matches use the UUID, so renames keep working and a different player
  who takes an old name is **not** flagged as that person.
* **Four states, strongest first:** `near` (a player entity with that UUID within `Nearby` blocks,
  `invisible` flagged), `spectator` (the reported game mode), `not in tab` (profile still in the
  player-info map but missing from the tab list — what vanish usually looks like), `listed`.
  Proximity is sampled every 5 ticks; each row is one line on the HUD in the colour of its state.
* **Still no probing.** No command-completion fishing, no tab-list requests, no packets of any kind, and
  nothing automatic on a detection. Vanished, far-away or never-sent staff remain invisible — that is
  the honest limit, and it is stated in the module's info card.

A full review of everything you uploaded — what is worth taking, what I refuse and why, including the
GPL-3.0 and CC0 licensing split — is in `docs/UPLOAD-REVIEW.md`.

What is new in 2.3.0
--------------------

**Freecam, take three — and this time the body really is held.** 2.2.0's freeze hook was attached to
`ClientInput#tick`. The local player's input object is a **`KeyboardInput`**, which *overrides*
`tick()` and never calls `super`, so that hook was dead code: your body kept walking while the camera
flew. Two things are fixed:

1. **The freeze is now on `KeyboardInput#tick`, and it is triple-layered.**
   * `KeyboardInputMixin` cancels the tick and writes `Input.EMPTY` / `Vec2.ZERO` into the input
     object, so a key press can never become movement.
   * `FreecamModule` writes `Input.EMPTY` into `player.input` every client tick, which also clears
     presses that were **already held** when you switched freecam on (cancelling the tick alone would
     have frozen the last held state in place).
   * Belt and braces: the body's position, delta movement, rotation and fall distance are written back
     to the values captured at enable, every tick. Even a water current, an explosion or a piston
     cannot walk the body out of position.
   The ClickGUI drawer's status line now prints `look ok · freeze ok` when both hooks ran, and
   `freeze held` when only the position layer is holding the body — so a repeat of this bug shows up
   as one word, not as a conversation.

2. **Turning is at normal speed.** 2.2.0 scaled the mouse delta by `turnPlayer`'s only parameter,
   which is a *frame delta*, not sensitivity — the camera turned roughly ten times too slowly. The
   scaling is now copied line for line from vanilla: `f = (sensitivity * 0.6 + 0.2)^3 * 8`, deltas
   times `f`, the `invertMouseX`/`invertMouseY` options applied, then `Entity#turn`'s `0.15` degree
   factor. Freecam look speed and direction are now identical to normal look.

**New module: StaffWatch** (Misc, default off). Reads the tab list the server already sent you and
answers "is anyone on my watch list online?".

* **Watch list** — `config/foxclient/staff-watch.txt`, one name per line, `#` comments ignored, case
  insensitive. The file is created with a comment template the first time the module runs; it is
  re-read when it changes on disk, so names can be added without restarting. **No names ship with the
  client** — the list is yours to fill in.
* **Rank tags** — if the server puts a staff rank in the tab entry's display name (`[MOD]`, `[ADMIN]`,
  `[HELPER]`, `[OWNER]`, …), that text is matched against a **whole-word** list of staff words, so a
  player called `Modzilla` is not flagged.
* Matched names get a red HUD block; a banner appears for a few seconds when a new one turns up, and
  the match can be written to the game log.
* **Limits, stated plainly:** a staff member who is vanished, not in the tab list, or using a nickname
  is invisible to it. It also has **no automatic action** — no auto-disconnect, no auto-disable, no
  packet. It reports; you decide.

**Also in 2.3.0:** `NoFog`'s sky-dome fix from the previous build is included (see below), and the
freecam distance clamp is unchanged at 16 blocks by default.

What is new in 2.2.0
--------------------

**Freecam, rebuilt after the corruption report.** Two things were wrong and both are gone:

1. **The giant triangles.** 2.1.0's `CameraMixin` called `Camera#captureFrustum()` to "refresh
   culling" from the moved camera. That runs before the frame's projection matrix exists, so the
   culling frustum was built from the previous frame's matrices — chunk visibility went haywire and
   the sky pass was drawn with a broken frustum. That call is deleted; vanilla derives culling from
   the same `Camera` object we move, so re-capturing was never needed.
2. **The sky dome itself.** 2.1.0's `NoFog` also pushed `skyEnd` and `cloudEnd` out to 512 blocks.
   Those two values are the fog that hides the edge of the sky dome; without it the dome's geometry is
   visible as giant triangles with black seams, which is the other half of what the screenshot shows.
   `NoFog` now touches only the environmental and render-distance bands, capped at 192 blocks, and
   `skyEnd`/`cloudEnd` are left exactly as vanilla computes them. Default distance dropped to 128.
3. **"It unloads chunks".** Chunks exist around the *player*, never around the camera. Flying an
   unlimited distance left the loaded world behind. The camera is now clamped to `Distance` (default
   **16** blocks, max 48) from your body, so you are always inside the loaded region; when you hit the
   limit the status line says `at the distance limit`.

**The body was supposed to stop moving here — it did not, see 2.3.0.** While freecam is on:

* movement input is neutralised at `ClientInput#tick` *(wrong class: `KeyboardInput` overrides that
  method, so this did nothing — fixed in 2.3.0)* — the body stands still, and no movement is ever
  produced, so nothing is sent;
* mouse look is intercepted at `MouseHandler#turnPlayer` and applied to the **camera** instead of the
  player, so your body keeps facing where it was;
* mouse buttons are swallowed while no screen is open, so you cannot attack or use from a body the
  server thinks is standing still.

Both input hooks live in their own optional mixin config (`foxclient.input.mixins.json`), because a
silently limp freecam is better than a client that will not boot: if a future 26.1.x renames those
methods, the camera still flies and the ClickGUI drawer's status line shows `look — · freeze —` so you
can see exactly which half applied.

What was new in 2.1.0
---------------------

**Six more HUD modules**, all client-side reads:

* **Watermark** — brand, ping, frame rate and clock in one line. Ping is the value the tab screen
  already shows, not a probe.
* **Keystrokes** — W/A/S/D with optional LMB/RMB, a space bar and a sneak row; each cell lights up from
  the same `KeyMapping` the movement code reads.
* **ItemCounter** — live counts for totems, golden apples, enchanted golden apples, ender pearls, end
  crystals, respawn anchors, glowstone, obsidian and XP bottles, from your own inventory.
* **TargetHUD** — name, health bar (absorption drawn as a second segment), hp numbers, distance and
  armour for whatever the crosshair is on, falling back to the nearest player inside a radius.
* **ArmorHUD** — the four armour slots with durability bars and optional percentages.
* **TargetEsp** — a breathing box on the entity vanilla's own pick ray already selected. It never scans
  for entities, so it cannot show you anything you could not have clicked.

**The now-playing card, rebuilt.** The Spotify HUD is now a card: green disc, title, artist, the lyric
line that is playing (or `no lyrics`), and the **progress bar underneath all of it** with elapsed and
total time at the ends — the arrangement from the screenshot you sent, drawn with our own shapes (a
disc and three bars, not their logo asset). `World Lyrics` gained a **Position** setting: `Under the
Spotify card` (default) or `Centre of the screen`. Under the card it reads as one block with the
player; centred it behaves like subtitles.

**The ClickGUI, restyled.** Rail of categories with live counts, big category name with a
`N modules · M on` line, search on the right, then two-column **module pills**: name, star, switch,
chevron. **Left click toggles, right click opens settings** in a drawer that slides over the grid —
that drawer is the deliberate difference from the reference, which has no settings surface at all.
Footer chips and the key hints sit where they do there, with our own keys.

What was new in 2.0.0
---------------------

The rename, and nothing else: the client is now **FoxClientv2.0** (version 2.0.0). No behaviour
changes, no new modules, no config migration — the mod id stayed `foxclient` precisely so this is a
drop-in swap. The previous build is kept in `artifacts/foxclient-1.6.0.jar`.

What was new in 1.6.0 (kept for the record)
-------------------------------------------

**A Cosmetics category** — four client-side modules that change nothing but what you see:

* **PrismHalo** - two counter-rotating inclined bands with motes and an arc gradient above players'
  heads (2.7.0, replaced the cone hat).
* **RunicRing** - a ring of radial tick marks at players' feet with an optional outer dot ring.
  gizmo lines in world space.
* **JumpRings** — a ring that expands where you jumped from and fades out; rings stay where you left
  them because they are world geometry, not screen decals.
* **Trail** — end-rod or cloud particles dropped behind your feet, rate-limited, spawned on your own
  client through `ClientLevel#addParticle` (nothing is sent to the server).
* **HitSpark** — crit sparks where anything nearby takes a hit. It *reads* `hurtTime`; it never
  attacks anything.

**Two visual modules with optional mixins:**

* **NoFog** — pushes the engine's fog out (water, lava, powder snow, the render-distance band, and
  optionally the sky/cloud boundaries) by editing the `FogData` fields right before they are uploaded.
* **SkyTint** — overwrites the sky colour the sky extractor produces, with a brightness scale so a
  dark tint stays usable.

Both live in a **separate optional mixin config** (`foxclient.optional.mixins.json`, `@Pseudo`,
`require = 0`, `defaultRequire: 0`). If 26.1.3 moves `FogRenderer#updateBuffer` or
`SkyRenderer#extractRenderState`, the mixin is simply not applied, the game still starts, and each
module's status line says `hook not applied in this build — no effect` instead of lying to you.

**A HUD editor.** Press **P** (rebindable) or use ClickGUI → Config → `Edit HUD…`: the HUD block is
draggable, snaps to whichever corner it is nearest, and the anchor is stored in `ui.json`. Every HUD
line now draws at `HudManager.lineX()` instead of a hardcoded `x = 4`, so the whole block moves
together.

**Startup splash polished:** drifting embers behind the card and a rotating tip line, including the
new keys.

**Provenance.** The four cosmetic modules, the fog/sky hooks and the HUD editor were written from
the 26.1 API surface in this repository. A reference dump of another client's sources was read for
*ideas* only — no file, class, method or comment from it was copied, and the two share no code paths
(that client targets an older Yarn-mapped version and draws through its own projection stack; this
one goes through vanilla gizmos and the render-state extractors).

What is new in 1.5.0 (kept for the record)
------------------------------------------

**Music in the client.** Two new modules and a shared media layer:

* **Spotify** - now-playing bar across the top of the screen with title, artist, elapsed time and a
  progress track, fed by the Spotify Web API over a PKCE login you authorise in your browser. **J**
  opens the playing track in your browser.
* **World Lyrics** - the current lyric line, centred over the world, from lrclib.net's time-synced
  LRC. Next line preview, width, offset, colour and backdrop are settings.
* **Discord presence** - a switch that pushes the track to the Discord desktop app over its local
  IPC socket, using your own application id. No bot, no library, no login.

**Anchor macro actually works.** Three separate causes, all fixed: it targeted the block the target
was standing in (vanilla refuses to place into an entity), it clicked the block below the cell with
`Direction.UP` even when the cell was wall-supported (the use-item hit air), and `Hold right click`
was on by default. Every step is now verified and the HUD names the exact blocker. Details under
"Combat" below.

**AI Assistant pane.** A chat-shaped feature finder over the module list - local, deterministic, no
network - that can also read live state (why the anchor macro is idle, what is playing, which
modules send packets). Undo / Clear / Copy and a "Completed N configuration changes" line, as in the
reference.

**UI rebuilt to the 2.6.2 shape.** Rail with FEATURES/OTHERS groups and a signed-in card, full-width
search, two-column module cards with favourite stars and switches, a settings panel with an enabled
row, live status lines and module actions, and a footer with the client-wide switches and the
keyboard hints. Plus a real RGB/alpha colour picker (`ColorPickScreen`) so per-block and per-module
colours are no longer preset-only.

**Build assist moved packages.** `com.foxclient.build` became `com.foxclient.schematic`; the old
directory name collided with Gradle's `build/` output on some setups and silently disappeared from
clean checkouts.

What is new in 1.4.0 (kept for the record)
------------------------------------------

**Combat category.** Three new modules, and the first ones in this client that talk to the server:

* **SpeedCrystals** - "speed crystals": while you hold right click it cycles place -> attack on the
  nearest target, every *Interval* ticks, so crystals are placed and popped continuously. Needs
  obsidian or bedrock next to the target and end crystals in your inventory; it swaps them into
  your held slot with a container click, aims at the block (`setYRot/setXRot`, which rides along
  with the next movement packet) and calls `useItemOn` / `attack`.
* **AnchorMacro** - the anchor loop: place respawn anchor -> charge with glowstone -> detonate ->
  repeat, with a per-step tick delay you can tune. Needs anchors and glowstone.
* **AutoTotem** - scans your inventory and swaps a totem of undying into the offhand whenever the
  offhand is not holding one, optionally only below a health threshold.

All three are `sends packets` risk and off by default. They have no friend list, no
self-damage guard and no anticheat evasion — they are exactly what they say they are.

**New UI.** The panel is now the sidebar layout: fixed left rail (categories with counts, then
Keybinds / Config / About panes), content column with a title, a live `N modules · M enabled`
subtitle, a search field and All/Enabled chips, then a stack of module cards with a chevron and a
toggle. Selecting a card opens its settings in a right-hand panel; with nothing selected that
panel shows the empty state. `packets` tags mark the combat modules on the cards themselves.

**Freecam is smooth now.** Input is sampled at the tick rate but the camera is placed per frame with
partial-tick interpolation, and movement is accelerated and damped instead of snapping to full
speed. There is also a `Refresh culling` switch: it is the only part of freecam that touches
vanilla render state, so it is the first thing to try if you see geometry oddities while flying.

**World drawing is now validated.** Every box, line and point goes through a filter before it
reaches the gizmo system: finite coordinates only, boxes between 1e-4 and 65 blocks per axis, lines
under 512 blocks. Anything that fails is dropped and attributed to the module that sent it, a
module that keeps failing has its world drawing switched off for the session, and the GUI's **About**
pane lists everything that was rejected. A bad number can now cost you one missing shape instead of
a broken frame.

What is new in 1.3.0 (kept for the record)
------------------------------------------

**Snow-fox glass UI.** The whole palette was rebuilt around a snow fox at night: deep translucent
blue-black glass, frost-white specular edges and one ice-cyan accent. Panels are deliberately
semi-transparent (body alpha ~0.75-0.85) so terrain and falling snow show through, there is a
frosted inset style for cards, and the ClickGUI, the block-colour editor, the schematic picker and
the block/entity pickers all draw with the same helpers. Ornamental snowflakes in the corners,
tail tip white, no more orange.

**A keybind on every module, edited in the GUI.** Each module owns a `KeybindSetting` as its first
setting. Click the row, press a key, done; right-click unbinds; DEL clears; ESC cancels. The bind
is drawn as a chip on the module row and saved with the rest of the config. Nothing is registered
with vanilla's options, so binds never collide with a real key conflict screen, and nothing fires
while a screen is open.

**Module info cards.** Selecting a module now shows a card above its settings: what it does, how it
does it, and a plain-language risk tag (`visual only` / `info advantage` / `very obvious`). The tag
describes how obvious the feature is to another player - it is not a promise of undetectability.

**BuildAssist - a Litematica hologram that never sends a packet.** See its own section below. It
reads your `.litematic` (or a plain-text `.foxbuild`), finds the blocks that are still missing,
draws them as a ghost hologram and walks you through them one at a time, telling you which face of
which block to click. You place everything yourself.

**Startup splash: a fox catching a fish.** 2.6 seconds, drawn over the title screen by
`ScreenMixin`: a snow fox waits on the ice, a fish swims past underneath, she leaps, dives, and
comes back up with it in her mouth while the loading bar fills. Pure 2D primitives - no textures,
no resource pack, nothing to install.

What is new in 1.2.0 (kept for the record)
------------------------------------------

**1. The world-corruption bug is gone.** In 1.1.0 the world-draw layer had two backends and a
fallback: when the gizmo collector was not available it wrote line geometry straight into the
frame via `RenderTypes.lines()`. Those raw vertices did not match 26.1's vertex layout, so the
GPU read colour integers back as positions - the result was the screenshot you sent: giant flat
triangles across the sky and terrain. That entire code path has been **deleted**. There is no
hand-written vertex or line backend left anywhere in the client. The only world-drawing API is
`Gizmos.cuboid/line/point`, which owns its own vertex format, so it cannot desynchronise with
the renderer. If the collector is missing in one event window, the client tries the other window
once and otherwise draws nothing - a missing box, never a broken world.

**2. Per-block colours in BlockRadar.** A new `ColorMapSetting` stores a `registry id -> ARGB`
map. BlockRadar keeps a `colourCache` indexed by block id, filled on refresh, so the per-frame
draw path does **zero** registry lookups. In the ClickGUI, open BlockRadar's settings and click
the **"Block colours"** row to get a centred editor listing every tracked block: left click
cycles the shared presets, right click drops back to the module default, and boxes on screen
recolour the instant you click.

**3. The UI is a proper glass surface now.** All of this is on top of the 1.1.0 work:

* both lists **scroll** (mouse wheel over the module or settings column) with thin scrollbars;
  rows outside the scissor rectangle are not even built, so nothing can be clicked through glass;
* **hover tooltips** on every module and setting, clamped to the screen edge;
* a **reset** chip in the settings header puts every setting of a module back to its default
  (`Setting` now carries `defaultValue`/`reset()`/`isDefault()`);
* per-category module **counts** in the rail, an "N modules active" status line, and a colour
  strip on the colour-map row so it reads as a door to the editor;
* a deeper glass stack: two soft shadows, a 1px border ring, a gradient body, a vertical sheen,
  a specular top edge and a faint inner bottom rim;
* one shared preset palette (`Glass.PRESETS`) used by both single colours and the block editor.

Twenty-five modules, all defaulting to **off**.

### UI - rail, pill grid, settings drawer
Right Shift opens a 900 x 560 panel (clamped to the window) on vanilla's blurred backdrop.

* **Rail:** snowflake and `FoxClientv2.0` / `v2.1.0`, then `FEATURES` (Favourites + Combat, Render,
  Player, Cosmetics, Misc, each with a live count), `OTHERS` (Keybinds, Config, AI Assistant), and a
  signed-in card at the bottom with your name, initial and an `Online` dot.
* **Header:** the open category in caps, `N modules · M on` underneath, and the search field on the
  right with a live match count.
* **Grid:** two columns of pills — name, favourite star, switch, chevron. **Left click toggles the
  module**, the switch does the same, the star favourites, and **right click opens the settings
  drawer** for that module. Scrollbar on the outer edge; rows outside the scissor are not built.
* **Drawer:** slides over the grid from the right with the grid dimmed: module name, `Enabled` row,
  wrapped description, a live status line for the modules that have one (anchor macro step and
  blocker, Spotify link and lyrics state), module actions (`Link Spotify`, `Open current track`,
  `Choose schematic…`), then the settings. Sliders drag, toggles flip, enums cycle, colours open the
  RGB/alpha picker, text rows take typing, keybind rows capture the next key. `Reset module settings`
  at the bottom, `×` to close, and every row also resets on right click.
* **Bottom:** `Packet modules off` and the profile chip inside the panel, the key hints under it.

One `Row` list is built per frame and read by both the draw pass and the click pass, so a control
cannot be drawn in one place and clicked in another.

### Spotify, World Lyrics and Discord - new in 1.5.0

**Spotify** (`MISC`) polls `GET /v1/me/player/currently-playing` and puts a bar along the top of the
screen: title, artist, elapsed time and a progress track. Press **J** (rebindable in Controls) to
open the playing track in your browser. Also bound in the panel: `Link Spotify` / `Unlink Spotify` /
`Open current track`.

Linking is a normal PKCE authorisation-code flow, done honestly:

1. create an app at `developer.spotify.com`, add the redirect URI `http://127.0.0.1:8890/callback`;
2. paste its **client ID** into `Spotify -> Spotify client ID` in the panel (a text row you click and
   type into - digits and lowercase letters are all it needs);
3. press `Link Spotify`. The client opens the consent page and starts a loopback server on
   `127.0.0.1:8890` to catch the redirect, exchanges the code for an access + refresh token and
   stores them in `config/foxclient/spotify.json`.
4. no client secret is involved and none is shipped - that is the point of PKCE.

**World Lyrics** (`RENDER`) takes that track, asks `lrclib.net` for a time-synced LRC, and draws the
line whose timestamp has arrived, centred over the world (offset, width, colour, backdrop and a
"next line" preview are all settings). Lyrics are looked up once per track and cached in memory. If a
track has no synced lyrics - instrumentals, brand-new releases, obscure uploads - nothing is drawn
and the module says so. The HUD text is screen-space: it never touches the gizmo world-draw path.

**Discord presence** (a switch on the Spotify module) pushes the track to the Discord desktop app
over its local IPC socket - `discord-ipc-0..9`, unix socket on Linux/macOS, named pipe on Windows -
with a hand-encoded `SET_ACTIVITY` frame and no third-party library. It needs **your own Discord
application id** (discord.com/developers -> New Application -> Application ID), typed into
`Spotify -> Discord app ID`. No bot token, no login, no OAuth. If Discord is not running the HUD and
the panel say `Discord not running`.

### AI Assistant - new in 1.5.0

The `AI Assistant` pane has the shape of a chat client (Conversation / Connection tabs, user and
assistant bubbles, suggestion chips, Undo / Clear / Copy, and a "Completed N configuration changes"
line) but **it is not a language model**. It is a local, deterministic matcher: it tokenises the
question, scores every module against its name, description, detail text, settings and a synonym
table (`"night vision" -> Fullbright`, `"ore" -> BlockRadar`, ...), and answers from that. It also
reads live client state, which a remote model could not: ask `why is anchor macro idle` and it
reports the actual step and blocker; ask `what am I listening to` and it reads the Spotify poll;
ask `which modules send packets` and it lists the ones with `Risk.PACKET`.

The Connection tab says all of this in the UI, including "Model: none" and "Network: none". Nothing
you type leaves the machine. A real model would need an API key and a network call, which this build
deliberately does not do.

### Fullbright (`RENDER`)
Raises the values inside the per-frame `LightmapRenderState`; nothing to restore, and your
gamma slider is never touched.

### BlockRadar (`RENDER`)
What changed since 1.0.0:

* **nearest-first drawing.** A distance-sorted snapshot is rebuilt every 10 ticks, so the draw
  cap now guarantees the closest blocks are the ones shown instead of an arbitrary subset.
* **per-chunk invalidation.** An unloaded chunk drops only its own hits and re-queues itself;
  1.0.0 threw the whole cache away and rescanned everything.
* **per-block colours** (new in 1.2.0) - each tracked block has its own ARGB, resolved from a
  cached array, with the module colour as the fallback;
* **HUD progress.** `blocks 412 scanning…` tells you the scan is still filling in.
* unchanged: budgeted scanning, `maybeHas` section skipping, primitive hit set, through-walls.

### ESP (`RENDER`)
* **colour by type** — players shift green→red with health, hostiles orange, passives green,
  items yellow, vehicles cyan;
* **distance fade** so the nearest target is the brightest thing on screen;
* **only visible** filter (line-of-sight check) for when you want realism instead of walls;
* spectators skipped, real frame partial tick for smooth boxes, per-frame HUD target list.

### Sound Radar (`MISC`)
The noise problem is fixed: `SoundCategory.IGNORED` now runs **first**, and anything a fish, a
rainstorm or a sheep can make is dropped before it reaches the event log — fish/salmon/cod/tropical/
tadpole/axolotl/squid/dolphin, bubbles, water, swim, splash, weather, rain, thunder, music,
ambient chatter, footsteps, leaves, grass, fire, portals and UI blips. Two more noise gates:
a **self radius** (default 4 blocks) so your own doors and chests never count, and the old
AMBIENT category is gone entirely.

Cluster lines now show which categories voted (`BASE? 62% 30m ↑ period 20t MECH+PLACE piston.extend`).
Doors, trapdoors, fence gates and iron doors joined MECHANICAL; anvils/enchanting are COMBAT.

### Freecam (`PLAYER`)
Detaches the camera and flies it with WASD / space / shift. Movement is accelerated and damped
(`Smoothing`) and the camera position is interpolated with the frame's partial tick in `CameraMixin`,
so it glides instead of stepping at 20 Hz.

* **`Distance` (default 16, max 48)** clamps the camera to a radius around your body. This is not a
  cosmetic limit: chunks are only loaded around the player, so staying close is what keeps the world
  rendering. Past it the camera stops and the status line says `at the distance limit`.
* **Position and rotation** are both set at `Camera#update` RETURN. There is **no frustum
  re-capture** — that was 2.1.0's bug and it is gone.
* **Freeze body** (default on) is triple-layered, because the single-layer version in 2.2.0 pointed at
  the wrong class and did nothing: `KeyboardInputMixin` cancels `KeyboardInput#tick` (the method that
  actually reads your keys — `ClientInput#tick` is overridden and never called), the module writes
  `Input.EMPTY` into `player.input` every tick so already-held keys are cleared too, and the body's
  position / rotation / delta movement are written back to the captured values as a last resort.
  Mouse look goes to the camera in `MouseHandler#turnPlayer` at vanilla sensitivity, and mouse buttons
  are swallowed while no screen is open.
* **Look speed** is vanilla's own formula, `(sensitivity * 0.6 + 0.2)^3 * 8` with the invert options
  applied, then `Entity#turn`'s `0.15` factor — so freecam turns exactly as fast as normal play.

The input hooks are in an optional mixin config, so a future rename degrades to "camera flies, body
walks, position layer holds it" instead of a crash — the status line reports `look ok · freeze ok`
when both ran, and `freeze held` when only the position layer is holding.

### StaffWatch (`MISC`) — new in 2.3.0

Answers "is anyone on my watch list online?" from the tab list the server already sent you.

* **Watch lists** — `config/foxclient/staff-watch.txt`, one name per line, `#` comments ignored, case
  insensitive, re-read when it changes on disk; `staff-watch.<server>.txt` overrides it on one server.
  **Nothing is pre-filled**: the lists are yours. Names are pinned to UUIDs on first sight
  (`staff-watch-uuids.json`), so renames keep working and a new owner of an old name is not flagged.
* **Rank tags** (default on) — matches staff rank words (`staff`, `admin`, `mod`, `moderator`,
  `srmod`, `helper`, `owner`, `manager`, `developer`, `dev`, `trial`) as **whole words** in a tab
  entry's display name, which is where servers usually draw `[MOD]` / `[ADMIN]`.
* Each match is one HUD line labelled with its strongest evidence: `near` (within `Nearby` blocks,
  `invisible` flagged), `spectator`, `not in tab` (profile present but missing from the tab list — what
  vanish usually looks like), or `listed`. A banner names anyone newly seen; `Log matches` writes it to
  the game log.
* **Read-only.** No packets, no name lookups, no auto-disconnect, no auto-disable. Vanished, off-tab
  and nicknamed staff are invisible to it — it is a tab-list reader, not a moderator detector.

### Waypoints (`MISC`) — new
Press **B** to drop a waypoint where you stand. Stored as `name|x|y|z`, drawn in the world with
an optional 48-block beam, and listed on the HUD nearest-first with bearing arrows and distance.

### Info (`MISC`) — new
Coordinates, FPS, dimension id and (off by default) biome.

### BuildAssist (`PLAYER`) - new in 1.3.0

* **Materials** (2.5.0) - a `need / have / miss` table for the top `Material rows` block types, counted
  with `SlotUtil`, red when a block has no placeable item. Explicitly an estimate: double slabs and
  multi-part blocks need the block state, which the reader does not keep.

A Litematica hologram you build by hand. Files go in `config/foxclient/schematics/`:

* `.litematic` - real Litematica files. Multi-region schematics are merged into one volume;
  both encodings are read (version 1 = one palette index per long, version 2+ = bit-packed with
  entries allowed to straddle a long boundary) and `Size` may be negative.
* `.foxbuild` - plain text, `x y z minecraft:block_id` per line. There is a worked example in
  `sample-schematics/fox-den.foxbuild`.

Open it from ClickGUI -> BuildAssist -> **Schematic file…**, click the file, hit **anchor here**.

How it guides you:

* missing blocks are drawn as a translucent ghost hologram (capped, through-terrain optional);
* the next block is a bright box with a line from your eyes, and the block you must click is
  outlined in white, with the exact face named on the HUD
  (`next: Oak Planks -> 104 64 -22 · click top of 104 63 -22`);
* ordering is bottom-up (lowest layer first, then nearest), so nothing you are told to place is
  floating;
* it re-plans every `Rescan interval` ticks, so the highlight advances the moment you place a
  block - no keys to press. `Layer only` restricts the hologram to the current layer;
* the HUD shows progress %, remaining blocks, the current layer, and which hotbar slot already
  holds the block you need.

**What it does not do, on purpose:** it never rotates your view, never swings, never moves items
and never sends a placement packet. There is no "auto place" switch to find, because the whole
feature is built around you doing the placing.

### Combat (`COMBAT`) - new in 1.4.0, sends packets

These are the only modules that change game state. All three default to off, all three are tagged
`sends packets`, and all three are trivial for a server to notice.

**SpeedCrystals** - every *Interval* ticks (default 1): pop the closest crystal within *Break range*
of your eyes with `attack`, otherwise find obsidian/bedrock with air above within *Search radius* of
the target (`Place range`), swap end crystals into your held slot, aim, `useItemOn(MAIN_HAND, ...)`,
swing. `Hold right click` (default on) means it does nothing unless you are holding the use key.

**AnchorMacro** - `PLACE` -> `WAIT_PLACE` -> `CHARGE` -> `WAIT_CHARGE` -> `DETONATE`, one step per
*Step delay* ticks, then it picks a new cell and starts again. **Fixed in 2.9.1**: the send and confirm
phases are separate (a slow server no longer looks like a failed placement), failed steps are retried
*Retries* times at the same cell, and the held item is verified before the click so it cannot place the
wrong block. **Fixed in 1.5.0** - it had three ways to do nothing:

1. the cell search used the block the target was *standing in*, and vanilla rejects placement into a
   space an entity occupies, so no anchor ever appeared;
2. the click always landed on the block *below* the cell with `Direction.UP`, which is air whenever
   the cell is held up by a wall - the use-item hit nothing at all;
3. `Hold right click` defaulted to on, so unless you were holding the use key it sat there.

Now: cells are rejected if an entity is inside them, ground-supported cells are preferred, the click
is aimed at the real supporting block's face, and every step is **verified** - a placement only
counts once the anchor block exists, a charge only counts once the anchor reports a charge level.
When the server rejects an action the HUD says `the server rejected the placement` instead of
looping silently. The HUD also shows anchor and glowstone counts and the current charge, and says so
plainly when you are in the Overworld, where a charged anchor sets spawn instead of exploding.

**AutoTotem** - every *Delay* ticks: if the offhand is not a totem, find one in the 36 inventory
slots and swap it in with one container click (`SWAP`, button 40 = offhand). `Only when low` gates it
on health.

Honest limitations: no friends list, no self-damage guard, no rotation smoothing beyond
`setYRot/setXRot`, no pathing, and no attempt to look less robotic than the interval you set. Reach is
vanilla 4.4 blocks. If the server rejects an interaction, the module simply tries again next tick.

### Cosmetics (`COSMETIC`) - new in 1.6.0

Render-only. Each one draws through a path that vanilla owns: gizmo lines for the hat and the rings,
`ClientLevel#addParticle` for the trail and the sparks. None of them sends a packet, reads your
inventory, or changes combat - the sparks fire off the client-visible hurt timer, which is a read.

Cost is bounded on purpose: the hat is at most 48 lines per player within 48 blocks, at most 12 rings
exist at once, the trail is rate-limited to the density you set, and the spark tracker drops its map
when it passes 256 entities. If you are chasing frames, these are still the first things to switch
off, because they are the only modules that run every frame in the world.

### Macro Studio, Fight Telemetry, Ghost Replay - new in 2.8.0

`MacroStudio` (`MISC`) records input edges with the tick delay between them and replays them by
calling `KeyMapping.click(key)` / `KeyMapping.set(key, pressed)` - the same mapping state the keyboard
handler sets, so vanilla's own `consumeClick` handlers run, including `keyUse`, which is what makes a
replayed right-click place a block. Nothing is injected into the window's event queue and no packet is
built by us. The capture pass scans GLFW key codes with `InputConstants.isKeyDown` and mouse buttons 1
and 2 with `glfwGetMouseButton`, keeps only codes that some `KeyMapping` currently resolves to, and
drops the module's own binds so a macro cannot trigger its own playback. **Speed** divides each
recorded tick delay, clamped to zero, so 4x on a 1-tick gap becomes same-tick. Limits: 640 steps, 90 s,
four slots, four macros; the HUD shows `REC slot 2 - 14 keys - 1.4s` and `MACRO slot 2 - 7/14 - 2.00x`.

`FightTelemetry` (`COMBAT`) uses the rising edge of `player.swinging` as the attack tick and
`client.hitResult` as the target, so it only ever sees what vanilla already resolved - a swing that
hit nothing counts as a miss. Reach is `eye -> nearest point of the hitbox`, averaged over the last 16
hits with the maximum kept. The strafe score is `100 * (1 - mean|angle-90|/90)` over ticks where you
were moving with a target within 6 blocks, and the straight-in count is ticks below 45 degrees.
Knockback is `deltaMovement.horizontalDistance()` on the tick `hurtTime` rises.

`GhostReplay` (`MISC`) samples `x, y, z, yaw, onGround` per tick, at most 2400 ticks per slot, three
slots, into `config/foxclient/ghosts.json`. Drawing decimates to at most 400 gizmo line segments and
a marker cross with a facing arrow, all through `WorldDraw` like every other world overlay here.

### Fog and sky - new in 1.6.0

`NoFog` and `SkyTint` are the only modules backed by hooks that vanilla does not expose as an event.
Both use the optional mixin config described above, which is why their status lines report whether the
hook is actually live. This is deliberately different from the required mixins the client already
ships (`CameraMixin`, `ClientPacketListenerMixin`, `LightmapRenderStateExtractorMixin`,
`ScreenMixin`): those are load-bearing for features that must not silently half-work, so they are
allowed to fail the launch loudly instead.

### Render architecture (rewritten for 1.2.0)
* **Only path:** `LevelRenderEvents.BEFORE_GIZMOS` + `Gizmos.cuboid/line/point` with
  `setAlwaysOnTop()` - vanilla owns the vertex format, so nothing here can corrupt a frame.
* **Retry, not fallback:** each frame calls `WorldDraw.beginFrame()`; if any gizmo submission
  throws `IllegalStateException` (no collector bound) `lastFrameOk()` goes false and the client
  moves world drawing to `LevelRenderEvents.END_MAIN` for the rest of the session, logging once.
  The worst case is boxes that do not appear; there is no second geometry backend.
* `WorldDraw` exposes `box`, `boxFilled` (a zero-alpha fill collapses to a stroke), `line` and
  `point` - and no `PoseStack` or `MultiBufferSource` anywhere.
* `RenderUtils` is colour maths only now (`argb`, `withAlpha`, `fade`, `lerp`, `brighten`,
  `toHex`); all per-vertex drawing was removed together with the old fallback.

### Known gaps
* **Freecam's input hooks are optional mixins.** If a future version renames `turnPlayer` or
  `KeyboardInput#tick`, the camera still flies and the position layer keeps the body where it stood,
  but held keys may bleed through for a tick; the status line shows which hook is missing. `Distance`
  is capped at 48 blocks for the same reason the clamp exists.
* **StaffWatch only sees the tab list.** A vanished, off-tab or nicknamed staff member is invisible to
  it, and nothing ships pre-filled: `config/foxclient/staff-watch.txt` starts empty by design.
* **No audio ping** in Sound Radar; constructing a `SimpleSoundInstance` is still unverified
  without a runtime.
* **Sound rules are not GUI-editable** — the table is in `SoundCategory`.
* **ESP labels are a HUD list**, not per-entity world text.
* **Profiling numbers are reasoned, not measured** — no GPU here.
* **Per-block colours are still edited as presets** in `ColorEditorScreen`; the free-form RGB/alpha
  picker (`ColorPickScreen`) is wired to every `ColorSetting`, not yet to the per-block map.
* **BuildAssist compares blocks, not block states.** Stairs/doors are checked by block type, so a
  correctly-placed-but-wrong-facing stair will not be flagged. Placement still works; verification
  is just looser.
* **BuildAssist does not path-find or scaffold.** If the next block has no solid neighbour it says
  so on the HUD and you have to make a support yourself.
* **The litematic reader has never been run against a real file in this environment** - there is
  no Minecraft here to boot. The bit-unpacking was verified standalone (all widths 2-64, including
  cross-long entries, 58,256 checks), and both the version-1 and version-2+ layouts are
  implemented, but if a file comes back with zero blocks the client logs a warning telling you
  exactly that. Use `.foxbuild` as the known-good path while testing.
* **Spotify needs your own client id, and that step cannot be automated.** No mod can ship a client
  secret, so PKCE + your own application is the only honest route. The refresh token sits in
  `config/foxclient/spotify.json` in plain text - do not share that file.
* **The top bar is not clickable.** 26.1 exposes no client-side mouse callback a HUD overlay can
  hook without swallowing every click in the game, so the link is on the **J** key and in the
  settings panel instead of on the bar itself.
* **Lyrics depend on lrclib coverage.** Instrumentals, very new releases and obscure uploads often
  have no synced lyrics; the module draws nothing and says so rather than showing words out of sync.
* **Discord presence needs the desktop app running and your own application id**, and it is written
  against the IPC/`SET_ACTIVITY` protocol by hand - it has never been exercised against a live
  Discord client here.
* **The AI Assistant is not an AI.** It is a deterministic keyword matcher: no model, no network, no
  API key, and it cannot answer anything outside this client's own module list.
* **The whole 1.5.0 media stack has never been run against live services in this environment** -
  there is no Minecraft and no Spotify account here. The URLs, OAuth parameters and frame layout are
  written from the documented protocols; test `Link Spotify` and the Discord switch in your own
  session and read the status line, which reports every failure instead of hiding it.
* **The cosmetic modules have never been seen on a GPU.** The geometry is validated and the maths is
  plain, but colours, line widths and how the hat sits on a 1.8-block-tall player are exactly the kind
  of thing that needs eyes. Adjust radius/height first.
* **Fog and sky depend on optional mixins.** If a future 26.1.x renames those methods, both modules go
  inert rather than crashing. Their status lines tell you which state you are in; the log line
  `[FoxClientv2.0] optional mixin config ... applied/skipped` says the same.
* **The HUD editor moves the block, not individual elements.** It is one anchored stack (module list,
  then module lines), so choosing a corner and dragging is the whole feature; per-element offsets
  would need every module to carry its own anchor.
* **The new HUD modules are layout, not art, until you look at them.** Sizes, colours and the
  keystroke cell metrics were chosen on paper; expect to nudge them. `Ping` in the watermark is 0 in
  singleplayer, which is correct, not a bug.
* **TargetHUD's fallback pick is a plain nearest-player search**, not line of sight — with nothing
  under the crosshair it can pick someone behind a wall inside the radius. The crosshair pick itself
  is vanilla's, so it is always something you could actually be aiming at.
* **26.1.2 only.**
* Waypoint deletion is by editing `config/foxclient/config.json`; the GUI only adds.

* **The combat modules have never been run against a server here.** The API calls are verified by
  disassembly (`useItemOn`, `attack`, `handleContainerInput(... ContainerInput.SWAP ...)`), but the
  pacing, reach and the offhand swap button (40) are the parts that only a real session can confirm.
  Test in your own world first.
* **World-draw validation cannot fix a colour choice.** If boxes look like black slabs, that is the
  colour you (or a preset) chose plus the fill alpha - press `reset` or pick a lighter preset, and
  check the About pane for rejects before assuming the renderer is broken.

Package layout
--------------

    src/main/resources/fabric.mod.json           mod metadata
    src/client/resources/foxclient.client.mixins.json
    src/client/java/com/foxclient/
        FoxClient.java          client entry point, keybinds, event wiring
        module/                 Module, ModuleManager, Category, Risk
        module/modules/         Fullbright, BlockRadar, ESP, SoundRadar, Freecam, Waypoints,
                                Info, BuildAssist, SpeedCrystals, AnchorMacro, AppleUI, GrapeCrosshair,
                                SelectionApple, BuildCapture, AmbienceMixer,
                                AttributeSwap, ShieldStatus, FightCoach, ReplayTheatre, AutoTotem,
                                Spotify, World Lyrics, StaffWatch
        setting/                Bool, Int, Float, Enum, Color, ColorMap, StringList, String, Keybind
        schematic/              Schematic, LitematicaReader, TextSchematicReader, BuildPlanner
        media/                  NowPlaying, SpotifyClient, LyricsClient, DiscordRpc
        assistant/              AssistantBrain (local feature matcher)
        gui/                    ClickGuiScreen, Glass, ColorPickScreen, ColorEditorScreen,
                                ListPickerScreen, SchematicScreen, FoxSplash
        hud/                    HudManager
        render/                 WorldDraw (gizmos only), RenderUtils (colour maths)
        config/                 ConfigManager
        util/                   SlotUtil, CombatUtil, AnchorUtil, UrlOpener
        mixin/                  Camera, packet listener, lightmap, screen

License: CC0-1.0 — do whatever you want with it.
