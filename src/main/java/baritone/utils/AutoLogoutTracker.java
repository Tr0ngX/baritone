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
import baritone.api.utils.BaritoneFileLogger;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Trình theo dõi và xử lý tự động ngắt kết nối an toàn (Auto-Logout Tracker).
 * Hỗ trợ quét phát hiện người chơi khác kể cả khi dùng thuốc tàng hình (Invis),
 * lưu toạ độ X Y Z lúc thoát và hiển thị toạ độ nổi bật lên màn hình.
 */
public final class AutoLogoutTracker {

    private static volatile boolean hasLoggedOut = false;
    private static volatile double lastX = 0;
    private static volatile double lastY = 0;
    private static volatile double lastZ = 0;
    private static volatile String lastDimension = "minecraft:overworld";
    private static volatile String lastReason = "";
    private static volatile long lastTimestamp = 0;
    private static volatile boolean pendingWorldJoinAlert = false;

    private AutoLogoutTracker() {}

    public static boolean hasLoggedOut() {
        return hasLoggedOut;
    }

    public static double getLastX() {
        return lastX;
    }

    public static double getLastY() {
        return lastY;
    }

    public static double getLastZ() {
        return lastZ;
    }

    public static String getLastDimension() {
        return lastDimension;
    }

    public static String getLastReason() {
        return lastReason;
    }

    public static long getLastTimestamp() {
        return lastTimestamp;
    }

    public static class DetectedPlayerInfo {
        public final Player player;
        public final String name;
        public final double distance;
        public final boolean isInvis;
        public final boolean isSpectator;

        public DetectedPlayerInfo(Player player, String name, double distance, boolean isInvis, boolean isSpectator) {
            this.player = player;
            this.name = name;
            this.distance = distance;
            this.isInvis = isInvis;
            this.isSpectator = isSpectator;
        }

