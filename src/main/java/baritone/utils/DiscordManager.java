/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.utils.Helper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quản lý gửi Telemetry / Alert đến Discord Webhook theo chu kỳ tùy chỉnh,
 * và nhận lệnh điều khiển từ xa (!doihuong, !mine, !stop) qua:
 * 1. Discord Bot Token polling trực tiếp từ Channel Discord.
 * 2. Embedded REST HTTP Server (Port 25590).
 * 3. In-game chat listener.
 */
public final class DiscordManager implements Helper {

    private static final DiscordManager INSTANCE = new DiscordManager();
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "Baritone-DiscordManager");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private HttpServer httpServer = null;
    private int currentHttpPort = -1;
    private long lastTelemetryTime = 0;
    private String lastPolledDiscordMessageId = "";

    private DiscordManager() {}

    public static DiscordManager getInstance() {
        return INSTANCE;
    }

    /**
     * Khởi động background tasks cho Discord Webhook và HTTP listener.
     */
    public synchronized void start() {
        if (isStarted.compareAndSet(false, true)) {
            // Task 1: Telemetry định kỳ và Alert
            scheduler.scheduleWithFixedDelay(this::telemetryTick, 2, 1, TimeUnit.SECONDS);

            // Task 2: Polling Discord Bot Channel (nếu có cấu hình bot token)
            scheduler.scheduleWithFixedDelay(this::discordChannelPollTick, 3, 2, TimeUnit.SECONDS);

            // Task 3: Đảm bảo HTTP Server đang lắng nghe
            ensureHttpServerRunning();

            logDebug("[DiscordManager] Đã khởi động hệ thống Discord Webhook & Remote Control thành công.");
        }
    }

    /**
     * Khởi chạy Embedded HTTP Server trên cổng chỉ định (mặc định 25590)
     * để nhận lệnh điều khiển trực tiếp (GET/POST /api/doihuong, /api/status, /api/command).
     */
    public synchronized void ensureHttpServerRunning() {
        try {
            int port = Baritone.settings().discordHttpPort.value;
            if (port <= 0) port = 25590;

            if (httpServer != null && currentHttpPort == port) {
                return;
            }

            if (httpServer != null) {
                try {
                    httpServer.stop(0);
                } catch (Exception ignored) {}
                httpServer = null;
            }

            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

            // Endpoint 1: /api/doihuong (Lập tức đổi hướng 100% giải kẹt)
            httpServer.createContext("/api/doihuong", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    try {
                        String newDir = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().forceDoiHuong();
                        JsonObject resp = new JsonObject();
                        resp.addProperty("status", "ok");
                        resp.addProperty("command", "!doihuong");
                        resp.addProperty("newDirection", newDir);
                        resp.addProperty("message", "Đã đổi hướng 100% sang " + newDir + "!");
                        sendJsonResponse(exchange, 200, resp.toString());
                    } catch (Exception e) {
                        JsonObject err = new JsonObject();
                        err.addProperty("status", "error");
                        err.addProperty("message", e.getMessage());
                        sendJsonResponse(exchange, 500, err.toString());
                    }
                }
            });

            // Endpoint 2: /api/status (Xem trạng thái telemetry trực tiếp)
            httpServer.createContext("/api/status", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    try {
                        JsonObject status = buildTelemetryJson();
                        sendJsonResponse(exchange, 200, status.toString());
                    } catch (Exception e) {
                        JsonObject err = new JsonObject();
                        err.addProperty("status", "error");
                        err.addProperty("message", e.getMessage());
                        sendJsonResponse(exchange, 500, err.toString());
                    }
                }
            });

            // Endpoint 3: /api/command (Chạy lệnh Baritone tổng quát)
            httpServer.createContext("/api/command", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    try {
                        String cmd = "";
                        String query = exchange.getRequestURI().getQuery();
                        if (query != null && query.contains("cmd=")) {
                            for (String param : query.split("&")) {
                                if (param.startsWith("cmd=")) {
                                    cmd = java.net.URLDecoder.decode(param.substring(4), StandardCharsets.UTF_8);
                                    break;
                                }
                            }
                        }
                        if (cmd.isEmpty() && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                            try (InputStream is = exchange.getRequestBody()) {
                                cmd = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                            }
                        }

                        if (cmd.equalsIgnoreCase("!doihuong") || cmd.equalsIgnoreCase("doihuong")) {
                            String newDir = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().forceDoiHuong();
                            JsonObject resp = new JsonObject();
                            resp.addProperty("status", "ok");
                            resp.addProperty("command", cmd);
                            resp.addProperty("result", "Đổi hướng sang " + newDir);
                            sendJsonResponse(exchange, 200, resp.toString());
                            return;
                        }

                        String finalCmd = cmd;
                        Minecraft.getInstance().execute(() -> {
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(finalCmd);
                        });

                        JsonObject resp = new JsonObject();
                        resp.addProperty("status", "ok");
                        resp.addProperty("command", cmd);
                        resp.addProperty("message", "Đã gửi lệnh vào Baritone!");
                        sendJsonResponse(exchange, 200, resp.toString());
                    } catch (Exception e) {
                        JsonObject err = new JsonObject();
                        err.addProperty("status", "error");
                        err.addProperty("message", e.getMessage());
                        sendJsonResponse(exchange, 500, err.toString());
                    }
                }
            });

            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            currentHttpPort = port;
            logDebug("[DiscordManager] Embedded HTTP Server sẵn sàng tại http://127.0.0.1:" + port);
        } catch (Exception e) {
            logDebug("[DiscordManager] Không thể mở HTTP server cổng: " + e.getMessage());
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Vòng lặp kiểm tra và gửi Telemetry định kỳ theo giây cấu hình trong discordWebhookInterval.
     */
    private void telemetryTick() {
        try {
            if (!Baritone.settings().discordWebhookEnabled.value) {
                return;
            }
            String webhookUrl = Baritone.settings().discordWebhookUrl.value;
            if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) {
                return;
            }

            int intervalSec = Baritone.settings().discordWebhookInterval.value;
            if (intervalSec <= 0) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastTelemetryTime < intervalSec * 1000L) {
                return;
            }
            lastTelemetryTime = now;

            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }

            JsonObject payload = buildDiscordWebhookEmbedPayload(
                    "⛏️ Baritone Bot Telemetry",
                    buildTelemetryDescription(player),
                    0x00D26A, // Xanh lá ngọc bích
                    buildTelemetryFields(player)
            );

            sendAsyncToWebhook(webhookUrl, payload.toString());
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi gửi telemetry: " + e.getMessage());
        }
    }

    /**
     * Gửi Alert tức thời lên Discord Webhook (dành cho sự kiện quan trọng: AntiDeath, Đổi hướng, Kẹt...).
     */
    public void sendAlert(String title, String message, int colorHex) {
        try {
            if (!Baritone.settings().discordWebhookEnabled.value) {
                return;
            }
            String webhookUrl = Baritone.settings().discordWebhookUrl.value;
            if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) {
                return;
            }

            LocalPlayer player = Minecraft.getInstance().player;
            JsonArray fields = new JsonArray();
            if (player != null) {
                JsonObject posField = new JsonObject();
                posField.addProperty("name", "📍 Tọa độ");
                posField.addProperty("value", "`X: " + player.getBlockX() + ", Y: " + player.getBlockY() + ", Z: " + player.getBlockZ() + "`");
                posField.addProperty("inline", true);
                fields.add(posField);

                JsonObject hpField = new JsonObject();
                hpField.addProperty("name", "❤️ Máu");
                hpField.addProperty("value", (int) player.getHealth() + " / " + (int) player.getMaxHealth());
                hpField.addProperty("inline", true);
                fields.add(hpField);
            }

            JsonObject payload = buildDiscordWebhookEmbedPayload(title, message, colorHex, fields);
            sendAsyncToWebhook(webhookUrl, payload.toString());
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi gửi alert: " + e.getMessage());
        }
    }

    public void sendAlert(String message) {
        sendAlert("🚨 [Baritone Thông Báo]", message, 0xFFA500); // Màu vàng cam
    }

    /**
     * Gửi POST JSON bất đồng bộ tới Webhook URL.
     */
    private void sendAsyncToWebhook(String url, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "Baritone-DiscordManager/1.21.11")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(6))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        logDebug("[DiscordManager] Lỗi kết nối Webhook: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            logDebug("[DiscordManager] Không thể tạo HTTP request webhook: " + e.getMessage());
        }
    }

    /**
     * Polling tin nhắn từ Channel Discord nếu người dùng cấu hình Bot Token & Channel ID.
     * Tự động nhận diện lệnh "!doihuong", "!mine", "!stop".
     */
    private void discordChannelPollTick() {
        try {
            String token = Baritone.settings().discordBotToken.value;
            String channelId = Baritone.settings().discordChannelId.value;
            if (token == null || token.trim().isEmpty() || channelId == null || channelId.trim().isEmpty()) {
                return;
            }

            String pollUrl = "https://discord.com/api/v10/channels/" + channelId.trim() + "/messages?limit=3";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl))
                    .header("Authorization", "Bot " + token.trim())
                    .header("User-Agent", "Baritone-DiscordBot/1.21.11")
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return;
            }

            JsonElement root = JsonParser.parseString(response.body());
            if (!root.isJsonArray()) {
                return;
            }

            JsonArray messages = root.getAsJsonArray();
            if (messages.isEmpty()) {
                return;
            }

            // Nếu lần đầu khởi động, chỉ ghi nhận message ID mới nhất làm mốc để không lặp lại tin nhắn cũ
            if (lastPolledDiscordMessageId.isEmpty()) {
                lastPolledDiscordMessageId = messages.get(0).getAsJsonObject().get("id").getAsString();
                return;
            }

            for (int i = messages.size() - 1; i >= 0; i--) {
                JsonObject msgObj = messages.get(i).getAsJsonObject();
                String msgId = msgObj.get("id").getAsString();
                if (msgId.compareTo(lastPolledDiscordMessageId) <= 0) {
                    continue;
                }
                lastPolledDiscordMessageId = msgId;

                // Kiểm tra tác giả: bỏ qua tin nhắn từ Bot để tránh phản hồi vòng lặp
                if (msgObj.has("author") && msgObj.getAsJsonObject("author").has("bot")
                        && msgObj.getAsJsonObject("author").get("bot").getAsBoolean()) {
                    continue;
                }

                String content = msgObj.has("content") ? msgObj.get("content").getAsString().trim() : "";
                if (content.equalsIgnoreCase("!doihuong") || content.equalsIgnoreCase("!doi_huong")) {
                    logDirect("§a[Discord] Nhận lệnh !doihuong từ Discord! Tiến hành đổi hướng ngay lập tức...");
                    String newDir = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().forceDoiHuong();

                    // Phản hồi lại vào channel Discord
                    replyToDiscordChannel(channelId, token, "🔄 [AntiLoop] Nhận lệnh **!doihuong** thành công! Đã đổi hướng 100% sang **" + newDir + "**!");
                } else if (content.equalsIgnoreCase("!stop")) {
                    Minecraft.getInstance().execute(() -> {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                    });
                    replyToDiscordChannel(channelId, token, "🛑 [Baritone] Đã dừng toàn bộ hành động theo lệnh Discord!");
                }
            }
        } catch (Exception e) {
            // Im lặng hoặc log nhẹ nếu mạng chập chờn
        }
    }

    private void replyToDiscordChannel(String channelId, String token, String replyText) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("content", replyText);

            String postUrl = "https://discord.com/api/v10/channels/" + channelId.trim() + "/messages";
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(postUrl))
                    .header("Authorization", "Bot " + token.trim())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HTTP_CLIENT.sendAsync(postReq, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    // ==========================================
    // CÁC HÀM XÂY DỰNG NỘI DUNG TELEMETRY
    // ==========================================

    private JsonObject buildDiscordWebhookEmbedPayload(String title, String description, int color, JsonArray fields) {
        JsonObject root = new JsonObject();
        root.addProperty("username", "Baritone AI Pathfinder (Tr0ngX)");
        root.addProperty("avatar_url", "https://raw.githubusercontent.com/cabaletta/baritone/master/src/main/resources/assets/baritone/icon.png");

        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Tr0ngX Baritone Mod 1.21.11 • KingMC.vn");
        embed.add("footer", footer);

        if (fields != null && !fields.isEmpty()) {
            embed.add("fields", fields);
        }

        embeds.add(embed);
        root.add("embeds", embeds);
        return root;
    }

    private String buildTelemetryDescription(LocalPlayer player) {
        String server = "Chơi Đơn (Singleplayer)";
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            server = serverData.ip;
        }

        String state = "Nghỉ ngơi / Chờ lệnh";
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
            state = "⛏️ Đang đào quặng (MineProcess)";
        } else if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            state = "🏃 Đang di chuyển (Pathing)";
        }

        return "**Người chơi**: `" + player.getName().getString() + "`\n" +
               "**Máy chủ**: `" + server + "`\n" +
               "**Hoạt động**: " + state;
    }

    private JsonArray buildTelemetryFields(LocalPlayer player) {
        JsonArray fields = new JsonArray();

        // Tọa độ
        JsonObject posField = new JsonObject();
        posField.addProperty("name", "📍 Tọa độ");
        posField.addProperty("value", "`X: " + player.getBlockX() + ", Y: " + player.getBlockY() + ", Z: " + player.getBlockZ() + "`");
        posField.addProperty("inline", true);
        fields.add(posField);

        // Máu
        JsonObject hpField = new JsonObject();
        hpField.addProperty("name", "❤️ Sinh lực");
        hpField.addProperty("value", (int) player.getHealth() + " / " + (int) player.getMaxHealth());
        hpField.addProperty("inline", true);
        fields.add(hpField);

        // Thức ăn
        JsonObject foodField = new JsonObject();
        foodField.addProperty("name", "🍗 Thức ăn");
        foodField.addProperty("value", player.getFoodData().getFoodLevel() + " / 20");
        foodField.addProperty("inline", true);
        fields.add(foodField);

        // Hướng đào
        JsonObject dirField = new JsonObject();
        dirField.addProperty("name", "🧭 Hướng nhìn / đào");
        dirField.addProperty("value", player.getDirection().getName().toUpperCase());
        dirField.addProperty("inline", true);
        fields.add(dirField);

        // Balo trống
        Inventory inv = player.getInventory();
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) {
                emptySlots++;
            }
        }
        JsonObject invField = new JsonObject();
        invField.addProperty("name", "🎒 Balo trống");
        invField.addProperty("value", emptySlots + " / 36 ô");
        invField.addProperty("inline", true);
        fields.add(invField);

        // AntiLoop status
        JsonObject antiLoopField = new JsonObject();
        antiLoopField.addProperty("name", "🛡️ AntiLoop 100%");
        antiLoopField.addProperty("value", "Gõ `!doihuong` để đổi hướng tức thì");
        antiLoopField.addProperty("inline", false);
        fields.add(antiLoopField);

        return fields;
    }

    public JsonObject buildTelemetryJson() {
        JsonObject obj = new JsonObject();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            obj.addProperty("player", player.getName().getString());
            obj.addProperty("x", player.getBlockX());
            obj.addProperty("y", player.getBlockY());
            obj.addProperty("z", player.getBlockZ());
            obj.addProperty("hp", player.getHealth());
            obj.addProperty("food", player.getFoodData().getFoodLevel());
            obj.addProperty("direction", player.getDirection().getName().toUpperCase());
            obj.addProperty("isMining", BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive());
            obj.addProperty("isPathing", BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing());
        }
        return obj;
    }
}
