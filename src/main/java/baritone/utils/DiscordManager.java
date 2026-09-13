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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Trích xuất số dư / tiền tệ cho Webhook & HUD
    // Hỗ trợ phân cách hàng nghìn bằng dấu chấm, dấu phẩy, khoảng trắng, dấu nháy đơn, và hậu tố k, m, b, K, M, B
    private static final String NUM_PART_REGEX = "[0-9]+(?:(?:[.,'\\u00A0\\u202F]|\\s+)[0-9]+)*(?:\\s*[kmbKMB])?";
    private static final String CURR_PREFIX_REGEX = "(?:[$₫¥€£]\\s*)";
    private static final String CURR_SUFFIX_REGEX = "(?:\\s*(?:[$₫¥€£]|xu|coins?|coin|điểm|diem|points?|bucks?|đ|vnđ|vnd))";
    private static final String BAL_KEYWORDS_REGEX = "(?:số\\s*dư\\s*ví|so\\s*du\\s*vi|số\\s*dư|so\\s*du|ví\\s*tiền|vi\\s*tien|ví|vi|balance|money|coins?|coin|xu|ngân\\s*hàng|ngan\\s*hang|tài\\s*sản|tai\\s*san|cash|funds?)";

    // 1. Khớp từ khóa số dư kèm số tiền (hỗ trợ cả "$ MONEY 136.69M" và "MONEY: 136.69M $")
    private static final Pattern BALANCE_WITH_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(?:[$₫¥€£]\\s*)?" + BAL_KEYWORDS_REGEX + "\\s*[:：\\-–—=]?\\s*(" + CURR_PREFIX_REGEX + "?" + NUM_PART_REGEX + CURR_SUFFIX_REGEX + "?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // 2. Khớp số tiền có ký hiệu/đơn vị tiền tệ độc lập
    private static final Pattern STANDALONE_CURRENCY_PATTERN = Pattern.compile(
            "(?i)(" + CURR_PREFIX_REGEX + NUM_PART_REGEX + "|" + NUM_PART_REGEX + CURR_SUFFIX_REGEX + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // 3. Khớp số thuần túy (cho dòng số tiền nằm riêng biệt bên dưới dòng nhãn)
    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile(
            "^\\s*(" + NUM_PART_REGEX + ")\\s*$"
    );

    // 4. Kiểm tra dòng có chứa từ khóa số dư hay không
    private static final Pattern KEYWORD_CHECK_PATTERN = Pattern.compile(
            "(?i)\\b" + BAL_KEYWORDS_REGEX + "\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // 5. Các từ khóa chỉ thống kê/thông tin khác (không phải số dư ví) để loại trừ
    private static final Pattern REJECT_LINE_PATTERN = Pattern.compile(
            "(?i)(?:[0-9]{1,2}:[0-9]{2}|[0-9]+/[0-9]+|[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4}|ping|fps|cấp|level|online|top|tài\\s*khoản|tai\\s*khoan|người\\s*chơi|nguoi\\s*choi|tọa\\s*độ|toa\\s*do|rank|thứ\\s*hạng|thu\\s*hang|thời\\s*gian|thoi\\s*gian|ngày|ngay|giờ|gio|balo|kho|nhiệm\\s*vụ|nhiem\\s*vu|máu|hp|thức\\s*ăn|food|ip|mc\\.|shard|mảnh|kills?|deaths?|keys?|played|team)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // 6. Nhận diện tin nhắn biến động nhỏ / giao dịch / delta / bán quặng để không ghi đè số dư ví
    private static final Pattern TRANSACTION_DELTA_PATTERN = Pattern.compile(
            "(?i)(?:[+\\-]\\s*[$₫¥€£0-9]|nhận\\s*được|nhan\\s*duoc|đã\\s*cộng|da\\s*cong|cộng\\s*vào|cong\\s*vao|cộng\\s*thêm|cong\\s*them|đã\\s*trừ|da\\s*tru|trừ\\s*bớt|tru\\s*bot|bán\\s*được|ban\\s*duoc|đã\\s*bán|da\\s*ban|bán\\s*tự\\s*động|ban\\s*tu\\s*dong|tự\\s*động\\s*bán|autosell|thưởng|thuong|chuyển\\s*cho|chuyen\\s*cho|gửi\\s*cho|gui\\s*cho|thanh\\s*toán|thanh\\s*toan|chi\\s*phí|chi\\s*phi|lãi\\s*suất|lai\\s*suat|giá\\s*bán|gia\\s*ban|mỗi\\s*khối|moi\\s*khoi|/\\s*block|/\\s*item|quét\\s*rác|hút\\s*vật\\s*phẩm)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static class ScoreboardLine {
        final String name;
        final String scoreText;
        final int scoreValue;

        ScoreboardLine(String name, String scoreText, int scoreValue) {
            this.name = name != null ? name : "";
            this.scoreText = scoreText != null ? scoreText : "";
            this.scoreValue = scoreValue;
        }
    }

    private static volatile String lastScoreboardBalance = null;
    private static volatile Objective lastActiveSidebarObjective = null;
    private static volatile String lastTabBalance = null;
    private static volatile Component lastTabHeader = null;
    private static volatile Component lastTabFooter = null;
    private static volatile String lastKnownBalance = "Đang cập nhật...";

    public static void updateActiveScoreboard(Objective objective) {
        if (objective != null) {
            lastActiveSidebarObjective = objective;
            scanScoreboardObjective(objective);
        }
    }

    public static void onWorldChanged() {
        lastScoreboardBalance = null;
        lastTabBalance = null;
        lastActiveSidebarObjective = null;
        lastTabHeader = null;
        lastTabFooter = null;
        lastKnownBalance = "Đang cập nhật...";
    }

    public static void updateTabListHeader(Component header) {
        lastTabHeader = header;
        if (header != null) {
            scanTabListText(header.getString());
        }
    }

    public static void updateTabListFooter(Component footer) {
        lastTabFooter = footer;
        if (footer != null) {
            scanTabListText(footer.getString());
        }
    }

    public static void scanTabListText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String clean = cleanText(line);
            if (clean.isEmpty()) continue;
            // Bỏ qua các dòng thống kê không liên quan (như Kills, Deaths, Keys, Played, Shards, Team...)
            if (REJECT_LINE_PATTERN.matcher(clean).find() && !KEYWORD_CHECK_PATTERN.matcher(clean).find()) {
                continue;
            }
            if (KEYWORD_CHECK_PATTERN.matcher(clean).find()) {
                String bal = parseBalanceFromText(line);
                if (bal != null && !bal.isEmpty() && !bal.matches("^[0-9]{1,2}\\s*\\$?$")) {
                    lastTabBalance = bal;
                    lastKnownBalance = bal;
                    return;
                }
            }
        }
    }

    public static String cleanText(String rawText) {
        if (rawText == null || rawText.isEmpty()) return "";
        return rawText.replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .replace("\u00A0", " ")
                .replace("\u202F", " ")
                .trim();
    }

    private static String normalizeCurrency(String val, String fullText) {
        if (val == null) return null;
        val = val.trim();
        String lowVal = val.toLowerCase(Locale.ROOT);
        String lowFull = fullText.toLowerCase(Locale.ROOT);

        boolean hasCurrency = val.contains("$") || val.contains("₫") || val.contains("¥") || val.contains("€") || val.contains("£")
                || lowVal.contains("xu") || lowVal.contains("đ") || lowVal.contains("coin") || lowVal.contains("vnđ") || lowVal.contains("vnd")
                || lowVal.contains("điểm") || lowVal.contains("point") || lowVal.contains("buck");

        if (!hasCurrency) {
            if (lowFull.contains("xu")) {
                return val + " Xu";
            } else if (lowFull.contains("coin")) {
                return val + " Coins";
            } else if (lowFull.contains("điểm") || lowFull.contains("point")) {
                return val + " Điểm";
            } else if (lowFull.contains("$") || lowFull.contains("tiền") || lowFull.contains("balance") || lowFull.contains("money")
                    || lowFull.contains("ví") || lowFull.contains("ngân hàng") || lowFull.contains("tài sản") || lowFull.contains("cash")) {
                return val + " $";
            }
        }
        return val;
    }

    public static void updateBalanceIfDetected(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String clean = cleanText(text);
        if (clean.isEmpty()) return;

        // Bỏ qua tin nhắn delta/giao dịch bán tự động (+10 $, bán 1 quặng: 10 $...) để tránh ghi đè số dư ví
        if (TRANSACTION_DELTA_PATTERN.matcher(clean).find()) {
            return;
        }

        String parsed = parseBalanceFromText(clean);
        if (parsed != null && !parsed.isEmpty()) {
            // Không nhận các số quá nhỏ mà không có đơn vị rõ ràng nếu trông giống row score (<= 15)
            if (!parsed.matches("^[0-9]{1,2}\\s*\\$?$")) {
                lastKnownBalance = parsed;
            }
        }
    }

    public static String parseBalanceFromText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return null;
        String clean = cleanText(rawText);
        if (clean.isEmpty()) return null;

        // Nếu dòng chứa các thông tin loại trừ (như ping, level, timestamp, tỉ lệ) mà không có từ khóa ví/tiền
        if (REJECT_LINE_PATTERN.matcher(clean).find() && !KEYWORD_CHECK_PATTERN.matcher(clean).find()) {
            return null;
        }

        // 1. Khớp từ khóa ví + số tiền (ví dụ: "$ MONEY 137.92M", "Ví: 10,000 $", "Balance: $1,200")
        Matcher km = BALANCE_WITH_KEYWORD_PATTERN.matcher(clean);
        if (km.find()) {
            String val = km.group(1).trim();
            if (val.matches(".*[0-9].*")) {
                int endIdx = km.end();
                String tail = endIdx < clean.length() ? clean.substring(endIdx).trim() : "";
                if (!tail.startsWith(":") && !tail.startsWith("/")) {
                    return normalizeCurrency(val, clean);
                }
            }
        }

        // 2. Khớp số tiền có ký hiệu tiền tệ độc lập
        Matcher cm = STANDALONE_CURRENCY_PATTERN.matcher(clean);
        if (cm.find()) {
            String val = cm.group(1).trim();
            if (val.matches(".*[0-9].*")) {
                int endIdx = cm.end();
                String tail = endIdx < clean.length() ? clean.substring(endIdx).trim() : "";
                if (!tail.startsWith(":") && !tail.startsWith("/")) {
                    return val;
                }
            }
        }
        return null;
    }

    /**
     * Tìm bảng điểm (Scoreboard Objective) đang hiển thị ở Sidebar.
     * Hỗ trợ đầy đủ: Objective vừa render, Team Color Slot của người chơi, DisplaySlot.SIDEBAR và quét mọi slot.
     */
    public static Objective findActiveSidebarObjective(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) return null;

        // 1. Objective vừa được render gần nhất qua MixinGui.displayScoreboardSidebar
        if (lastActiveSidebarObjective != null) {
            if (scoreboard.getObjective(lastActiveSidebarObjective.getName()) != null) {
                return lastActiveSidebarObjective;
            }
        }

        // 2. Logic chuẩn của Minecraft vanilla renderScoreboardSidebar (Hỗ trợ Team Color slot)
        if (mc.player != null) {
            PlayerTeam playerTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
            if (playerTeam != null) {
                ChatFormatting color = playerTeam.getColor();
                DisplaySlot teamSlot = DisplaySlot.teamColorToSlot(color);
                if (teamSlot != null) {
                    Objective teamObj = scoreboard.getDisplayObjective(teamSlot);
                    if (teamObj != null) return teamObj;
                }
            }
        }

        // 3. Slot DisplaySlot.SIDEBAR tiêu chuẩn
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) return sidebar;

        // 4. Quét qua toàn bộ DisplaySlot values
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective obj = scoreboard.getDisplayObjective(slot);
            if (obj != null) return obj;
        }

        // 5. Quét tất cả objectives hiện có trong scoreboard
        for (Objective obj : scoreboard.getObjectives()) {
            if (obj != null) return obj;
        }

        return null;
    }

    /**
     * Quét trực tiếp nội dung Objective của Scoreboard Sidebar để trích xuất số dư ví.
     * Hỗ trợ 100% các server Paper/Spigot dùng TAB plugin, FastBoard, FeatherBoard (Minecraft 1.8 -> 1.21).
     */
    public static void scanScoreboardObjective(Objective objective) {
        if (objective == null) return;
        try {
            Scoreboard scoreboard = objective.getScoreboard();
            if (scoreboard == null) return;

            NumberFormat defaultFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
            Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
            if (entries == null || entries.isEmpty()) return;

            List<ScoreboardLine> lines = new ArrayList<>();

            for (PlayerScoreEntry entry : entries) {
                if (entry.isHidden()) continue;

                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                Component nameComp = PlayerTeam.formatNameForTeam(team, entry.ownerName());
                String name = nameComp != null ? nameComp.getString() : "";

                Component scoreComp = entry.formatValue(defaultFormat);
                String scoreText = scoreComp != null ? scoreComp.getString() : "";
                int scoreVal = entry.value();

                lines.add(new ScoreboardLine(name, scoreText, scoreVal));

                // 1. Quét ngay khi ghép cả 2 cột trên cùng 1 dòng (cột name bên trái + cột score bên phải):
                // Server KingMC và đa số server SMP dùng TAB plugin hiển thị "$ MONEY" ở name và "137.92M" ở score
                String rowCombined = (name + " " + scoreText).trim();
                String cleanRow = cleanText(rowCombined);
                if (KEYWORD_CHECK_PATTERN.matcher(cleanRow).find()) {
                    String bal = parseBalanceFromText(cleanRow);
                    if (bal != null && !bal.isEmpty() && !bal.matches("^[0-9]{1,2}\\s*\\$?$")) {
                        lastScoreboardBalance = bal;
                        lastKnownBalance = bal;
                        return;
                    }
                }

                // 2. Nếu name có từ khóa và scoreText là số tiền (FixedFormat / custom NumberFormat)
                String cleanName = cleanText(name);
                String cleanScore = cleanText(scoreText);
                if (KEYWORD_CHECK_PATTERN.matcher(cleanName).find() && !cleanScore.isEmpty()) {
                    boolean isRowNumber = cleanScore.equals(String.valueOf(scoreVal)) && scoreVal >= 0 && scoreVal <= 15;
                    if (!isRowNumber) {
                        Matcher pm = PURE_NUMBER_PATTERN.matcher(cleanScore);
                        if (pm.find()) {
                            String norm = normalizeCurrency(pm.group(1).trim(), cleanName);
                            if (norm != null && !norm.matches("^[0-9]{1,2}\\s*\\$?$")) {
                                lastScoreboardBalance = norm;
                                lastKnownBalance = norm;
                                return;
                            }
                        }
                    }
                }

                // 3. Nếu toàn bộ nằm trong cột name
                if (KEYWORD_CHECK_PATTERN.matcher(cleanName).find()) {
                    String balName = parseBalanceFromText(name);
                    if (balName != null && !balName.isEmpty() && !balName.matches("^[0-9]{1,2}\\s*\\$?$")) {
                        lastScoreboardBalance = balName;
                        lastKnownBalance = balName;
                        return;
                    }
                }
            }

            // 4. Nếu số tiền nằm ở dòng tiếp theo (Dòng i là nhãn, dòng i+1 là số tiền)
            for (int i = 0; i < lines.size() - 1; i++) {
                ScoreboardLine cur = lines.get(i);
                ScoreboardLine next = lines.get(i + 1);

                String curClean = cleanText(cur.name + " " + cur.scoreText);
                String nextClean = cleanText(next.name + " " + next.scoreText);

                if (KEYWORD_CHECK_PATTERN.matcher(curClean).find() && !REJECT_LINE_PATTERN.matcher(nextClean).find()) {
                    String combined = curClean + " " + nextClean;
                    String bal = parseBalanceFromText(combined);
                    if (bal != null && !bal.isEmpty() && !bal.matches("^[0-9]{1,2}\\s*\\$?$")) {
                        lastScoreboardBalance = bal;
                        lastKnownBalance = bal;
                        return;
                    }

                    Matcher pm = PURE_NUMBER_PATTERN.matcher(nextClean);
                    if (pm.find()) {
                        String norm = normalizeCurrency(pm.group(1).trim(), curClean);
                        if (norm != null && !norm.matches("^[0-9]{1,2}\\s*\\$?$")) {
                            lastScoreboardBalance = norm;
                            lastKnownBalance = norm;
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String getPlayerBalance() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                // ƯU TIÊN 1: SCOREBOARD SIDEBAR (Bảng thông tin trực tiếp trên màn hình game)
                Objective sidebarObj = findActiveSidebarObjective(mc);
                if (sidebarObj != null) {
                    scanScoreboardObjective(sidebarObj);
                }
                if (lastScoreboardBalance != null && !lastScoreboardBalance.isEmpty() && !lastScoreboardBalance.matches("^[0-9]{1,2}\\s*\\$?$")) {
                    return lastScoreboardBalance;
                }

                // ƯU TIÊN 2: TAB LIST (Header / Footer / Player entries)
                if (lastTabHeader != null) {
                    scanTabListText(lastTabHeader.getString());
                }
                if (lastTabFooter != null) {
                    scanTabListText(lastTabFooter.getString());
                }
                if (lastTabBalance != null && !lastTabBalance.isEmpty() && !lastTabBalance.matches("^[0-9]{1,2}\\s*\\$?$")) {
                    return lastTabBalance;
                }

                // Quét danh sách người chơi trong Tab
                if (mc.getConnection() != null) {
                    for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                        Component disp = info.getTabListDisplayName();
                        if (disp != null) {
                            String txt = disp.getString();
                            String clean = cleanText(txt);
                            if (KEYWORD_CHECK_PATTERN.matcher(clean).find()) {
                                String bal = parseBalanceFromText(txt);
                                if (bal != null && !bal.isEmpty() && !bal.matches("^[0-9]{1,2}\\s*\\$?$")) {
                                    lastTabBalance = bal;
                                    lastKnownBalance = bal;
                                    return bal;
                                }
                            }
                        }
                    }
                }

                // ƯU TIÊN 3: Quét Player Teams dự phòng (khi server dùng Team prefix/suffix)
                Scoreboard scoreboard = mc.level.getScoreboard();
                if (scoreboard != null) {
                    for (PlayerTeam team : scoreboard.getPlayerTeams()) {
                        String prefix = team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
                        String suffix = team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
                        String full = (prefix + " " + suffix).trim();
                        String cleanFull = cleanText(full);
                        if (KEYWORD_CHECK_PATTERN.matcher(cleanFull).find()) {
                            String bal = parseBalanceFromText(full);
                            if (bal != null && !bal.isEmpty() && !bal.matches("^[0-9]{1,2}\\s*\\$?$")) {
                                lastScoreboardBalance = bal;
                                lastKnownBalance = bal;
                                return bal;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (lastScoreboardBalance != null && !lastScoreboardBalance.isEmpty() && !lastScoreboardBalance.matches("^[0-9]{1,2}\\s*\\$?$")) {
            return lastScoreboardBalance;
        }
        if (lastTabBalance != null && !lastTabBalance.isEmpty() && !lastTabBalance.matches("^[0-9]{1,2}\\s*\\$?$")) {
            return lastTabBalance;
        }
        if (lastKnownBalance != null && !lastKnownBalance.equals("Đang cập nhật...") && !lastKnownBalance.matches("^[0-9]{1,2}\\s*\\$?$")) {
            return lastKnownBalance;
        }

        if (getServerIp().contains("Singleplayer")) {
            return "Chơi Đơn (N/A)";
        }

        return "Đang cập nhật...";
    }

    public static String getRawPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            LocalPlayer player = mc.player;
            if (player != null && player.getGameProfile() != null) {
                String name = player.getGameProfile().name();
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
            if (mc.getUser() != null) {
                String name = mc.getUser().getName();
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        }
        String streamerName = StreamerUtil.getLocalPlayerName();
        if (streamerName != null && !streamerName.trim().isEmpty()) {
            return streamerName.trim();
        }
        return "Unknown";
    }

    public static String getServerIp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "Chơi Đơn (Singleplayer)";
        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.trim().isEmpty()) {
            return data.ip.trim();
        }
        if (mc.hasSingleplayerServer()) {
            return "Chơi Đơn (Singleplayer)";
        }
        return "Local / Unknown";
    }

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
            String rawPlayerName = getRawPlayerName();
            String spoilerPlayerName = "||" + rawPlayerName + "||";
            String playerBalance = getPlayerBalance();
            String serverIp = getServerIp();

            int intervalSec = Baritone.settings().discordWebhookInterval.value;
            int intervalMin = Math.max(1, intervalSec / 60);
            String intervalStr = intervalSec >= 60 ? (intervalSec / 60) + " phút" : intervalSec + "s";

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
            String state = "Đang nghỉ ngơi / Chờ lệnh";
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                state = "⛏️ AutoMine";
            } else if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                state = "🏃 Pathing";
            }

            // 4. Tạo Embed Fields (Bố cục Dashboard 3 cột hiện đại)
            JsonArray fields = new JsonArray();

            // Hàng 1: Tài khoản, Số dư, Thể trạng (Inline 3 cột)
            JsonObject playerField = new JsonObject();
            playerField.addProperty("name", "👤 TÀI KHOẢN");
            playerField.addProperty("value", spoilerPlayerName);
            playerField.addProperty("inline", true);
            fields.add(playerField);

            JsonObject balField = new JsonObject();
            balField.addProperty("name", "💰 SỐ DƯ / XU");
            balField.addProperty("value", "**" + playerBalance + "**");
            balField.addProperty("inline", true);
            fields.add(balField);

            JsonObject hpField = new JsonObject();
            hpField.addProperty("name", "❤️ THỂ TRẠNG");
            if (player != null) {
                hpField.addProperty("value", ((int) player.getHealth()) + "/" + ((int) player.getMaxHealth()) + " HP • " + player.getFoodData().getFoodLevel() + "/20 Đói");
            } else {
                hpField.addProperty("value", "N/A");
            }
            hpField.addProperty("inline", true);
            fields.add(hpField);

            // Hàng 2: Tọa độ, Túi đồ trống, Trạng thái (Inline 3 cột)
            JsonObject posField = new JsonObject();
            posField.addProperty("name", "📍 TỌA ĐỘ");
            if (player != null) {
                posField.addProperty("value", "`X: " + player.getBlockX() + ", Y: " + player.getBlockY() + ", Z: " + player.getBlockZ() + "`");
            } else {
                posField.addProperty("value", "N/A");
            }
            posField.addProperty("inline", true);
            fields.add(posField);

            JsonObject invField = new JsonObject();
            invField.addProperty("name", "🎒 TÚI TRỐNG");
            if (player != null) {
                Inventory inv = player.getInventory();
                int emptySlots = 0;
                for (int i = 0; i < 36; i++) {
                    if (inv.getItem(i).isEmpty()) emptySlots++;
                }
                invField.addProperty("value", emptySlots + "/36 ô");
            } else {
                invField.addProperty("value", "N/A");
            }
            invField.addProperty("inline", true);
            fields.add(invField);

            JsonObject stateField = new JsonObject();
            stateField.addProperty("name", "⚙️ TRẠNG THÁI");
            stateField.addProperty("value", state);
            stateField.addProperty("inline", true);
            fields.add(stateField);

            // Hàng 3: Quặng đào thêm (Full width)
            JsonObject deltaField = new JsonObject();
            deltaField.addProperty("name", "✨ SỐ QUẶNG ĐÀO ĐƯỢC THÊM (" + intervalMin + " PHÚT QUA)");
            deltaField.addProperty("value", deltaSb.toString());
            deltaField.addProperty("inline", false);
            fields.add(deltaField);

            // Hàng 4: Tổng kết quả tích lũy (Full width)
            JsonObject totalField = new JsonObject();
            totalField.addProperty("name", "📦 TỔNG KẾT QUẢ FARM TÍCH LŨY");
            totalField.addProperty("value", totalSb.toString());
            totalField.addProperty("inline", false);
            fields.add(totalField);

            String title = isTest ? "🧪 [GỬI THỬ] BÁO CÁO KẾT QUẢ FARM TỰ ĐỘNG" : "⛏️ [BÁO CÁO FARM] TIẾN ĐỘ ĐÀO KHOÁNG (" + intervalMin + " PHÚT)";
            StringBuilder descSb = new StringBuilder();
            descSb.append("> 👤 **Tài khoản:** ").append(spoilerPlayerName).append("\n");
            descSb.append("> 💰 **Số dư ví:** **").append(playerBalance).append("**\n");
            descSb.append("> 🌐 **Máy chủ:** `").append(serverIp).append("` • ⏱️ **Chu kỳ:** `").append(intervalStr).append("`\n");
            if (screenshotBytes != null) {
                descSb.append("📸 *Ảnh chụp màn hình thực tế đính kèm bên dưới.*");
            } else {
                descSb.append("ℹ️ *Chế độ báo cáo văn bản.*");
            }
            String desc = descSb.toString();

            String content = "🔔 **[BÁO CÁO FARM]** Người chơi: " + spoilerPlayerName + " • Số dư: **" + playerBalance + "**";
            JsonObject payload = buildDiscordWebhookEmbedPayload(content, "🤖 BARITONE NEXTGEN • AI AUTOMINE", title, desc, 0x10B981, fields, screenshotBytes != null);

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
            String rawPlayerName = getRawPlayerName();
            String spoilerPlayerName = "||" + rawPlayerName + "||";
            String playerBalance = getPlayerBalance();
            String serverIp = getServerIp();

            JsonArray fields = new JsonArray();

            JsonObject playerField = new JsonObject();
            playerField.addProperty("name", "👤 Tài khoản");
            playerField.addProperty("value", spoilerPlayerName);
            playerField.addProperty("inline", true);
            fields.add(playerField);

            JsonObject balField = new JsonObject();
            balField.addProperty("name", "💰 Số dư");
            balField.addProperty("value", "**" + playerBalance + "**");
            balField.addProperty("inline", true);
            fields.add(balField);

            if (player != null) {
                JsonObject hpField = new JsonObject();
                hpField.addProperty("name", "❤️ Sinh lực");
                hpField.addProperty("value", (int) player.getHealth() + " / " + (int) player.getMaxHealth());
                hpField.addProperty("inline", true);
                fields.add(hpField);

                JsonObject posField = new JsonObject();
                posField.addProperty("name", "📍 Tọa độ");
                posField.addProperty("value", "`X: " + player.getBlockX() + ", Y: " + player.getBlockY() + ", Z: " + player.getBlockZ() + "`");
                posField.addProperty("inline", true);
                fields.add(posField);
            }

            JsonObject srvField = new JsonObject();
            srvField.addProperty("name", "🌐 Máy chủ");
            srvField.addProperty("value", "`" + serverIp + "`");
            srvField.addProperty("inline", true);
            fields.add(srvField);

            String alertContent = "🚨 **" + title + "** • Người chơi: " + spoilerPlayerName + " • Số dư: **" + playerBalance + "**";
            JsonObject payload = buildDiscordWebhookEmbedPayload(alertContent, "🚨 BARITONE HỆ THỐNG CẢNH BÁO", title, message, colorHex, fields, false);
            sendAsyncToWebhook(webhookUrl, payload.toString());
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi gửi alert: " + e.getMessage());
        }
    }

    public void sendAlertWithAttachment(String title, String message, int colorHex, byte[] imageBytes) {
        if (!Baritone.settings().discordWebhookEnabled.value) {
            return;
        }
        String webhookUrl = Baritone.settings().discordWebhookUrl.value;
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            LocalPlayer player = Minecraft.getInstance().player;
            String rawPlayerName = getRawPlayerName();
            String spoilerPlayerName = "||" + rawPlayerName + "||";
            String playerBalance = getPlayerBalance();
            String serverIp = getServerIp();

            JsonArray fields = new JsonArray();

            JsonObject playerField = new JsonObject();
            playerField.addProperty("name", "👤 Tài khoản");
            playerField.addProperty("value", spoilerPlayerName);
            playerField.addProperty("inline", true);
            fields.add(playerField);

            JsonObject balField = new JsonObject();
            balField.addProperty("name", "💰 Số dư");
            balField.addProperty("value", "**" + playerBalance + "**");
            balField.addProperty("inline", true);
            fields.add(balField);

            if (player != null) {
                JsonObject hpField = new JsonObject();
                hpField.addProperty("name", "❤️ Sinh lực");
                hpField.addProperty("value", (int) player.getHealth() + " / " + (int) player.getMaxHealth());
                hpField.addProperty("inline", true);
                fields.add(hpField);

                JsonObject posField = new JsonObject();
                posField.addProperty("name", "📍 Tọa độ");
                posField.addProperty("value", "`X: " + player.getBlockX() + ", Y: " + player.getBlockY() + ", Z: " + player.getBlockZ() + "`");
                posField.addProperty("inline", true);
                fields.add(posField);
            }

            JsonObject srvField = new JsonObject();
            srvField.addProperty("name", "🌐 Máy chủ");
            srvField.addProperty("value", "`" + serverIp + "`");
            srvField.addProperty("inline", true);
            fields.add(srvField);

            String alertContent = "🚨 **" + title + "** • Người chơi: " + spoilerPlayerName + " • Số dư: **" + playerBalance + "**";
            JsonObject payload = buildDiscordWebhookEmbedPayload(alertContent, "🚨 BARITONE HỆ THỐNG CẢNH BÁO", title, message, colorHex, fields, imageBytes != null && imageBytes.length > 0);
            if (imageBytes != null && imageBytes.length > 0) {
                sendMultipartAsyncToWebhook(webhookUrl, payload.toString(), imageBytes);
            } else {
                sendAsyncToWebhook(webhookUrl, payload.toString());
            }
        } catch (Exception e) {
            logDebug("[DiscordManager] Lỗi gửi alert kèm ảnh: " + e.getMessage());
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

    private JsonObject buildDiscordWebhookEmbedPayload(String content, String authorName, String title, String description, int color, JsonArray fields, boolean hasImage) {
        JsonObject root = new JsonObject();
        root.addProperty("username", "Baritone AI NextGen (Tr0ngX)");
        root.addProperty("avatar_url", "https://raw.githubusercontent.com/cabaletta/baritone/master/src/main/resources/assets/baritone/icon.png");

        if (content != null && !content.trim().isEmpty()) {
            root.addProperty("content", content.trim());
        }

        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();

        // Author
        JsonObject author = new JsonObject();
        author.addProperty("name", authorName != null && !authorName.isEmpty() ? authorName : "🤖 BARITONE NEXTGEN • AI AUTOMINE");
        author.addProperty("icon_url", "https://raw.githubusercontent.com/cabaletta/baritone/master/src/main/resources/assets/baritone/icon.png");
        embed.add("author", author);

        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        // Thumbnail (Baritone Icon)
        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", "https://raw.githubusercontent.com/cabaletta/baritone/master/src/main/resources/assets/baritone/icon.png");
        embed.add("thumbnail", thumb);

        // Footer with server IP
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Baritone NextGen 1.21.11 • Dev by Tr0ngX • Server: " + getServerIp());
        footer.addProperty("icon_url", "https://raw.githubusercontent.com/cabaletta/baritone/master/src/main/resources/assets/baritone/icon.png");
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

    private JsonObject buildDiscordWebhookEmbedPayload(String title, String description, int color, JsonArray fields, boolean hasImage) {
        return buildDiscordWebhookEmbedPayload(null, null, title, description, color, fields, hasImage);
    }

    private JsonObject buildDiscordWebhookEmbedPayload(String title, String description, int color, JsonArray fields) {
        return buildDiscordWebhookEmbedPayload(null, null, title, description, color, fields, false);
    }

    private String buildTelemetryDescription(LocalPlayer player) {
        String server = getServerIp();
        String state = "Nghỉ ngơi / Chờ lệnh";
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
            state = "⛏️ Đang đào quặng (MineProcess)";
        } else if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            state = "🏃 Đang di chuyển (Pathing)";
        }

        String rawName = getRawPlayerName();
        String spoilerName = "||" + rawName + "||";
        String balance = getPlayerBalance();

        return "> 👤 **Tài khoản**: " + spoilerName + "\n" +
               "> 💰 **Số dư ví**: **" + balance + "**\n" +
               "> 🌐 **Máy chủ**: `" + server + "`\n" +
               "> ⚙️ **Hoạt động**: " + state;
    }

    private JsonArray buildTelemetryFields(LocalPlayer player) {
        JsonArray fields = new JsonArray();

        String rawName = getRawPlayerName();
        String spoilerName = "||" + rawName + "||";
        String balance = getPlayerBalance();

        // Tài khoản
        JsonObject playerField = new JsonObject();
        playerField.addProperty("name", "👤 Tài khoản");
        playerField.addProperty("value", spoilerName);
        playerField.addProperty("inline", true);
        fields.add(playerField);

        // Số dư
        JsonObject balField = new JsonObject();
        balField.addProperty("name", "💰 Số dư");
        balField.addProperty("value", "**" + balance + "**");
        balField.addProperty("inline", true);
        fields.add(balField);

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