        public String getFormattedDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (isInvis) {
                sb.append(" [TÀNG HÌNH / INVIS]");
            }
            if (isSpectator) {
                sb.append(" [SPECTATOR]");
            }
            sb.append(String.format(Locale.ROOT, " (Cách %.1fm)", distance));
            return sb.toString();
        }
    }

    /**
     * Quét toàn bộ người chơi trong phạm vi tải của Client, bao gồm:
     * - Người chơi bình thường
     * - Người chơi dùng thuốc tàng hình / hiệu ứng Invisibility (kể cả không mặc giáp)
     * - Người chơi Spectator / Crouching
     */
    public static DetectedPlayerInfo scanForNearbyPlayer(IPlayerContext ctx) {
        if (ctx == null || ctx.player() == null || ctx.world() == null) {
            return null;
        }
        if (!Baritone.settings().autoLogoutOnPlayer.value) {
            return null;
        }

        LocalPlayer self = ctx.player();
        UUID selfUUID = self.getUUID();
        ClientLevel world = (ClientLevel) ctx.world();

        double maxRange = Baritone.settings().autoLogoutPlayerRange.value;

        // Parse danh sách whitelist bạn bè / đồng đội bỏ qua
        String whitelistRaw = Baritone.settings().autoLogoutPlayerWhitelist.value;
        Set<String> whitelist = new HashSet<>();
        if (whitelistRaw != null && !whitelistRaw.trim().isEmpty()) {
            for (String s : whitelistRaw.split(",")) {
                String trimmed = s.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    whitelist.add(trimmed);
                }
            }
        }

        Player closest = null;
        double closestDist = Double.MAX_VALUE;
        boolean closestInvis = false;
        boolean closestSpectator = false;
        String closestName = "";

        Set<UUID> checkedUUIDs = new HashSet<>();

        // 1. Quét danh sách world.players() (Toàn bộ Player đang được client quản lý)
        List<Player> candidates = new ArrayList<>(world.players());

        // 2. Quét thêm entitiesForRendering() đề phòng trường hợp entity được inject riêng
        try {
            for (Entity e : world.entitiesForRendering()) {
                if (e instanceof Player p && !candidates.contains(p)) {
                    candidates.add(p);
                }
            }
        } catch (Throwable ignored) {}

        // 3. Quét trực tiếp qua spatial query trong bán kính cực đại để không sót bất kỳ player nào
        try {
            double searchRadius = maxRange > 0 ? Math.min(maxRange, 512.0D) : 256.0D;
            List<Player> spatialPlayers = world.getEntitiesOfClass(Player.class, self.getBoundingBox().inflate(searchRadius));
            for (Player p : spatialPlayers) {
                if (p != null && !candidates.contains(p)) {
                    candidates.add(p);
                }
            }
        } catch (Throwable ignored) {}

        for (Player p : candidates) {
            if (p == null || p == self) {
                continue;
            }
            UUID uuid = p.getUUID();
            if (uuid != null) {
                if (uuid.equals(selfUUID)) {
                    continue;
                }
                if (checkedUUIDs.contains(uuid)) {
                    continue;
                }
                checkedUUIDs.add(uuid);
            }
            if (!p.isAlive() || p.isRemoved()) {
                continue;
            }

            String name = p.getScoreboardName();
            if (name == null || name.isEmpty()) {
                name = p.getName().getString();
            }
            if (whitelist.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                if (p.getGameProfile() != null && p.getGameProfile().name() != null) {
                    if (whitelist.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                }
            } catch (Throwable ignored) {}

            double dist = self.distanceTo(p);
            // Nếu maxRange <= 0: Quét vô cực (mọi player đều kích hoạt)!
            if (maxRange > 0 && dist > maxRange) {
                continue;
            }

            // Kiểm tra tàng hình triệt để:
            // 1. isInvisible() - cờ byte flag 5 từ synched entity data
            // 2. isInvisibleTo(self)
            // 3. hasEffect(MobEffects.INVISIBILITY)
            boolean isInvis = p.isInvisible() || p.isInvisibleTo(self) || p.hasEffect(MobEffects.INVISIBILITY);
            boolean isSpectator = p.isSpectator();

            if (dist < closestDist) {
                closestDist = dist;
                closest = p;
                closestInvis = isInvis;
                closestSpectator = isSpectator;
                closestName = name;
            }
        }

        if (closest != null) {
            return new DetectedPlayerInfo(closest, closestName, closestDist, closestInvis, closestSpectator);
        }
        return null;
    }

    /**
     * Thực hiện quy trình ngắt kết nối an toàn (Auto-Logout khẩn cấp):
     * 1. Ghi nhận toạ độ X Y Z và Thế giới (Dimension)
     * 2. Tự động sao chép toạ độ vào Clipboard của hệ thống
     * 3. Hủy bỏ mọi tác vụ Baritone và giải phóng phím bấm
     * 4. Tạo kick message Component hiển thị toạ độ cực kỳ rõ nét lên màn hình
     * 5. Ghi log vĩnh viễn vào file baritone.log
     */
    public static void performAutoLogout(IPlayerContext ctx, String reason) {
        if (ctx == null || ctx.player() == null) return;

        lastX = ctx.player().getX();
        lastY = ctx.player().getY();
        lastZ = ctx.player().getZ();
        lastTimestamp = System.currentTimeMillis();
        lastReason = reason;
        hasLoggedOut = true;
        pendingWorldJoinAlert = true;

        if (ctx.world() != null && ctx.world().dimension() != null) {
            try {
                lastDimension = ctx.world().dimension().identifier().toString();
            } catch (Throwable ignored) {
                lastDimension = "minecraft:overworld";
            }
        } else {
            lastDimension = "minecraft:overworld";
        }

        // Tự động chép toạ độ vào Clipboard ngay lập tức để người chơi tiện dùng
        try {
            String coordsSimple = String.format(Locale.ROOT, "%.1f %.1f %.1f", lastX, lastY, lastZ);
            Minecraft.getInstance().keyboardHandler.setClipboard(coordsSimple);
        } catch (Throwable ignored) {}

        // Tự động tắt cả 2 tính năng bảo vệ để khi đăng nhập lại vào game không bị ngắt kết nối lặp lại
        Baritone.settings().autoLogoutOnDanger.value = false;
        Baritone.settings().autoLogoutOnPlayer.value = false;
        AutoMineScreen.optAutoLogout = false;
        AutoMineConfig.save();
        baritone.api.utils.SettingsUtil.save(Baritone.settings());

        // Dừng mọi hành vi điều khiển của Baritone
        try {
            baritone.api.IBaritone primary = baritone.api.BaritoneAPI.getProvider() != null ? baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone() : null;
            if (primary != null) {
                primary.getPathingBehavior().cancelEverything();
                primary.getMineProcess().cancel();
                primary.getInputOverrideHandler().clearAllKeys();
            }
        } catch (Throwable ignored) {}

        String alertLog = String.format(Locale.ROOT,
                "[AutoLogout] KHẨN CẤP: %s | Toạ độ lúc thoát: X=%.2f, Y=%.2f, Z=%.2f (%s)",
                reason, lastX, lastY, lastZ, lastDimension);
        Helper.HELPER.logDirect("§c" + alertLog);
        BaritoneFileLogger.warn(alertLog);

        // Tạo giao diện thông báo ngắt kết nối hiển thị toạ độ cực kỳ rõ nét lên màn hình (bố cục gọn gàng, không tràn/đè)
        Component kickReason = Component.literal(
                "§c§l[ BẢO VỆ SINH TỒN – AUTO LOGOUT ]\n\n" +
                "§e⚠ Cảnh báo: §f" + reason + "\n\n" +
                "§6📍 TOẠ ĐỘ VỊ TRÍ LÚC THOÁT:\n" +
                "§a§lX: " + String.format(Locale.ROOT, "%.2f", lastX) +
                "   §8|   §a§lY: " + String.format(Locale.ROOT, "%.2f", lastY) +
                "   §8|   §a§lZ: " + String.format(Locale.ROOT, "%.2f", lastZ) + "\n" +
                "§7Khu vực: §f[" + (int) Math.floor(lastX) + ", " + (int) Math.floor(lastY) + ", " + (int) Math.floor(lastZ) + "]  •  §8" + lastDimension.replace("minecraft:", "")
        );

        if (ctx.world() instanceof ClientLevel clientLevel) {
            clientLevel.disconnect(kickReason);
        } else if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().getConnection().disconnect(kickReason);
        }
    }

    /**
     * Vẽ Banner hiển thị toạ độ Auto-Logout ở phía trên cùng màn hình ngắt kết nối (DisconnectedScreen).
     */
    public static void renderDisconnectedScreen(DisconnectedScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!hasLoggedOut) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        int screenWidth = screen.width;
        int bannerW = Math.min(540, screenWidth - 24);
        int bannerH = 34;
        int bannerX = (screenWidth - bannerW) / 2;
        int bannerY = 6;

        boolean hovered = mouseX >= bannerX && mouseX <= bannerX + bannerW && mouseY >= bannerY && mouseY <= bannerY + bannerH;

        int bgColor = hovered ? 0xF01E293B : 0xE60F172A;
        int borderColor = hovered ? 0xFF38BDF8 : 0xFFEF4444;

        // Vẽ card nền và viền mảnh hiện đại
        guiGraphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, bgColor);
        guiGraphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + 1, borderColor);
        guiGraphics.fill(bannerX, bannerY + bannerH - 1, bannerX + bannerW, bannerY + bannerH, borderColor);
        guiGraphics.fill(bannerX, bannerY + 1, bannerX + 1, bannerY + bannerH, borderColor);
        guiGraphics.fill(bannerX + bannerW - 1, bannerY, bannerX + bannerW, bannerY + bannerH, borderColor);

        // Vạch accent màu đỏ cảnh báo ở mép trái
        guiGraphics.fill(bannerX + 1, bannerY + 1, bannerX + 4, bannerY + bannerH - 1, 0xFFEF4444);

        // Dòng 1: Tiêu đề + Lý do
        String shortReason = lastReason.length() > 50 ? (lastReason.substring(0, 47) + "...") : lastReason;
        String line1 = "§c§l🛡 AUTO-LOGOUT KHẨN CẤP §8| §e" + shortReason;
        guiGraphics.drawString(font, line1, bannerX + 10, bannerY + 5, 0xFFFFFFFF, true);

        // Dòng 2: Toạ độ X Y Z + phím tắt
        String dimClean = lastDimension.replace("minecraft:", "");
        String line2 = String.format(Locale.ROOT, "§7Vị trí: §fX: §a§l%.2f  §fY: §a§l%.2f  §fZ: §a§l%.2f §8(§7%s§8) §8• §e[Phím C: Chép XYZ]",
                lastX, lastY, lastZ, dimClean);
        guiGraphics.drawString(font, line2, bannerX + 10, bannerY + 19, 0xFFFFFFFF, true);
    }

    /**
     * Nhắc lại toạ độ khi người chơi đăng nhập lại vào thế giới.
     */
    public static void onWorldJoined() {
        if (pendingWorldJoinAlert && hasLoggedOut) {
            pendingWorldJoinAlert = false;
            hasLoggedOut = false;
            String msg = String.format(Locale.ROOT,
                    "§6[Baritone] §eToạ độ AutoLogout gần nhất: §fX: §a%.2f §fY: §a%.2f §fZ: §a%.2f §7(%s) §d(Đã lưu)",
                    lastX, lastY, lastZ, lastDimension);
            Helper.HELPER.logDirect(msg);
            Helper.HELPER.logDirect("§e[Baritone] §a✔ Đã tự động tắt Anti-Player & Auto-Logout để bạn an toàn vào lại thế giới. Bật lại trong ClickGUI khi cần!");
        }
    }
}
