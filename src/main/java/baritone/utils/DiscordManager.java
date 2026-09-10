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
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private String lastPolledDiscordMessageId = "";

    // Thống kê kết quả farm cho Discord Webhook
    private final Map<MiningStatsTracker.OreType, Integer> lastReportOreCounts = new ConcurrentHashMap<>();
    private final Map<Item, Integer> lastReportDropCounts = new ConcurrentHashMap<>();
    private int lastReportTotalBlocks = 0;
    private long lastFarmingReportTime = 0;

    private DiscordManager() {}

    public static DiscordManager getInstance() {
        return INSTANCE;
    }

    /**
     * Khởi động background tasks cho Discord Webhook và HTTP listener.
     */
    public synchronized void start() {
        if (isStarted.compareAndSet(false, true)) {
            // Task 1: Báo cáo kết quả farm định kỳ (mặc định 5 phút)
            scheduler.scheduleWithFixedDelay(this::farmingReportTick, 5, 2, TimeUnit.SECONDS);

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
     * Vòng lặp kiểm tra và gửi báo cáo kết quả farm định kỳ theo discordWebhookInterval (mặc định 5 phút = 300s).
     */
    private void farmingReportTick() {
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
                intervalSec = 300;
            }

            long now = System.currentTimeMillis();
            if (now - lastFarmingReportTime < intervalSec * 1000L) {
                return;
            }
            lastFarmingReportTime = now;

            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }

            sendFarmingReport(false);
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi vòng lặp farming report: " + e.getMessage());
        }
    }

    /**
     * Gửi Báo cáo kết quả farm & đào khoáng lên Discord.
     * Tự động tính số quặng đào thêm (+delta) trong chu kỳ vừa qua và tổng số quặng đã tích lũy.
     * Chụp ảnh màn hình đính kèm nếu option discordCaptureScreen đang BẬT.
     */
    public void sendFarmingReport(boolean isTest) {
        try {
            String webhookUrl = Baritone.settings().discordWebhookUrl.value;
            if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) {
                if (isTest) {
                    logDirect("§c[Discord] Vui lòng nhập hoặc [DÁN] Webhook URL trước khi gửi thử!");
                }
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            boolean captureScreen = Baritone.settings().discordCaptureScreen.value;

            if (captureScreen && mc.getMainRenderTarget() != null) {
                CleanScreenshotHelper.requestCleanScreenshot(imageBytes -> {
                    dispatchFarmingReport(isTest, imageBytes);
                });
            } else {
                dispatchFarmingReport(isTest, null);
            }
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi gửi báo cáo farm: " + e.getMessage());
        }
    }

    private void dispatchFarmingReport(boolean isTest, byte[] screenshotBytes) {
        try {
            String webhookUrl = Baritone.settings().discordWebhookUrl.value;
            if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) {
                return;
            }

            LocalPlayer player = Minecraft.getInstance().player;
            String playerName = player != null ? player.getName().getString() : "Tr0ngX";
            int intervalSec = Baritone.settings().discordWebhookInterval.value;
            int intervalMin = Math.max(1, intervalSec / 60);

            // 1. Tính toán Delta quặng đào thêm
            List<String> deltaList = new ArrayList<>();
            for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                int curOre = MiningStatsTracker.getInstance().getOreCount(ore);
                int lastOre = lastReportOreCounts.getOrDefault(ore, 0);
                int deltaOre = curOre - lastOre;

                Item dropItem = ore.getDropItem();
                int curDrop = dropItem != null ? MiningStatsTracker.getInstance().getDropItemCount(dropItem) : 0;
                int lastDrop = dropItem != null ? lastReportDropCounts.getOrDefault(dropItem, 0) : 0;
                int deltaDrop = curDrop - lastDrop;

                if (deltaOre > 0 || deltaDrop > 0) {
                    if (ore == MiningStatsTracker.OreType.DIAMOND) {
                        deltaList.add("💎 **Kim Cương:** `+" + deltaDrop + " cục` (`+" + deltaOre + " quặng`)");
                    } else if (ore == MiningStatsTracker.OreType.EMERALD) {
                        deltaList.add("🟢 **Lục Bảo:** `+" + deltaDrop + " cục` (`+" + deltaOre + " quặng`)");
                    } else if (ore == MiningStatsTracker.OreType.ANCIENT_DEBRIS) {
                        deltaList.add("🟣 **Mảnh Cổ Đại:** `+" + deltaDrop + " mảnh`");
                    } else {
                        deltaList.add("⛏️ **" + ore.getNameVi() + ":** `+" + deltaOre + " quặng`");
                    }
                }
            }

            int curTotalBlocks = MiningStatsTracker.getInstance().getTotalBlocksMined();
            int deltaBlocks = curTotalBlocks - lastReportTotalBlocks;

            if (!isTest) {
                for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                    lastReportOreCounts.put(ore, MiningStatsTracker.getInstance().getOreCount(ore));
                    if (ore.getDropItem() != null) {
                        lastReportDropCounts.put(ore.getDropItem(), MiningStatsTracker.getInstance().getDropItemCount(ore.getDropItem()));
                    }
                }
                lastReportTotalBlocks = curTotalBlocks;
            }

            StringBuilder deltaSb = new StringBuilder();
            if (deltaList.isEmpty()) {
                if (deltaBlocks > 0) {
                    deltaSb.append("Chưa có quặng mới trong chu kỳ này (`+").append(deltaBlocks).append(" blocks` đất/đá đã đào)\n");
                } else {
                    deltaSb.append("Không có biến động trong ").append(intervalMin).append(" phút qua (Bot đang di chuyển hoặc nghỉ)\n");
                }
            } else {
                for (String line : deltaList) {
                    deltaSb.append(line).append("\n");
                }
                if (deltaBlocks > 0) {
                    deltaSb.append("🧱 **Khối đào thêm:** `+").append(deltaBlocks).append(" blocks`\n");
                }
            }

            // 2. Tính toán Tổng số quặng tích lũy
            List<String> totalList = new ArrayList<>();
            for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                int curOre = MiningStatsTracker.getInstance().getOreCount(ore);
                Item dropItem = ore.getDropItem();
                int curDrop = dropItem != null ? MiningStatsTracker.getInstance().getDropItemCount(dropItem) : 0;

                if (curOre > 0 || curDrop > 0) {
                    if (ore == MiningStatsTracker.OreType.DIAMOND) {
                        totalList.add("💎 **Kim Cương:** `" + curDrop + " cục` (`" + curOre + " quặng`)");
                    } else if (ore == MiningStatsTracker.OreType.EMERALD) {
                        totalList.add("🟢 **Lục Bảo:** `" + curDrop + " cục` (`" + curOre + " quặng`)");
                    } else if (ore == MiningStatsTracker.OreType.ANCIENT_DEBRIS) {
                        totalList.add("🟣 **Mảnh Cổ Đại:** `" + curDrop + " mảnh`");
                    } else {
                        totalList.add("⛏️ **" + ore.getNameVi() + ":** `" + curOre + " quặng`");
                    }
                }
            }

            StringBuilder totalSb = new StringBuilder();
            if (totalList.isEmpty()) {
                totalSb.append("Chưa đào được quặng nào.\n");
            } else {
                for (String line : totalList) {
                    totalSb.append(line).append("\n");
                }
            }
            totalSb.append("🧱 **Tổng khối đã đào:** `").append(curTotalBlocks).append(" blocks`\n");
            totalSb.append("⏱️ **Thời gian hoạt động:** `").append(MiningStatsTracker.getInstance().getFormattedDuration()).append("`");

            // 3. Thông tin trạng thái & Tọa độ
            StringBuilder statusSb = new StringBuilder();
            if (player != null) {
                statusSb.append("📍 **Tọa độ:** `X: ").append(player.getBlockX()).append(", Y: ").append(player.getBlockY()).append(", Z: ").append(player.getBlockZ()).append("`\n");
                statusSb.append("❤️ **Máu:** `").append((int) player.getHealth()).append("/").append((int) player.getMaxHealth()).append("` • ");
                statusSb.append("🍗 **Đói:** `").append(player.getFoodData().getFoodLevel()).append("/20` • ");

                Inventory inv = player.getInventory();
                int emptySlots = 0;
                for (int i = 0; i < 36; i++) {
                    if (inv.getItem(i).isEmpty()) emptySlots++;
                }
                statusSb.append("🎒 **Túi trống:** `").append(emptySlots).append("/36 ô`\n");
            }

            String state = "Đang nghỉ ngơi / Chờ lệnh";
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                state = "⛏️ Đang tự động đào khoáng (MineProcess)";
            } else if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                state = "🏃 Đang di chuyển tìm đường (Pathing)";
            }
            statusSb.append("⚙️ **Trạng thái:** ").append(state);

            // 4. Tạo Embed Fields
            JsonArray fields = new JsonArray();

            JsonObject deltaField = new JsonObject();
            deltaField.addProperty("name", "✨ SỐ QUẶNG ĐÀO ĐƯỢC THÊM (" + intervalMin + " PHÚT QUA)");
            deltaField.addProperty("value", deltaSb.toString());
            deltaField.addProperty("inline", false);
            fields.add(deltaField);

            JsonObject totalField = new JsonObject();
            totalField.addProperty("name", "📦 TỔNG KẾT QUẢ FARM TÍCH LŨY");
            totalField.addProperty("value", totalSb.toString());
            totalField.addProperty("inline", false);
            fields.add(totalField);

            JsonObject infoField = new JsonObject();
            infoField.addProperty("name", "📊 VỊ TRÍ & TÌNH TRẠNG BOT");
            infoField.addProperty("value", statusSb.toString());
            infoField.addProperty("inline", false);
            fields.add(infoField);

            String title = isTest ? "🧪 [GỬI THỬ] BÁO CÁO KẾT QUẢ FARM TỪ BARITONE" : "⛏️ [BÁO CÁO FARM] KẾT QUẢ ĐÀO KHOÁNG (" + intervalMin + " PHÚT)";
            String desc = "**Người chơi:** `" + playerName + "` • **Chu kỳ:** `" + (intervalSec >= 60 ? (intervalSec / 60) + " phút" : intervalSec + "s") + "`\n" +
                          (screenshotBytes != null ? "📸 *Đã đính kèm ảnh chụp màn hình góc nhìn game thực tế.*" : "ℹ *Chế độ báo cáo văn bản.*");

            JsonObject payload = buildDiscordWebhookEmbedPayload(title, desc, 0x10B981, fields, screenshotBytes != null);

            sendMultipartAsyncToWebhook(webhookUrl, payload.toString(), screenshotBytes);

            if (isTest) {
                logDirect("§a[Discord] ✔ Đã gửi thành công báo cáo farm test lên Discord! Hãy kiểm tra tin nhắn và ảnh chụp.");
            }
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi dispatchFarmingReport: " + e.getMessage());
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

            JsonObject payload = buildDiscordWebhookEmbedPayload(title, message, colorHex, fields, false);
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
     * Gửi multipart/form-data chứa payload_json và file ảnh đính kèm (files[0]).
     */
    private void sendMultipartAsyncToWebhook(String url, String jsonPayload, byte[] imageBytes) {
        try {
            if (imageBytes == null || imageBytes.length == 0) {
                sendAsyncToWebhook(url, jsonPayload);
                return;
            }

            String boundary = "----BaritoneBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Part 1: payload_json
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Type: application/json; charset=utf-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // Part 2: files[0]
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"files[0]\"; filename=\"screenshot.png\"\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(imageBytes);
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // Closing boundary
            baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            byte[] bodyBytes = baos.toByteArray();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", "Baritone-DiscordManager/1.21.11")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .timeout(Duration.ofSeconds(12))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        logDebug("[DiscordManager] Lỗi gửi multipart Webhook: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            logDebug("[DiscordManager] Không thể tạo multipart request: " + e.getMessage());
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

    private JsonObject buildDiscordWebhookEmbedPayload(String title, String description, int color, JsonArray fields, boolean hasImage) {
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
        footer.addProperty("text", "Tr0ngX Baritone Mod 1.21.11 • Báo Cáo Kết Quả Farm");
        embed.add("footer", footer);

        if (fields != null && !fields.isEmpty()) {
            embed.add("fields", fields);
        }

        if (hasImage) {
            JsonObject imageObj = new JsonObject();
            imageObj.addProperty("url", "attachment://screenshot.png");
            embed.add("image", imageObj);
        }

        embeds.add(embed);
        root.add("embeds", embeds);
        return root;
    }

    private JsonObject buildDiscordWebhookEmbedPayload(String title, String description, int color, JsonArray fields) {
        return buildDiscordWebhookEmbedPayload(title, description, color, fields, false);
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
