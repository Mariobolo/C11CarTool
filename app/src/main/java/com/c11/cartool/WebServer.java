package com.c11.cartool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量级嵌入式 HTTP 服务器（无外部依赖）
 *
 * 提供 Web 远程控制界面，手机扫码即可：
 *   - 查看车辆状态（电量、续航、里程、胎压等）
 *   - 执行车控命令（空调、车窗、车门等）
 *   - 查看实时日志
 *   - 运行一键诊断
 *
 * 基于 Java ServerSocket 实现，无需 NanoHTTPD 等外部库
 * v0.3.7 安全加固：
 *   - /api/control 需要 X-Auth-Token 鉴权（token 在启动时随机生成）
 *   - CORS 收紧（不再 * 全开）
 *   - escapeJson 补全控制字符/换行符
 */
public class WebServer {

    private static final String TAG = "WebServer";
    private static final int DEFAULT_PORT = 8080;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private int port = DEFAULT_PORT;
    private static volatile int lastStartedPort = -1;

    // [SECURITY] Web 控制鉴权 token（启动时随机生成，二维码 URL 携带）
    private volatile String authToken = null;

    public static int lastStartedPort() { return lastStartedPort; }
    private com.c11.cartool.vehicle.VehicleController vehicleController;

    public void setVehicleController(com.c11.cartool.vehicle.VehicleController vc) {
        this.vehicleController = vc;
    }

    /** 获取当前鉴权 token（用于生成二维码 URL） */
    public String getAuthToken() { return authToken; }

    /**
     * 启动 Web 服务器
     */
    /** 端口探测上限（preferred..preferred+20） */
    private static final int PORT_SCAN_RANGE = 20;

