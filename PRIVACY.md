# Privacy — what FoxClientv2.0 sends, and what it never sends

This is a description of the code in this repository, not a promise about intentions. Everything below
can be checked by searching `src/client/java` for `HttpClient`, `SocketChannel` and `URI`.

## The short version

* **Nothing about your server is ever sent anywhere.** No address, no port, no hostname, no ping record.
* **No telemetry.** No analytics, no usage counters, no crash reporting, no update checks, no licensing
  pings. The client runs, or it doesn't; it does not report.
* **No identifiers.** No Mojang UUID, no session token, no username, no hardware ID is transmitted by any
  code in this mod. The game already talks to its own servers; this mod adds nothing to that.
* **Configuration stays on disk.** `config/foxclient/*.json` — modules, themes, HUD layout, waypoints,
  macros, captures. Nothing in those files is uploaded.

## The three places a socket is opened

| Where | What goes out | When | Off switch |
|---|---|---|---|
| `media/SpotifyClient` | Calls to `accounts.spotify.com` / `api.spotify.com` with **your own** OAuth token, plus the "currently playing" query. A tiny localhost callback server receives the redirect. | Only if you connect Spotify from the module | Don't connect it; or turn the module off |
| `media/LyricsClient` | A **track name / artist** query to `lrclib.net`. No account, no ID, nothing about the game. | Only while a track is playing and the lyrics HUD is on | **WorldLyrics → "Look up online" off** (3.3.1+): no URL is even built |
| `media/DiscordRpc` | A **local** socket `discord-ipc-0…9` (unix socket / named pipe) on your own machine, carrying the track you are playing. It never leaves the computer — Discord receives it exactly as it does from any music app. | Only if Discord is running and the module is on | Turn the Discord presence off; or turn the module off |

Nothing else in this mod touches the network. `Chat`, `Macros`, `BuildCapture`, `ESP`, `ShieldStatus`,
`AmbienceMixer` and the rest are local computation and drawing.

## Things that were deliberately *not* built because of this

* **Per-server profiles** — auto-switching config by server address. Even implemented entirely locally it
  means the client reads and records where you connect, which is the one piece of information worth not
  collecting. Declined at the request of the person who asked for it.
* **Any server-side component.** There is no backend, no account system, no "cloud sync". The client has
  nothing to log into.

## Reporting a leak

If you find a request in this codebase that is not in the table above, that is a bug of the worst kind —
open an issue with the file and line.
