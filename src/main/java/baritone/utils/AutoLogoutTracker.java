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

import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Trình theo dõi và xử lý tự động ngắt kết nối an toàn (Auto-Logout Tracker).
 * Hỗ trợ quét phát hiện người chơi khác kể cả khi dùng thuốc tàng hình (Invis),
 * chụp ảnh màn hình khẩn cấp lưu vào máy và hiển thị trực tiếp lên DisconnectedScreen.
 */
public final class AutoLogoutTracker {

    private static final Identifier SCREENSHOT_TEXTURE_ID = Identifier.fromNamespaceAndPath("baritone", "last_autologout_screenshot");
    private static volatile boolean hasLoggedOut = false;
    private static volatile double lastX = 0;
    private static volatile double lastY = 0;
    private static volatile double lastZ = 0;
    private static volatile String lastDimension = "minecraft:overworld";
    private static volatile String lastReason = "";
    private static volatile long lastTimestamp = 0;
    private static volatile boolean pendingWorldJoinAlert = false;
    private static volatile int joinGraceTicks = 0;

    // Trạng thái ảnh chụp màn hình khẩn cấp
    private static volatile boolean hasScreenshot = false;
    private static volatile File lastScreenshotFile = null;
    private static DynamicTexture lastScreenshotTexture = null;
    private static boolean isZoomed = false;

    // Toạ độ vùng bấm của thẻ ảnh chụp (Screenshot Card) trên DisconnectedScreen
    private static int cardX = 0;
    private static int cardY = 0;
    private static int cardW = 0;
    private static int cardH = 0;

    private AutoLogoutTracker() {}

    public static int getJoinGraceTicks() {
        return joinGraceTicks;
    }

    public static void decrementJoinGraceTicks() {
        if (joinGraceTicks > 0) {
            joinGraceTicks--;
        }
    }

    public static void setJoinGraceTicks(int ticks) {
        joinGraceTicks = ticks;
    }

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

    public static boolean hasScreenshot() {
        return hasScreenshot && lastScreenshotTexture != null;
    }

    public static File getLastScreenshotFile() {
        return lastScreenshotFile;
    }

    public static boolean isZoomed() {
        return isZoomed;
    }

    public static void setZoomed(boolean zoomed) {
        isZoomed = zoomed;
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

            // Bỏ qua thực thể clone / bot nhái tên của chính mình do plugin server / anti-cheat tạo ra
            String selfScoreboardName = self.getScoreboardName();
            String selfProfileName = self.getGameProfile() != null ? self.getGameProfile().name() : "";
            if (name.equalsIgnoreCase(selfScoreboardName) || name.equalsIgnoreCase(selfProfileName)) {
                continue;
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

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> performAutoLogout(ctx, reason));
            return;
        }

