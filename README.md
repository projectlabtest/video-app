# Video App — Video (Extended Screen)

Video player na may **extended screen** support at **upload** feature. Kontrol sa main
window, video display sa pangalawang monitor (projector/TV). Java-based, native desktop app.

## Requirements
- Native app: walang kailangan (naka-bundle ang JRE 17)
- Source: JDK 17+ (Zulu)

## Native app (JavaFX — true native window, walang browser)
May ready-made na native app sa `media-player-native/`. Gumagamit ng JavaFX WebView
(native window, hindi browser). Walang address bar, walang tabs, walang browser chrome.

**Paano patakbuhin:**
- I-double-click ang `media-player-native\run_hidden.vbs`
- Awtomatikong magbubukas ang JavaFX native window sa http://localhost:8090/
- May sariling JRE (Java 17) + JavaFX — hindi na kailangan ng Java install

**Kailangan (para sa development):**
- JDK 17+ (Zulu)
- JavaFX SDK 21 (naka-download sa temp folder, o i-download mula sa https://gluonhq.com/products/javafx/)
- `--module-path` at `--add-modules javafx.controls,javafx.web`

## Native app (jpackage — Chrome/Edge app mode)
May ready-made na native app sa `jpackage-output/VideoApp/`. Nagbubukas ng Chrome/Edge
sa app mode (walang address bar/tabs). Gumagamit ng system browser engine.

**Para sa native window (walang browser chrome):**
- I-double-click ang `run_hidden.vbs` — magbubukas ng Chrome/Edge sa app mode
- Gumagana kahit may spaces sa path (hindi tulad ng .exe)

**Alternatibo (.exe launcher):**
- `VideoApp.exe` — native app launcher (pero may "Failed to launch JVM" error
  kung may spaces sa path)

**Alternatibo (console):**
- `run.bat` — console window, gumagana sa anumang path
- `run-media.bat` (source) — compile + run, JDK 17+ required

## Run (source)
Double-click `run-media.bat` (compiles + starts on port 8090), or:
```
javac -d out src\*.java
java -cp out MediaPlayer
```
Buksan: http://localhost:8090/  (controller)
Output window: http://localhost:8090/output.html

Stop: `stop-media.bat` o Ctrl+C.

## Gamit
1. ⚙ → **Upload Files** → pumili ng video/audio files (i-uupload sa app)
   O ⚙ → **Add Folder** → ilagay ang folder path (hal. `C:\Users\...\Videos`) → Scan Library
2. Click ng video sa listahan → magbubukas ang Output Window at magpe-play
3. I-drag ang Output Window sa extended screen (2nd monitor), i-click para i-fullscreen
4. Controls sa main window: Play/Pause, Stop, Prev/Next, seek, volume, mute
5. Keyboard: Space (play/pause), ←/→ (seek ±5s), ↑/↓ (volume), N/P (next/prev), F (focus output)

## Upload
- ⚙ → Upload Files → pumili ng maraming video/audio files
- Supported video: mp4, m4v, mkv, avi, mov, wmv, flv, webm, ogv, mpeg, mpg, ts, m2ts, vob, 3gp, 3g2, rm, rmvb, asf, divx, f4v, mp4v, mpv, m2v, at iba pa
- Supported audio: mp3, wav, ogg, flac, m4a, aac, wma, opus, aiff, au, at iba pa
- Hindi supported na format ay tine-try pa rin i-classify via content-type (best effort)
- Ang mga na-upload na file ay naka-save sa `uploads/` folder sa app directory

## Features
- Video library scan (recursive folders), search
- Upload files (video + audio, maraming format)
- Playback: play/pause/stop/seek/volume/mute/next/prev
- Output window sync (play/pause/seek/volume/load) via /api/control
- State reporting (time/playing/track) via /api/state
- Extended screen support (Screen Details API + manual drag + click-to-fullscreen)
- Remote control: /remote.html (phone/tablet)
- Offline storage: data/library.json, settings.json, folders.json, pads.json, programs.json
- HTTP Range serving (video seeking, large files)

## Mga file
- `MediaPlayer.java` — HTTP server (library, folders, scan, media serving, upload, control/state/commands)
- `index.html` — controller UI (DJ button pad style)
- `output.html` — output/display window (extended screen, 1920×1080)
- `remote.html` — phone/tablet remote
- `run-media.bat` / `stop-media.bat` — Windows launcher
- `jpackage-output/MediaPlayer/` — portable native app (exe + bundled JRE)