    public boolean start(int preferredPort) {
        if (running) {
            Logger.warn(TAG, "服务器已在运行中");
            return false;
        }
        // [SECURITY] 每次启动生成新 token
        authToken = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Logger.info(TAG, "Web 控制 token 已生成: " + authToken);
        // 端口自适应：被占用则自动递增，绑定 0.0.0.0（所有网卡可达）
        ServerSocket ss = null;
        int chosen = -1;
        for (int offset = 0; offset <= PORT_SCAN_RANGE; offset++) {
            int tryPort = preferredPort + offset;
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress("0.0.0.0", tryPort), 50);
                chosen = tryPort;
                break;
            } catch (IOException e) {
                Logger.warn(TAG, "端口 " + tryPort + " 被占用/不可用，尝试下一个");
                try { if (ss != null) ss.close(); } catch (Exception ignored) {}
                ss = null;
            }
        }
        if (ss == null) {
            Logger.error(TAG, "端口 " + preferredPort + "~" + (preferredPort + PORT_SCAN_RANGE) + " 均不可用");
            return false;
        }
        this.port = chosen;
        lastStartedPort = chosen;
        serverSocket = ss;
        try {
            running = true;
            serverThread = new Thread(this::serverLoop, "web-server");
            serverThread.start();
            Logger.ok(TAG, "Web 服务器已启动，端口: " + chosen
                    + (chosen != preferredPort ? "（首选 " + preferredPort + " 被占用，已自动切换）" : ""));
            Logger.ok(TAG, "访问地址: http://<车机IP>:" + chosen + "/?t=" + authToken);
            return true;
        } catch (Exception e) {
            running = false;
            Logger.error(TAG, "启动 Web 服务器失败: " + e);
            return false;
        }
    }

    /** 本机 HTTP 自检：127.0.0.1:port/api/info 是否返回 200 */
    public boolean selfTest() {
        if (!running) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 1500);
            OutputStream o = s.getOutputStream();
            o.write("GET /api/info HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
            o.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = r.readLine();
            boolean ok = line != null && line.contains(" 200 ");
            Logger.info(TAG, "Web 自检 127.0.0.1:" + port + " -> " + (ok ? "200 OK" : ("失败: " + line)));
            return ok;
        } catch (Exception e) {
            Logger.warn(TAG, "Web 自检失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 停止 Web 服务器
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        if (serverThread != null) {
            serverThread.interrupt();
        }
        Logger.info(TAG, "Web 服务器已停止");
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }

    /**
     * 获取车机全部 IPv4 地址（遍历常见网卡，跳过 loopback）。
     * 车机以太网走 eth0（车内网 192.168.1.x），WiFi 走 wlan0，均需覆盖。
     */
    /**
     * 纯 Java 枚举全部非 loopback IPv4（不依赖 shell/ADB，主线程可安全调用）。
     * 返回 "网卡名 IP" 列表，顺序：eth*、wlan*、其他。
     */
    public static java.util.List<String> getAllIps() {
        java.util.List<String> ips = new java.util.ArrayList<>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                } catch (Exception ignored) { continue; }
                String name = ni.getName();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isLoopbackAddress()) continue;
                    if (a instanceof Inet4Address) {
                        ips.add(name + " " + a.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            Logger.warn(TAG, "枚举网卡失败: " + e.getMessage());
        }
        // 扫码/手机访问走 wlan（手机连同一 WiFi）。eth0 是车内 SOME/IP 网(192.168.1.x)，手机不可路由，排最后
        java.util.Collections.sort(ips, new java.util.Comparator<String>() {
            @Override public int compare(String x, String y) { return rank(x) - rank(y); }
            int rank(String s) {
                if (s.startsWith("wlan")) return 0;
                if (s.startsWith("eth")) return 2;
                return 1;
            }
        });
        if (ips.isEmpty()) ips.add("无IP 127.0.0.1");
        return ips;
    }

    /**
     * 二维码与主访问地址：必须手机可达，故优先 wlan*（手机连同一 WiFi）。
     * eth0(车内 SOME/IP 网 192.168.1.x) 对手机不可路由，仅在没有 wlan 时才回退其它网卡。
     */
    public static String getDeviceIp() {
        java.util.List<String> ips = getAllIps();
        for (String s : ips) { String[] p = s.split(" ");
            if (p.length >= 2 && p[0].startsWith("wlan")) return p[1]; }
        // NPE safe: p[1] accessed after length check
        for (String s : ips) { String[] p = s.split(" ");
            if (p.length >= 2 && !p[0].startsWith("eth") && !p[1].startsWith("127.")) return p[1]; }
        for (String s : ips) { String[] p = s.split(" ");
            if (p.length >= 2 && !p[1].startsWith("127.")) return p[1]; }
        return "127.0.0.1";
    }

    // ═══ 服务器主循环 ═══

    private void serverLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client), "web-client").start();
            } catch (IOException e) {
                if (running) {
                    Logger.error(TAG, "接受连接失败: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // 读取请求行
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                client.close();
                return;
            }

            // 解析请求
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(out, 400, "text/plain", "Bad Request".getBytes());
                client.close();
                return;
            }

            String method = parts[0];
            String path = parts[1];

            // 读取请求头（跳过，直到空行）
            Map<String, String> headers = new HashMap<>();
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String value = line.substring(colon + 1).trim();
                    headers.put(key, value);
                    if (key.equals("content-length")) {
                        try { contentLength = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // 读取请求体
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = reader.read(buf, 0, contentLength);
                if (read > 0) body = new String(buf, 0, read);
            }

            // 路由
            try {
                route(method, path, body, out);
            } catch (Exception e) {
                Logger.error(TAG, "处理请求异常: " + path + " - " + e.getMessage());
                sendResponse(out, 500, "text/plain", ("Internal Server Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }

            out.flush();
            client.close();
        } catch (IOException e) {
            Logger.error(TAG, "客户端处理异常: " + e.getMessage());
        }
    }

    // ═══ 路由 ═══

    private void route(String method, String path, String body, java.util.Map<String, String> headers, OutputStream out) throws IOException {
        // 去掉 query string 得到纯 path
        String purePath = path;
        int qIdx = path.indexOf('?');
        if (qIdx > 0) purePath = path.substring(0, qIdx);

        // [SECURITY] /api/control 鉴权检查
        if (purePath.equals("/api/control")) {
            String token = headers != null ? headers.get("x-auth-token") : null;
            if (token == null) token = extractTokenFromPath(path);
            if (authToken != null && !authToken.isEmpty() && !authToken.equals(token)) {
                Logger.warn(TAG, "[SECURITY] /api/control 鉴权失败，拒绝请求");
                sendResponse(out, 401, "application/json; charset=utf-8",
                        "{\"success\":false,\"error\":\"unauthorized: missing or invalid token\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
        }

        // 静态页面（携带 token 注入到 JS）
        if (purePath.equals("/") || purePath.equals("/index.html")) {
            String html = WebPages.getIndexPage(authToken);
            sendResponse(out, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // API: 获取车辆状态
        if (purePath.equals("/api/status") && method.equals("GET")) {
            String json = getVehicleStatusJson();
            sendResponse(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // API: 执行车控命令
        if (purePath.equals("/api/control") && method.equals("POST")) {
            String result = executeControl(body);
            sendResponse(out, 200, "application/json; charset=utf-8", result.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // API: 获取日志
        if (purePath.equals("/api/logs") && method.equals("GET")) {
            String json = getLogsJson();
            sendResponse(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // API: 运行诊断
        if (purePath.equals("/api/diagnostic") && method.equals("GET")) {
            String report = DiagnosticMode.runFullDiagnostic(null);
            String json = "{\"success\":true,\"report\":" + escapeJson(report) + "}";
            sendResponse(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // API: 服务器信息
        if (purePath.equals("/api/info") && method.equals("GET")) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true");
            sb.append(",\"ip\":\"").append(getDeviceIp()).append("\"");
            sb.append(",\"ips\":[");
            java.util.List<String> ips = getAllIps();
            for (int i = 0; i < ips.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(ips.get(i))).append("\"");
            }
            sb.append("]");
            sb.append(",\"port\":").append(port);
            sb.append(",\"adbConnected\":").append(Sh.isAdbConnected());
            sb.append(",\"uid\":").append(Sh.getAdbUid());
            sb.append(",\"permission\":\"").append(escapeJson(Sh.getPermissionLabel())).append("\"");
            sb.append(",\"version\":\"").append(AppInfo.VERSION).append("\"");
            sb.append(",\"running\":").append(running);
            sb.append("}");
            sendResponse(out, 200, "application/json; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }

        // 404
        sendResponse(out, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
    }

    // ═══ API 实现 ═══

    private String getVehicleStatusJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"adbConnected\":").append(Sh.isAdbConnected());
        sb.append(",\"uid\":").append(Sh.getAdbUid());
        sb.append(",\"permission\":\"").append(Sh.getPermissionLabel()).append("\"");
        sb.append(",\"data\":{");

        // 读取各项车辆数据
        String[][] keys = {
            {"battery_soc", "battery"},
            {"vehicle_range", "range"},
            {"vehicle_odo", "odometer"},
            {"vehicle_speed", "speed"},
            {"gear", "gear"},
            {"charging_state", "charging"},
            {"tire_pressure_fl", "tireFL"},
            {"tire_pressure_fr", "tireFR"},
            {"tire_pressure_rl", "tireRL"},
            {"tire_pressure_rr", "tireRR"},
            {"ac_temperature", "acTemp"},
            {"ac_fan_speed", "acFan"}
        };

        boolean first = true;
        for (String[] kv : keys) {
            Sh.Result r = Sh.run("settings get global " + kv[0]);
            String val = r.out != null ? r.out.trim() : "";
            if (!first) sb.append(",");
            sb.append("\"").append(kv[1]).append("\":\"").append(escapeJson(val)).append("\"");
            first = false;
        }

        // 基本信息
        sb.append(",\"model\":\"").append(escapeJson(Sh.out("getprop ro.product.model"))).append("\"");
        sb.append(",\"androidVersion\":\"").append(escapeJson(Sh.out("getprop ro.build.version.release"))).append("\"");

        sb.append("}}");
        return sb.toString();
    }

    /** 从 URL path 中提取 token 参数 */
    private static String extractTokenFromPath(String path) {
        int q = path.indexOf('?');
        if (q < 0) return null;
        String query = path.substring(q + 1);
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals("t")) {
                return kv.substring(eq + 1);
            }
        }
        return null;
    }

    private String executeControl(String body) {
        try {
            String action = extractJsonValue(body, "action");
            if (action == null || action.isEmpty()) {
                return "{\"success\":false,\"error\":\"missing action\"}";
            }

            // [SECURITY] action 白名单校验，防止注入
            action = action.trim();
            if (!action.matches("[a-zA-Z0-9_]{1,50}")) {
                Logger.warn(TAG, "[SECURITY] 非法 action: " + action);
                return "{\"success\":false,\"error\":\"invalid action format\"}";
            }

            Logger.info(TAG, "[Web控制] action=" + action);
            boolean ok = false;
            String detail = "";

            if (vehicleController != null) {
                switch (action) {
                    // 车锁
                    case "lockCar": ok = vehicleController.lockCar(); break;
                    case "unlockCar": ok = vehicleController.unlockCar(); break;
                    // 灯光
                    case "lowBeamOn": ok = vehicleController.lowBeamOn(); break;
                    case "lowBeamOff": ok = vehicleController.lowBeamOff(); break;
                    case "fogLightOn": ok = vehicleController.fogLightOn(); break;
                    case "fogLightOff": ok = vehicleController.fogLightOff(); break;
                    case "positionLightOn": ok = vehicleController.positionLightOn(); break;
                    case "positionLightOff": ok = vehicleController.positionLightOff(); break;
                    // 空调
                    case "acMaxOn": ok = vehicleController.acMaxOn(); break;
                    case "acMaxOff": ok = vehicleController.acMaxOff(); break;
                    case "acOn": ok = vehicleController.acOn(); break;
                    case "acOff": ok = vehicleController.acOff(); break;
                    case "acTempUp": ok = vehicleController.setAcTemperature(24); break;
                    case "acTempDown": ok = vehicleController.setAcTemperature(20); break;
                    case "acFanUp": ok = vehicleController.setAcFanSpeed(5); break;
                    case "acFanDown": ok = vehicleController.setAcFanSpeed(2); break;
                    // 除霜
                    case "frontDefrost": ok = vehicleController.frontDefrostOn(); break;
                    case "rearDefrost": ok = vehicleController.rearDefrostOn(); break;
                    // 儿童锁
                    case "leftChildLock": ok = vehicleController.leftChildLockOn(); break;
                    case "rightChildLock": ok = vehicleController.rightChildLockOn(); break;
                    // 后备箱
                    case "openTrunk": ok = vehicleController.openTrunk(); break;
                    case "closeTrunk": ok = vehicleController.closeTrunk(); break;
                    // 车窗
                    case "windowFLUp": ok = vehicleController.setWindow("front_left", 100); break;
                    case "windowFLDown": ok = vehicleController.setWindow("front_left", 0); break;
                    case "windowFRUp": ok = vehicleController.setWindow("front_right", 100); break;
                    case "windowFRDown": ok = vehicleController.setWindow("front_right", 0); break;
                    case "windowRLUp": ok = vehicleController.setWindow("rear_left", 100); break;
                    case "windowRLDown": ok = vehicleController.setWindow("rear_left", 0); break;
                    case "windowRRUp": ok = vehicleController.setWindow("rear_right", 100); break;
                    case "windowRRDown": ok = vehicleController.setWindow("rear_right", 0); break;
                    // 360
                    case "open360": ok = vehicleController.open360View(); break;
                    default:
                        return "{\"success\":false,\"error\":\"unknown action: " + escapeJson(action) + "\"}";
                }
            } else {
                return "{\"success\":false,\"error\":\"VehicleController not initialized\"}";
            }

            return "{\"success\":" + ok + ",\"action\":\"" + escapeJson(action) + "\",\"detail\":\"" + escapeJson(detail) + "\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }
    private String getLogsJson() {
        String logs = Logger.getRecentLogs(200);
        return "{\"success\":true,\"logs\":\"" + escapeJson(logs) + "\"}";
    }

    // ═══ 工具方法 ═══

    private void sendResponse(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String statusText = status == 200 ? "OK" : status == 404 ? "Not Found" : status == 500 ? "Internal Server Error" : status == 401 ? "Unauthorized" : "Error";
        // [SECURITY] CORS 收紧：不再 * 全开
        String header = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                       "Content-Type: " + contentType + "\r\n" +
                       "Content-Length: " + body.length + "\r\n" +
                       "X-Content-Type-Options: nosniff\r\n" +
                       "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    /**
     * [FIX] JSON 字符串转义（补全控制字符/换行/Unicode）。
     * 此前只处理了 " 和 \，换行符/控制字符会导致 JSON 解析失败。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return null;
        idx++;
        // 跳过空白
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length()) return null;

        if (json.charAt(idx) == '"') {
            // 字符串值
            idx++;
            StringBuilder sb = new StringBuilder();
            while (idx < json.length() && json.charAt(idx) != '"') {
                if (json.charAt(idx) == '\\' && idx + 1 < json.length()) {
                    char next = json.charAt(idx + 1);
                    switch (next) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        default: sb.append(next);
                    }
                    idx += 2;
                } else {
                    sb.append(json.charAt(idx));
                    idx++;
                }
            }
            return sb.toString();
        } else {
            // 数字/布尔值
            StringBuilder sb = new StringBuilder();
            while (idx < json.length() && json.charAt(idx) != ',' && json.charAt(idx) != '}') {
                sb.append(json.charAt(idx));
                idx++;
            }
            return sb.toString().trim();
        }
    }
}
