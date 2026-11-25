import com.sun.net.httpserver.*;
import java.io.*;
import java.awt.Desktop;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Media Player — local desktop service (JDK-only, walang external lib).
 * Run:  javac -d out src\*.java   then   java -cp out MediaPlayer
 * UI:   http://localhost:8090/
 */
public class MediaPlayer {
    static int PORT = 8090;
    static Path BASE = Path.of("").toAbsolutePath();
    static HttpServer SERVER = null;

    static Path LOG_FILE = null;

    static final Map<String, Object> STATE = new ConcurrentHashMap<>();
    static final List<String> LOG = new CopyOnWriteArrayList<>();
    static final List<Map<String, Object>> COMMANDS = new CopyOnWriteArrayList<>();
    static final List<Map<String, Object>> OUTPUT_COMMANDS = new CopyOnWriteArrayList<>();
    static final List<Map<String, Object>> LIBRARY = new CopyOnWriteArrayList<>();
    static final List<Map<String, Object>> PROGRAMS = new CopyOnWriteArrayList<>();
    static final List<Map<String, Object>> PADS = new CopyOnWriteArrayList<>();
    static final List<String> SCAN_FOLDERS = new CopyOnWriteArrayList<>();

    static Map<String, Object> SETTINGS = new LinkedHashMap<>();

    // media extensions
    static final Set<String> AUDIO_EXT = Set.of("mp3","wav","ogg","oga","flac","m4a","aac","wma","opus","webm","aiff","au","m4b","m4p","mid","midi","amr","awb","ra","ram");
    static final Set<String> VIDEO_EXT = Set.of("mp4","m4v","webm","mkv","avi","mov","wmv","flv","ogv","mpeg","mpg","m2ts","ts","mts","vob","3gp","3g2","rm","rmvb","asf","divx","f4v","mp4v","mpv","m2v","m1v","svi","viv","vivo","xvid","yuv","h264","h265","hevc");