        // Chụp ảnh màn hình ngay lập tức TRƯỚC KHI disconnect thế giới
        captureLastScreenshot();

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
            mc.keyboardHandler.setClipboard(coordsSimple);
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
        } else if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(kickReason);
        }
    }

    /**
     * Chụp ảnh màn hình thế giới game ngay tại thời điểm ngắt kết nối khẩn cấp,
     * lưu ra ổ đĩa và tải vào DynamicTexture để hiển thị trên màn hình ngắt kết nối.
     */
    public static void captureLastScreenshot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getMainRenderTarget() == null) {
            return;
        }

        try {
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), 1, nativeImage -> {
                if (nativeImage == null) return;

                Util.ioPool().execute(() -> {
                    try {
                        File screenshotsDir = new File(mc.gameDirectory, "screenshots");
                        if (!screenshotsDir.exists()) {
                            screenshotsDir.mkdirs();
                        }
                        String timeStr = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
                        File targetFile = new File(screenshotsDir, "autologout_" + timeStr + ".png");
                        nativeImage.writeToFile(targetFile);
                        lastScreenshotFile = targetFile;
                        BaritoneFileLogger.info("Đã lưu ảnh chụp ngắt kết nối khẩn cấp: " + targetFile.getAbsolutePath());
                    } catch (Throwable t) {
                        BaritoneFileLogger.error("Lỗi lưu file ảnh chụp ra ổ đĩa: " + t);
                    }

                    mc.execute(() -> {
                        try {
                            if (lastScreenshotTexture != null) {
                                mc.getTextureManager().release(SCREENSHOT_TEXTURE_ID);
                                lastScreenshotTexture.close();
                                lastScreenshotTexture = null;
                            }
                            lastScreenshotTexture = new DynamicTexture(() -> "AutoLogoutScreenshot", nativeImage);
                            mc.getTextureManager().register(SCREENSHOT_TEXTURE_ID, lastScreenshotTexture);
                            hasScreenshot = true;
                        } catch (Throwable t) {
                            BaritoneFileLogger.error("Lỗi đăng ký texture ảnh chụp: " + t);
                        }
                    });
                });
            });
        } catch (Throwable t) {
            BaritoneFileLogger.error("Lỗi chụp ảnh màn hình takeScreenshot: " + t);
        }
    }

    /**
     * Vẽ ảnh chụp cuối cùng làm hình nền điện ảnh cho DisconnectedScreen.
     * Trả về true nếu đã vẽ ảnh nền (để huỷ nền đất mặc định của Minecraft).
     */
    public static boolean renderDisconnectedBackground(GuiGraphics guiGraphics, int width, int height) {
        if (!hasLoggedOut || !hasScreenshot || lastScreenshotTexture == null) {
            return false;
        }

        try {
            // Vẽ ảnh chụp toàn màn hình tỉ lệ co giãn phủ kín nền
            guiGraphics.blit(SCREENSHOT_TEXTURE_ID, 0, 0, width, height, 0.0f, 1.0f, 0.0f, 1.0f);

            // Phủ lớp màn tối kính mờ điện ảnh (Dark Cinematic Vignette) để chữ DisconnectedScreen nổi bật 100%
            guiGraphics.fillGradient(0, 0, width, height, 0xCC0A0F1D, 0xEE020617);

            // Viền bóng tối 2 mép trên dưới (Vignette)
            guiGraphics.fill(0, 0, width, 40, 0x80000000);
            guiGraphics.fill(0, height - 50, width, height, 0x80000000);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Vẽ Banner hiển thị toạ độ và Thẻ xem ảnh chụp màn hình (Double-Bezel Screenshot Card)
     * cùng chế độ Phóng to toàn màn hình (Fullscreen Zoom Modal).
     */
    public static void renderDisconnectedScreen(DisconnectedScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!hasLoggedOut) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        int screenWidth = screen.width;
        int screenHeight = screen.height;

        // ==========================================
        // 1. BANNER TRÊN CÙNG: CẢNH BÁO & TOẠ ĐỘ
        // ==========================================
        int bannerW = Math.min(560, screenWidth - 24);
        int bannerH = 34;
        int bannerX = (screenWidth - bannerW) / 2;
        int bannerY = 6;

        boolean bannerHovered = mouseX >= bannerX && mouseX <= bannerX + bannerW && mouseY >= bannerY && mouseY <= bannerY + bannerH;
        int bannerBg = bannerHovered ? 0xF01E293B : 0xE60F172A;
        int bannerBorder = bannerHovered ? 0xFF38BDF8 : 0xFFEF4444;

        guiGraphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, bannerBg);
        guiGraphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + 1, bannerBorder);
        guiGraphics.fill(bannerX, bannerY + bannerH - 1, bannerX + bannerW, bannerY + bannerH, bannerBorder);
        guiGraphics.fill(bannerX, bannerY + 1, bannerX + 1, bannerY + bannerH, bannerBorder);
        guiGraphics.fill(bannerX + bannerW - 1, bannerY, bannerX + bannerW, bannerY + bannerH, bannerBorder);

        // Vạch accent màu đỏ cảnh báo ở mép trái
        guiGraphics.fill(bannerX + 1, bannerY + 1, bannerX + 4, bannerY + bannerH - 1, 0xFFEF4444);

        String shortReason = lastReason.length() > 50 ? (lastReason.substring(0, 47) + "...") : lastReason;
        String line1 = "§c§l🛡 AUTO-LOGOUT KHẨN CẤP §8| §e" + shortReason;
        guiGraphics.drawString(font, line1, bannerX + 10, bannerY + 5, 0xFFFFFFFF, true);

        String dimClean = lastDimension.replace("minecraft:", "");
        String line2 = String.format(Locale.ROOT, "§7Vị trí: §fX: §a§l%.2f  §fY: §a§l%.2f  §fZ: §a§l%.2f §8(§7%s§8) §8• §e[C: Chép XYZ] §8• §b[T: /tp] §8• §a[O: Mở Ảnh]",
                lastX, lastY, lastZ, dimClean);
        guiGraphics.drawString(font, line2, bannerX + 10, bannerY + 19, 0xFFFFFFFF, true);

        // ==========================================
        // 2. CHẾ ĐỘ PHÓNG TO TOÀN MÀN HÌNH (ZOOM MODAL)
        // ==========================================
        if (isZoomed && hasScreenshot && lastScreenshotTexture != null) {
            guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xF5020617);

            int maxZoomW = screenWidth - 32;
            int maxZoomH = screenHeight - 64;
            int zoomW = maxZoomW;
            int zoomH = (zoomW * 9) / 16;
            if (zoomH > maxZoomH) {
                zoomH = maxZoomH;
                zoomW = (zoomH * 16) / 9;
            }
            int zoomX = (screenWidth - zoomW) / 2;
            int zoomY = (screenHeight - zoomH) / 2 + 8;

            // Khung viền Double-Bezel Neon Cyan phát sáng
            guiGraphics.fill(zoomX - 2, zoomY - 2, zoomX + zoomW + 2, zoomY + zoomH + 2, 0xFF0284C7);
            guiGraphics.fill(zoomX - 1, zoomY - 1, zoomX + zoomW + 1, zoomY + zoomH + 1, 0xFF38BDF8);

            // Vẽ ảnh độ nét cao
            guiGraphics.blit(SCREENSHOT_TEXTURE_ID, zoomX, zoomY, zoomX + zoomW, zoomY + zoomH, 0.0f, 1.0f, 0.0f, 1.0f);

            // Floating Pill Header trên cùng
            int pillW = Math.min(480, screenWidth - 20);
            int pillH = 18;
            int pillX = (screenWidth - pillW) / 2;
            int pillY = Math.max(4, zoomY - pillH - 4);
            guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xEE0F172A);
            guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + 1, 0xFF38BDF8);
            guiGraphics.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, 0xFF38BDF8);
            guiGraphics.fill(pillX, pillY + 1, pillX + 1, pillY + pillH, 0xFF38BDF8);
            guiGraphics.fill(pillX + pillW - 1, pillY, pillX + pillW, pillY + pillH, 0xFF38BDF8);

            String zoomHeader = "§b🔍 ẢNH CHỤP CUỐI CÙNG §8| §fNhấn §e[ESC]§f hoặc §e[Z]§f để đóng §8• §a[O] Mở file";
            int zhW = font.width(zoomHeader);
            guiGraphics.drawString(font, zoomHeader, pillX + (pillW - zhW) / 2, pillY + 5, 0xFFFFFFFF, true);

            // Footer toạ độ dưới ảnh
            String zoomFoot = String.format(Locale.ROOT, "§7Toạ độ: §aX=%.2f Y=%.2f Z=%.2f §8(§e%s§8) §8• §c%s",
                    lastX, lastY, lastZ, dimClean, shortReason);
            int zfW = font.width(zoomFoot);
            guiGraphics.drawString(font, zoomFoot, (screenWidth - zfW) / 2, zoomY + zoomH + 5, 0xFFFFFFFF, true);

            return; // Khi đang phóng to thì không vẽ card thumbnail
        }

        // ==========================================
        // 3. THẺ XEM THỬ ẢNH (PREVIEW CARD) BÊN PHẢI
        // ==========================================
        if (hasScreenshot && lastScreenshotTexture != null && screenWidth >= 480) {
            cardW = Math.min(176, (screenWidth - 340) / 2);
            if (cardW >= 120) {
                int imgW = cardW - 8;
                int imgH = (imgW * 9) / 16;
                cardH = imgH + 30;
                cardX = screenWidth - cardW - 12;
                cardY = 46;

                boolean cardHovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;

                int cardBg = cardHovered ? 0xF51E293B : 0xEE0F172A;
                int cardBorder = cardHovered ? 0xFF38BDF8 : 0x8038BDF8;

                // Nền card Double-Bezel
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, cardBg);
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 1, cardBorder);
                guiGraphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, cardBorder);
                guiGraphics.fill(cardX, cardY + 1, cardX + 1, cardY + cardH, cardBorder);
                guiGraphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, cardBorder);

                // Tiêu đề card
                String cardTitle = "§b📸 ẢNH CHỤP CUỐI";
                guiGraphics.drawString(font, cardTitle, cardX + 6, cardY + 4, 0xFFFFFFFF, true);

                // Thumbnail ảnh
                int imgX = cardX + 4;
                int imgY = cardY + 15;
                guiGraphics.blit(SCREENSHOT_TEXTURE_ID, imgX, imgY, imgX + imgW, imgY + imgH, 0.0f, 1.0f, 0.0f, 1.0f);

                // Viền mảnh bọc quanh ảnh
                guiGraphics.fill(imgX - 1, imgY - 1, imgX + imgW + 1, imgY, 0x40FFFFFF);
                guiGraphics.fill(imgX - 1, imgY + imgH, imgX + imgW + 1, imgY + imgH + 1, 0x40FFFFFF);
                guiGraphics.fill(imgX - 1, imgY, imgX, imgY + imgH, 0x40FFFFFF);
                guiGraphics.fill(imgX + imgW, imgY, imgX + imgW + 1, imgY + imgH, 0x40FFFFFF);

                // Gợi ý thao tác dưới chân card
                String hint = cardHovered ? "§e[Click / Z: Phóng To]" : "§7[Z: Phóng to • O: Mở]";
                int hintW = font.width(hint);
                guiGraphics.drawString(font, hint, cardX + (cardW - hintW) / 2, imgY + imgH + 4, 0xFFFFFFFF, true);
            } else {
                cardW = cardH = 0;
            }
        } else {
            cardW = cardH = 0;
        }
    }

    /**
     * Xử lý chuột click trên DisconnectedScreen (click vào thẻ ảnh chụp để phóng to hoặc thoát zoom).
     */
    public static boolean handleDisconnectedClick(double mouseX, double mouseY, int button) {
        if (!hasLoggedOut) return false;

        if (isZoomed) {
            isZoomed = false;
            return true;
        }

        if (hasScreenshot && cardW > 0 && cardH > 0) {
            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                isZoomed = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Xử lý phím tắt trên DisconnectedScreen:
     * - ESC / Z / SPACE: Đóng chế độ xem phóng to ảnh
     * - Z: Bật chế độ phóng to ảnh nếu đang có ảnh chụp
     * - O: Mở file ảnh chụp trong ứng dụng xem ảnh của hệ điều hành
     */
    public static boolean handleDisconnectedKey(int key) {
        if (!hasLoggedOut) return false;

        if (isZoomed) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_SPACE) {
                isZoomed = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_O) {
                openScreenshotFile();
                return true;
            }
            return true; // Chặn các phím khác khi đang xem ảnh phóng to
        }

        if (key == GLFW.GLFW_KEY_Z && hasScreenshot) {
            isZoomed = true;
            return true;
        }

        if (key == GLFW.GLFW_KEY_O) {
            openScreenshotFile();
            return true;
        }

        return false;
    }

    /**
     * Mở file ảnh chụp màn hình trong ứng dụng xem ảnh mặc định của hệ điều hành.
     */
    public static void openScreenshotFile() {
        Minecraft mc = Minecraft.getInstance();
        if (lastScreenshotFile != null && lastScreenshotFile.exists()) {
            try {
                Util.getPlatform().openFile(lastScreenshotFile);
                Helper.HELPER.logDirect("§a[AutoLogout] Đang mở ảnh chụp màn hình trong Windows: §f" + lastScreenshotFile.getName());
            } catch (Throwable t) {
                BaritoneFileLogger.error("Lỗi khi mở file ảnh: " + t);
            }
        } else {
            try {
                File screenshotsDir = new File(mc.gameDirectory, "screenshots");
                if (screenshotsDir.exists()) {
                    Util.getPlatform().openFile(screenshotsDir);
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Nhắc lại toạ độ khi người chơi đăng nhập lại vào thế giới, đồng thời giải phóng GPU texture.
     */
    public static void onWorldJoined() {
        joinGraceTicks = 100; // 5 giây chờ an toàn (grace period) để di chuyển / gõ lệnh
        if (pendingWorldJoinAlert && hasLoggedOut) {
            pendingWorldJoinAlert = false;
            hasLoggedOut = false;
            String msg = String.format(Locale.ROOT,
                    "§6[Baritone] §eToạ độ AutoLogout gần nhất: §fX: §a%.2f §fY: §a%.2f §fZ: §a%.2f §7(%s) §d(Đã lưu)",
                    lastX, lastY, lastZ, lastDimension);
            Helper.HELPER.logDirect(msg);
            Helper.HELPER.logDirect("§e[Baritone] §a✔ Đã tự động tắt Anti-Player & Auto-Logout để bạn an toàn vào lại thế giới. Bật lại trong ClickGUI khi cần!");
        }

        // Giải phóng texture ảnh chụp cũ để tránh rò rỉ bộ nhớ đồ họa (Native Memory)
        if (lastScreenshotTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(SCREENSHOT_TEXTURE_ID);
                lastScreenshotTexture.close();
            } catch (Throwable ignored) {}
            lastScreenshotTexture = null;
        }
        hasScreenshot = false;
        isZoomed = false;
    }
}