    public static void main(String[] args) {
        // shutdown hook para sa logging
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { log("=== SERVER SHUTDOWN ==="); } catch (Exception ignored) {}
        }, "shutdown-hook"));

        while (true) {
            try {
                startServer();
                // buksan bilang native-app window (walang browser chrome) kung may Chrome/Edge
                try {
                    String url = "http://localhost:" + PORT + "/";
                    String[] candidates = {
                        "C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome.exe",
                        "C:\\\\Program Files (x86)\\\\Microsoft\\\\Edge\\\\Application\\\\msedge.exe",
                        "C:\\\\Program Files\\\\Microsoft\\\\Edge\\\\Application\\\\msedge.exe"
                    };
                    boolean opened = false;
                    for (String c : candidates) {
                        if (new java.io.File(c).exists()) {
                            new ProcessBuilder(c, "--app=" + url, "--window-size=1280,800").start();
                            opened = true;
                            break;
                        }
                    }
                    if (!opened) {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(url));
                        } else {
                            new ProcessBuilder("cmd", "/c", "start", "", url).start();
                        }
                    }
                } catch (Exception e) { log("app window open err: " + e.getMessage()); }

                // keep-alive: huwag mag-exit ang JVM habang tumatakbo ang server
                Thread.currentThread().join();
            } catch (Exception e) {
                log("SERVER CRASHED: " + e);
                e.printStackTrace();
                // wait 3 seconds before restarting
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
    }

    public static void startServer() throws Exception {
        resolveBase();
        try { LOG_FILE = BASE.resolve("server.log"); } catch (Exception ignored) {}
        loadSettings();
        loadLibrary();
        loadPrograms();
        loadPads();
        loadScanFolders();

        HttpServer s = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        // store reference for watchdog
        SERVER = s;
        s.createContext("/", MediaPlayer::serveStatic);
        s.createContext("/media", MediaPlayer::serveMedia);
        s.createContext("/api/status", MediaPlayer::apiStatus);
        s.createContext("/api/settings", MediaPlayer::apiSettings);
        s.createContext("/api/folders", MediaPlayer::apiFolders);
        s.createContext("/api/scan", MediaPlayer::apiScan);
        s.createContext("/api/library", MediaPlayer::apiLibrary);
        s.createContext("/api/programs", MediaPlayer::apiPrograms);
        s.createContext("/api/playlists", MediaPlayer::apiPrograms);
        s.createContext("/api/pads", MediaPlayer::apiPads);
        s.createContext("/api/control", MediaPlayer::apiControl);
        s.createContext("/api/state", MediaPlayer::apiState);
        s.createContext("/api/commands", MediaPlayer::apiCommands);
        s.createContext("/api/output-commands", MediaPlayer::apiOutputCommands);
        s.createContext("/api/upload", MediaPlayer::apiUpload);
        s.setExecutor(Executors.newCachedThreadPool());
        s.start();
        log("MediaPlayer START port=" + PORT);
        System.out.println("UI: http://localhost:" + PORT + "/");
        System.out.println("Remote: http://localhost:" + PORT + "/remote.html");
    }

    // ---------- base dir ----------
    static void resolveBase() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("index.html"))) { BASE = cwd; return; }
        try {
            Path self = Path.of(MediaPlayer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(self) ? self : self.getParent();
            if (dir != null && Files.exists(dir.resolve("index.html"))) { BASE = dir; return; }
        } catch (Exception ignored) {}
        BASE = cwd;
    }

    static Path cfgFile(String name) { return BASE.resolve(name); }
    static Path dataFile(String name) { return BASE.resolve("data").resolve(name); }

    // ---------- settings ----------
    static void loadSettings() {
        try {
            Path p = dataFile("settings.json");
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                SETTINGS.put("port", PORT);
                SETTINGS.put("theme", "dark");
                SETTINGS.put("defaultVolume", 0.85);
                SETTINGS.put("crossfadeSec", 0.5);
                saveSettings();
            } else {
                SETTINGS = Json.obj(Json.parse(Files.readString(p, StandardCharsets.UTF_8)));
            }
            PORT = Json.num(SETTINGS, "port", PORT);
        } catch (Exception e) { log("settings load err: " + e.getMessage()); }
    }
    static void saveSettings() {
        try {
            Files.createDirectories(dataFile("").getParent() == null ? BASE : dataFile("").getParent());
            Files.writeString(dataFile("settings.json"), Json.write(SETTINGS), StandardCharsets.UTF_8);
        } catch (Exception e) { log("settings save err: " + e.getMessage()); }
    }

    // ---------- scan folders ----------
    static void loadScanFolders() {
        try {
            Path p = dataFile("folders.json");
            if (Files.exists(p)) {
                for (Object x : Json.arr(Json.parse(Files.readString(p, StandardCharsets.UTF_8)))) {
                    String f = String.valueOf(x).trim();
                    if (!f.isEmpty()) SCAN_FOLDERS.add(f);
                }
            }
        } catch (Exception e) { log("folders load err: " + e.getMessage()); }
    }
    static void saveScanFolders() {
        try {
            Files.createDirectories(dataFile("").getParent());
            Files.writeString(dataFile("folders.json"), Json.write(new ArrayList<>(SCAN_FOLDERS)), StandardCharsets.UTF_8);
        } catch (Exception e) { log("folders save err: " + e.getMessage()); }
    }

    // ---------- library ----------
    static void loadLibrary() {
        try {
            Path p = dataFile("library.json");
            if (Files.exists(p)) {
                for (Object x : Json.arr(Json.parse(Files.readString(p, StandardCharsets.UTF_8)))) {
                    Map<String, Object> m = Json.obj(x);
                    if (m.get("id") != null) LIBRARY.add(m);
                }
            }
        } catch (Exception e) { log("library load err: " + e.getMessage()); }
    }
    static void saveLibrary() {
        try {
            Files.createDirectories(dataFile("").getParent());
            Files.writeString(dataFile("library.json"), Json.write(new ArrayList<>(LIBRARY)), StandardCharsets.UTF_8);
        } catch (Exception e) { log("library save err: " + e.getMessage()); }
    }

    static void scanAll() {
        // remove entries whose file no longer exists
        LIBRARY.removeIf(t -> {
            String path = Json.str(t, "path", "");
            return !path.isEmpty() && !Files.exists(Path.of(path));
        });
        Set<String> existingPaths = new HashSet<>();
        for (Map<String, Object> t : LIBRARY) existingPaths.add(Json.str(t, "path", ""));

        int added = 0;
        for (String folder : SCAN_FOLDERS) {
            Path root = Path.of(folder);
            if (!Files.exists(root)) { log("scan skip (missing): " + folder); continue; }
            try {
                Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString();
                    String ext = extOf(name);
                    boolean isMedia = AUDIO_EXT.contains(ext) || VIDEO_EXT.contains(ext);
                    // fallback: probe content type for files with unknown extension
                    if (!isMedia) {
                        try {
                            String ct = Files.probeContentType(p);
                            if (ct != null && (ct.startsWith("video/") || ct.startsWith("audio/"))) isMedia = true;
                        } catch (Exception ignored) {}
                    }
                    if (!isMedia) return;
                    String pathStr = p.toAbsolutePath().toString();
                    // check already in library
                    for (Map<String, Object> t : LIBRARY) {
                        if (pathStr.equals(Json.str(t, "path", ""))) return;
                    }
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("id", UUID.randomUUID().toString());
                    t.put("name", name);
                    t.put("path", pathStr);
                    try { t.put("size", Files.size(p)); } catch (Exception e) { t.put("size", 0L); }
                    t.put("modified", lastMod(p));
                    String type = "video";
                    if (AUDIO_EXT.contains(ext)) type = "audio";
                    else if (!VIDEO_EXT.contains(ext)) {
                        // unknown extension → classify by content type
                        try {
                            String ct = Files.probeContentType(p);
                            if (ct != null && ct.startsWith("audio/")) type = "audio";
                        } catch (Exception ignored) {}
                    }
                    t.put("type", type);
                    t.put("ext", ext);
                    t.put("duration", 0.0);
                    LIBRARY.add(t);
                    nonFinalAdd();
                });
            } catch (Exception e) { log("scan err " + folder + ": " + e.getMessage()); }
        }
        saveLibrary();
        log("scan done: " + LIBRARY.size() + " tracks");
    }

    // helper to count added (not strictly needed)
    static void nonFinalAdd() {}

    static String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
    static long lastMod(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
    }

    // ---------- programs ----------
    static void loadPrograms() {
        try {
            Path p = dataFile("programs.json");
            if (Files.exists(p)) {
                for (Object x : Json.arr(Json.parse(Files.readString(p, StandardCharsets.UTF_8)))) {
                    Map<String, Object> m = Json.obj(x);
                    if (m.get("id") != null) PROGRAMS.add(m);
                }
            }
        } catch (Exception e) { log("programs load err: " + e.getMessage()); }
    }
    static void savePrograms() {
        try {
            Files.createDirectories(dataFile("").getParent());
            Files.writeString(dataFile("programs.json"), Json.write(new ArrayList<>(PROGRAMS)), StandardCharsets.UTF_8);
        } catch (Exception e) { log("programs save err: " + e.getMessage()); }
    }

    // ---------- pads ----------
    static void loadPads() {
        try {
            Path p = dataFile("pads.json");
            if (Files.exists(p)) {
                for (Object x : Json.arr(Json.parse(Files.readString(p, StandardCharsets.UTF_8)))) {
                    Map<String, Object> m = Json.obj(x);
                    if (m.get("key") != null) PADS.add(m);
                }
            }
        } catch (Exception e) { log("pads load err: " + e.getMessage()); }
    }
    static void savePads() {
        try {
            Files.createDirectories(dataFile("").getParent());
            Files.writeString(dataFile("pads.json"), Json.write(new ArrayList<>(PADS)), StandardCharsets.UTF_8);
        } catch (Exception e) { log("pads save err: " + e.getMessage()); }
    }

    // ---------- log ----------
    static void log(String m) {
        String line = LocalTime.now().withNano(0) + " " + m;
        LOG.add(line);
        System.out.println(line);
        // write to log file
        try {
            if (LOG_FILE == null) {
                try { LOG_FILE = BASE.resolve("server.log"); } catch (Exception ignored) {}
            }
            if (LOG_FILE != null) {
                Files.writeString(LOG_FILE, line + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {}
        while (LOG.size() > 300) LOG.remove(0);
    }

    // ---------- HTTP helpers ----------
    static void send(HttpExchange x, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        x.getResponseHeaders().add("Cache-Control", "no-store");
        x.sendResponseHeaders(code, b.length);
        try (OutputStream os = x.getResponseBody()) { os.write(b); }
        x.close();
    }
    static String body(HttpExchange x) throws IOException {
        return new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static String mimeFor(String name) {
        String ext = extOf(name);
        return switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg","oga" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "m4a","aac" -> "audio/mp4";
            case "opus" -> "audio/opus";
            case "webm" -> "video/webm";
            case "mp4","m4v" -> "video/mp4";
            case "mkv" -> "video/x-matroska";
            case "avi" -> "video/x-msvideo";
            case "mov" -> "video/quicktime";
            case "wmv" -> "video/x-ms-wmv";
            case "flv" -> "video/x-flv";
            case "ogv" -> "video/ogg";
            case "mpeg","mpg" -> "video/mpeg";
            default -> "application/octet-stream";
        };
    }

    // ---------- static serving ----------
    static void serveStatic(HttpExchange x) throws IOException {
        try {
            String p = x.getRequestURI().getPath();
            if (p.equals("/")) p = "/index.html";
            // prevent traversal
            Path file = BASE.resolve(p.substring(1)).normalize();
            if (!file.startsWith(BASE)) { send(x, 403, "{\"ok\":false}"); return; }
            if (!Files.exists(file) || Files.isDirectory(file)) {
                // fallback to index.html for SPA
                file = BASE.resolve("index.html");
                if (!Files.exists(file)) { send(x, 404, "{\"ok\":false,\"err\":\"no index.html\"}"); return; }
            }
            byte[] b = Files.readAllBytes(file);
            String ct = mimeFor(file.getFileName().toString());
            if (file.getFileName().toString().endsWith(".html")) ct = "text/html; charset=utf-8";
            else if (file.getFileName().toString().endsWith(".css")) ct = "text/css; charset=utf-8";
            else if (file.getFileName().toString().endsWith(".js")) ct = "application/javascript; charset=utf-8";
            else if (file.getFileName().toString().endsWith(".json")) ct = "application/json; charset=utf-8";
            x.getResponseHeaders().add("Content-Type", ct);
            x.getResponseHeaders().add("Cache-Control", "no-store");
            x.sendResponseHeaders(200, b.length);
            try (OutputStream os = x.getResponseBody()) { os.write(b); }
        } catch (Exception e) {
            send(x, 500, "{\"ok\":false,\"err\":\"" + e.getMessage() + "\"}");
        } finally { x.close(); }
    }

    // ---------- media file serving with Range ----------
    static void serveMedia(HttpExchange x) throws IOException {
        try {
            String p = x.getRequestURI().getPath(); // /media?path=...  or /media/<id>
            // We use query param: /media?id=<trackId>
            String q = x.getRequestURI().getQuery();
            String id = null;
            if (q != null) for (String kv : q.split("&")) {
                String[] pp = kv.split("=", 2);
                if (pp.length == 2 && pp[0].equals("id")) id = URLDecoder.decode(pp[1], StandardCharsets.UTF_8);
            }
            if (id == null) { send(x, 400, "{\"ok\":false,\"err\":\"id required\"}"); return; }

            Map<String, Object> track = findTrack(id);
            if (track == null) { send(x, 404, "{\"ok\":false,\"err\":\"track not found\"}"); return; }
            String pathStr = Json.str(track, "path", "");
            Path file = Path.of(pathStr);
            if (!Files.exists(file)) { send(x, 404, "{\"ok\":false,\"err\":\"file missing\"}"); return; }

            long size = Files.size(file);
            String mime = mimeFor(file.getFileName().toString());
            String range = x.getRequestHeaders().getFirst("Range");

            if (range == null || range.isEmpty()) {
                x.getResponseHeaders().add("Content-Type", mime);
                x.getResponseHeaders().add("Accept-Ranges", "bytes");
                x.getResponseHeaders().add("Content-Length", String.valueOf(size));
                x.sendResponseHeaders(200, size);
                try (OutputStream os = x.getResponseBody(); InputStream is = Files.newInputStream(file)) {
                    is.transferTo(os);
                }
            } else {
                // parse bytes=start-end
                String r = range.trim();
                if (r.toLowerCase().startsWith("bytes=")) r = r.substring(6);
                String[] parts = r.split("-", 2);
                long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0].trim());
                long end = (parts.length < 2 || parts[1].trim().isEmpty()) ? size - 1 : Long.parseLong(parts[1].trim());
                if (end >= size) end = size - 1;
                if (start > end || start >= size) {
                    x.getResponseHeaders().add("Content-Range", "bytes */" + size);
                    x.sendResponseHeaders(416, -1);
                    x.close();
                    return;
                }
                long len = end - start + 1;
                x.getResponseHeaders().add("Content-Type", mime);
                x.getResponseHeaders().add("Accept-Ranges", "bytes");
                x.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + size);
                x.getResponseHeaders().add("Content-Length", String.valueOf(len));
                x.sendResponseHeaders(206, len);
                try (OutputStream os = x.getResponseBody(); InputStream is = Files.newInputStream(file)) {
                    is.skipNBytes(start);
                    byte[] buf = new byte[65536];
                    long remaining = len;
                    while (remaining > 0) {
                        int n = is.read(buf, 0, (int) Math.min(buf.length, remaining));
                        if (n < 0) break;
                        os.write(buf, 0, n);
                        remaining -= n;
                    }
                }
            }
        } catch (Exception e) {
            try { send(x, 500, "{\"ok\":false,\"err\":\"" + e.getMessage() + "\"}"); } catch (Exception ignored) {}
        } finally { try { x.close(); } catch (Exception ignored) {} }
    }

    static Map<String, Object> findTrack(String id) {
        for (Map<String, Object> t : LIBRARY) {
            if (id.equals(String.valueOf(t.get("id")))) return t;
        }
        return null;
    }

    // ---------- API ----------

    static void apiStatus(HttpExchange x) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("v", System.currentTimeMillis());
        out.put("lan_ip", lanIp());
        out.put("port", PORT);
        out.put("trackCount", LIBRARY.size());
        out.put("programCount", PROGRAMS.size());
        out.put("scanFolders", new ArrayList<>(SCAN_FOLDERS));
        // playback state
        Map<String, Object> pb = STATE.get("playback") instanceof Map ? (Map<String, Object>) STATE.get("playback") : new LinkedHashMap<>();
        out.put("playback", pb);
        // commands queue (for main client to execute)
        List<Map<String, Object>> cmds = new ArrayList<>(COMMANDS);
        out.put("commands", cmds);
        // clear commands after sending (main client will execute)
        // NOTE: we don't auto-clear; client acks via /api/commands
        send(x, 200, Json.write(out));
    }

    static void apiSettings(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            send(x, 200, Json.write(Map.of("ok", true, "settings", SETTINGS)));
            return;
        }
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        Map<String, Object> b = Json.obj(Json.parse(body(x)));
        for (var e : b.entrySet()) SETTINGS.put(e.getKey(), e.getValue());
        saveSettings();
        send(x, 200, Json.write(Map.of("ok", true, "settings", SETTINGS)));
    }

    static void apiFolders(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            send(x, 200, Json.write(Map.of("ok", true, "folders", new ArrayList<>(SCAN_FOLDERS))));
            return;
        }
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        Map<String, Object> b = Json.obj(Json.parse(body(x)));
        String action = Json.str(b, "action", "add").toLowerCase();
        if (action.equals("add")) {
            String f = Json.str(b, "folder", "").trim();
            if (f.isEmpty()) { send(x, 400, "{\"ok\":false,\"err\":\"folder required\"}"); return; }
            if (!SCAN_FOLDERS.contains(f)) SCAN_FOLDERS.add(f);
            saveScanFolders();
            send(x, 200, Json.write(Map.of("ok", true, "folders", new ArrayList<>(SCAN_FOLDERS))));
            return;
        }
        if (action.equals("remove")) {
            String f = Json.str(b, "folder", "").trim();
            SCAN_FOLDERS.remove(f);
            saveScanFolders();
            send(x, 200, Json.write(Map.of("ok", true, "folders", new ArrayList<>(SCAN_FOLDERS))));
            return;
        }
        send(x, 400, "{\"ok\":false,\"err\":\"action add/remove\"}");
    }

    static void apiScan(HttpExchange x) throws IOException {
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        scanAll();
        send(x, 200, Json.write(Map.of("ok", true, "count", LIBRARY.size())));
    }

    static void apiLibrary(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            // optional ?type=audio|video&q=search
            String q = queryParam(x, "q");
            String type = queryParam(x, "type");
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> t : LIBRARY) {
                if (type != null && !type.isEmpty() && !type.equalsIgnoreCase(Json.str(t, "type", ""))) continue;
                if (q != null && !q.isEmpty()) {
                    String name = Json.str(t, "name", "").toLowerCase();
                    if (!name.contains(q.toLowerCase())) continue;
                }
                // don't leak full path? keep path for media url; client uses /media?id=
                Map<String, Object> c = new LinkedHashMap<>(t);
                out.add(c);
            }
            send(x, 200, Json.write(Map.of("ok", true, "tracks", out)));
            return;
        }
        if (x.getRequestMethod().equalsIgnoreCase("POST")) {
            // update duration or metadata: {id, duration}
            Map<String, Object> b = Json.obj(Json.parse(body(x)));
            String id = Json.str(b, "id", "");
            if (id.isEmpty()) { send(x, 400, "{\"ok\":false,\"err\":\"id required\"}"); return; }
            Map<String, Object> t = findTrack(id);
            if (t == null) { send(x, 404, "{\"ok\":false,\"err\":\"not found\"}"); return; }
            if (b.containsKey("duration")) t.put("duration", Json.numD(b, "duration", 0.0));
            saveLibrary();
            send(x, 200, Json.write(Map.of("ok", true)));
            return;
        }
        if (x.getRequestMethod().equalsIgnoreCase("DELETE")) {
            String id = queryParam(x, "id");
            if (id == null || id.isEmpty()) { send(x, 400, "{\"ok\":false,\"err\":\"id required\"}"); return; }
            boolean removed = LIBRARY.removeIf(t -> id.equals(String.valueOf(t.get("id"))));
            // remove from programs + pads
            for (Map<String, Object> pl : PROGRAMS) {
                List<Object> ids = Json.arr(pl.get("trackIds"));
                ids.removeIf(o -> id.equals(String.valueOf(o)));
                pl.put("trackIds", ids);
            }
            PADS.removeIf(p -> id.equals(String.valueOf(p.get("trackId"))));
            saveLibrary(); savePrograms(); savePads();
            send(x, 200, Json.write(Map.of("ok", true, "removed", removed)));
            return;
        }
        send(x, 405, "{\"ok\":false}");
    }

    static void apiUpload(HttpExchange x) throws IOException {
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        // filename from query param ?name=...
        String name = queryParam(x, "name");
        if (name == null || name.trim().isEmpty()) { send(x, 400, "{\"ok\":false,\"err\":\"name required (?name=filename)\"}"); return; }
        name = name.trim().replaceAll("[\\\\/]", "_"); // sanitize
        // ensure uploads dir
        Path uploadsDir = BASE.resolve("uploads");
        try { Files.createDirectories(uploadsDir); } catch (Exception e) { send(x, 500, "{\"ok\":false,\"err\":\"cannot create uploads dir\"}"); return; }
        Path target = uploadsDir.resolve(name);
        // avoid overwriting: append (1), (2), ...
        if (Files.exists(target)) {
            String base = name;
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0) { ext = name.substring(dot); base = name.substring(0, dot); }
            int n = 1;
            while (Files.exists(target)) { target = uploadsDir.resolve(base + " (" + n + ")" + ext); n++; }
        }
        // stream request body to file
        try (InputStream is = x.getRequestBody(); OutputStream os = Files.newOutputStream(target)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
        } catch (Exception e) { try { Files.deleteIfExists(target); } catch (Exception ignored) {} send(x, 500, "{\"ok\":false,\"err\":\"upload failed: " + e.getMessage() + "\"}"); return; }
        // add to library
        String ext = extOf(target.getFileName().toString());
        boolean isAudio = AUDIO_EXT.contains(ext);
        boolean isVideo = VIDEO_EXT.contains(ext);
        if (!isAudio && !isVideo) {
            // probe content type
            try {
                String ct = Files.probeContentType(target);
                if (ct != null) { if (ct.startsWith("audio/")) isAudio = true; else if (ct.startsWith("video/")) isVideo = true; }
            } catch (Exception ignored) {}
        }
        if (!isAudio && !isVideo) {
            // still add as video (best effort) so user can try
            isVideo = true;
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", UUID.randomUUID().toString());
        t.put("name", target.getFileName().toString());
        t.put("path", target.toAbsolutePath().toString());
        try { t.put("size", Files.size(target)); } catch (Exception e) { t.put("size", 0L); }
        t.put("modified", lastMod(target));
        t.put("type", isAudio ? "audio" : "video");
        t.put("ext", ext);
        t.put("duration", 0.0);
        LIBRARY.add(t);
        saveLibrary();
        log("uploaded: " + target.getFileName() + " (" + t.get("type") + ")");
        send(x, 200, Json.write(Map.of("ok", true, "track", t)));
    }

    static void apiPrograms(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            send(x, 200, Json.write(Map.of("ok", true, "programs", new ArrayList<>(PROGRAMS))));
            return;
        }
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        Map<String, Object> b = Json.obj(Json.parse(body(x)));
        String action = Json.str(b, "action", "save").toLowerCase();
        if (action.equals("save")) {
            String id = Json.str(b, "id", "");
            String name = Json.str(b, "name", "Playlist");
            List<Object> trackIds = Json.arr(b.get("trackIds"));
            if (id.isEmpty()) id = UUID.randomUUID().toString();
            // update existing or add
            Map<String, Object> existing = null;
            for (Map<String, Object> pl : PROGRAMS) {
                if (id.equals(String.valueOf(pl.get("id")))) { existing = pl; break; }
            }
            if (existing != null) {
                existing.put("name", name);
                existing.put("trackIds", trackIds);
            } else {
                Map<String, Object> pl = new LinkedHashMap<>();
                pl.put("id", id);
                pl.put("name", name);
                pl.put("trackIds", trackIds);
                PROGRAMS.add(pl);
            }
            savePrograms();
            send(x, 200, Json.write(Map.of("ok", true, "id", id)));
            return;
        }
        if (action.equals("delete")) {
            String id = Json.str(b, "id", "");
            boolean removed = PROGRAMS.removeIf(pl -> id.equals(String.valueOf(pl.get("id"))));
            savePrograms();
            send(x, 200, Json.write(Map.of("ok", true, "removed", removed)));
            return;
        }
        send(x, 400, "{\"ok\":false,\"err\":\"action save/delete\"}");
    }

    static void apiPads(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            String pid = queryParam(x, "programId");
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> p : PADS) {
                if (pid != null && !pid.isEmpty() && !pid.equals(String.valueOf(p.get("programId")))) continue;
                out.add(p);
            }
            send(x, 200, Json.write(Map.of("ok", true, "pads", out)));
            return;
        }
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        Map<String, Object> b = Json.obj(Json.parse(body(x)));
        String action = Json.str(b, "action", "assign").toLowerCase();
        if (action.equals("assign")) {
            String programId = Json.str(b, "programId", "");
            String key = Json.str(b, "key", "");
            String trackId = Json.str(b, "trackId", "");
            if (programId.isEmpty() || key.isEmpty()) { send(x, 400, "{\"ok\":false,\"err\":\"programId,key required\"}"); return; }
            // remove existing assignment for this key+program
            PADS.removeIf(p -> programId.equals(String.valueOf(p.get("programId"))) && key.equals(String.valueOf(p.get("key"))));
            if (!trackId.isEmpty()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("programId", programId);
                p.put("key", key);
                p.put("trackId", trackId);
                PADS.add(p);
            }
            savePads();
            send(x, 200, Json.write(Map.of("ok", true)));
            return;
        }
        if (action.equals("clear")) {
            String programId = Json.str(b, "programId", "");
            PADS.removeIf(p -> programId.isEmpty() || programId.equals(String.valueOf(p.get("programId"))));
            savePads();
            send(x, 200, Json.write(Map.of("ok", true)));
            return;
        }
        send(x, 400, "{\"ok\":false,\"err\":\"action assign/clear\"}");
    }

    static void apiControl(HttpExchange x) throws IOException {
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        Map<String, Object> b = Json.obj(Json.parse(body(x)));
        String target = Json.str(b, "target", "main").toLowerCase();
        // command: {action, value, from}
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("action", Json.str(b, "action", ""));
        cmd.put("value", b.get("value"));
        cmd.put("from", Json.str(b, "from", "remote"));
        cmd.put("ts", System.currentTimeMillis());
        if (target.equals("output")) {
            OUTPUT_COMMANDS.add(cmd);
            while (OUTPUT_COMMANDS.size() > 50) OUTPUT_COMMANDS.remove(0);
        } else {
            COMMANDS.add(cmd);
            while (COMMANDS.size() > 50) COMMANDS.remove(0);
        }
        send(x, 200, Json.write(Map.of("ok", true)));
    }

    static void apiState(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            Map<String, Object> pb = STATE.get("playback") instanceof Map ? (Map<String, Object>) STATE.get("playback") : new LinkedHashMap<>();
            send(x, 200, Json.write(Map.of("ok", true, "playback", pb)));
            return;
        }
        if (!x.getRequestMethod().equalsIgnoreCase("POST")) { send(x, 405, "{\"ok\":false}"); return; }
        Map<String, Object> b = Json.obj(Json.parse(body(x)));
        STATE.put("playback", b);
        send(x, 200, Json.write(Map.of("ok", true)));
    }

    static void apiCommands(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            send(x, 200, Json.write(Map.of("ok", true, "commands", new ArrayList<>(COMMANDS))));
            return;
        }
        if (x.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, Object> b = Json.obj(Json.parse(body(x)));
            if (Json.bool(b, "clear", false)) { COMMANDS.clear(); }
            else if (b.containsKey("ts")) {
                long ts = (long) Json.numD(b, "ts", 0);
                COMMANDS.removeIf(c -> { Object t = c.get("ts"); return t instanceof Number && ((Number) t).longValue() == ts; });
            }
            send(x, 200, Json.write(Map.of("ok", true)));
            return;
        }
        send(x, 405, "{\"ok\":false}");
    }

    static void apiOutputCommands(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            send(x, 200, Json.write(Map.of("ok", true, "commands", new ArrayList<>(OUTPUT_COMMANDS))));
            return;
        }
        if (x.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, Object> b = Json.obj(Json.parse(body(x)));
            if (Json.bool(b, "clear", false)) { OUTPUT_COMMANDS.clear(); }
            else if (b.containsKey("ts")) {
                long ts = (long) Json.numD(b, "ts", 0);
                OUTPUT_COMMANDS.removeIf(c -> { Object t = c.get("ts"); return t instanceof Number && ((Number) t).longValue() == ts; });
            }
            send(x, 200, Json.write(Map.of("ok", true)));
            return;
        }
        send(x, 405, "{\"ok\":false}");
    }

    // ---------- lan ip ----------
    static String lanIp() {
        try {
            var ifs = NetworkInterface.getNetworkInterfaces();
            List<String> all = new ArrayList<>();
            while (ifs.hasMoreElements()) {
                var ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                var addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()
                            && !a.getHostAddress().startsWith("169.254")) {
                        all.add(a.getHostAddress());
                    }
                }
            }
            for (String ip : all) {
                if (ip.startsWith("192.168.") || ip.startsWith("10.")
                        || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
                    return ip;
                }
            }
            if (!all.isEmpty()) return all.get(0);
        } catch (Exception ignored) {}
        return "localhost";
    }

    static String queryParam(HttpExchange x, String name) {
        String q = x.getRequestURI().getQuery();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            String[] pp = kv.split("=", 2);
            if (pp.length == 2 && pp[0].equals(name)) {
                try { return URLDecoder.decode(pp[1], StandardCharsets.UTF_8); } catch (Exception e) { return pp[1]; }
            }
            if (pp.length == 1 && pp[0].equals(name)) return "";
        }
        return null;
    }
}
