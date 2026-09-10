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

package baritone.utils.hud;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.utils.ClickGuiTheme;
import baritone.utils.DiscordManager;
import baritone.utils.MiningStatsTracker;
import baritone.utils.StreamerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bang dieu khien toi gian cho Che do Botting (Man hinh den).
 * Hien thi day du thong tin tai khoan voi o spoiler bao mat, toa do, so du, tien trinh dao va chat
 * trong khi the gioi 3D hoan toan khong duoc render de giam tai GPU ve ~0%.
 */
public final class BottingDashboardOverlay {

    private static final List<String> RECENT_CHAT = new CopyOnWriteArrayList<>();
    private static volatile boolean nameSpoilerRevealed = false;

    private BottingDashboardOverlay() {}

    public static void addRecentChat(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        RECENT_CHAT.add(msg.trim());
        while (RECENT_CHAT.size() > 5) {
            RECENT_CHAT.remove(0);
        }
    }

    public static List<String> getRecentChat() {
        return RECENT_CHAT;
    }

    public static void toggleNameSpoiler() {
        nameSpoilerRevealed = !nameSpoilerRevealed;
    }

    public static void setNameSpoilerRevealed(boolean revealed) {
        nameSpoilerRevealed = revealed;
    }

    public static boolean isNameSpoilerRevealed() {
        return nameSpoilerRevealed;
    }

    public static void render(GuiGraphics g, Minecraft mc) {
        if (mc == null || mc.font == null || mc.player == null) {
            return;
        }

        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int cardW = Math.min(420, screenW - 24);
        int cardH = Math.min(260, screenH - 24);
        int cardX = (screenW - cardW) / 2;
        int cardY = (screenH - cardH) / 2;

        // Card Background & Outer Borders
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
        ClickGuiTheme.drawOutline(g, cardX, cardY, cardW, cardH, 0xFF0284C7);
        ClickGuiTheme.drawOutline(g, cardX + 1, cardY + 1, cardW - 2, cardH - 2, 0x3338BDF8);

        // Header Bar
        int headerH = 26;
        g.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + headerH, 0xFF0F172A);
        g.fill(cardX + 2, cardY + headerH, cardX + cardW - 2, cardY + headerH + 1, 0xFF1E293B);

        // Header Title & Badge
        ClickGuiTheme.drawText(g, font, "\u26A1 BOTTING DASHBOARD (ULTRA ECO)", cardX + 10, cardY + 9, 0xFF38BDF8, true);

        int fpsVal = BaritoneAPI.getSettings().bottingFps.value;
        String badgeText = "[" + (fpsVal > 0 ? fpsVal : 10) + " FPS | GPU ~0%]";
        int badgeW = font.width(badgeText) + 8;
        int badgeX = cardX + cardW - badgeW - 8;
        int badgeY = cardY + 5;
        g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 16, 0x40065F46);
        ClickGuiTheme.drawOutline(g, badgeX, badgeY, badgeW, 16, 0xFF10B981);
        ClickGuiTheme.drawText(g, font, badgeText, badgeX + 4, badgeY + 4, 0xFF34D399, false);

        // Content area
        int curY = cardY + headerH + 8;
        int col1X = cardX + 10;
        int col2X = cardX + (cardW / 2) + 6;

        // Column 1: Account (Spoiler Box), Server, Balance, Health
        Player player = mc.player;
        String rawName = player.getScoreboardName();
        String dispName = StreamerUtil.isHidePlayerNameActive() ? StreamerUtil.censorString(rawName) : rawName;

        ClickGuiTheme.drawText(g, font, "\u00A77T\u00E0i kho\u1EA3n: ", col1X, curY, 0xFFE2E8F0, false);
        int prefixW = font.width("Tài khoản: ");
        int boxX = col1X + prefixW + 2;
        int boxY = curY - 2;

        String spoilerText = nameSpoilerRevealed ? ("|| " + dispName + " ||") : "|| \u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022 ||";
        int textW = font.width(spoilerText);
        int boxW = textW + 8;
        int boxH = 13;

        // Ve o Spoiler phong cach Discord
        if (!nameSpoilerRevealed) {
            g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF202225);
            ClickGuiTheme.drawOutline(g, boxX, boxY, boxW, boxH, 0xFF40444B);
            ClickGuiTheme.drawText(g, font, "\u00A78|| \u00A77\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022 \u00A78||", boxX + 4, boxY + 2, 0xFF94A3B8, false);
        } else {
            g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2F3136);
            ClickGuiTheme.drawOutline(g, boxX, boxY, boxW, boxH, 0xFF5865F2);
            ClickGuiTheme.drawText(g, font, "\u00A78|| \u00A7f" + dispName + " \u00A78||", boxX + 4, boxY + 2, 0xFFF8FAFC, false);
        }
        curY += 15;

        String server = DiscordManager.getServerIp();
        if (server.length() > 22) server = server.substring(0, 20) + "...";
        ClickGuiTheme.drawText(g, font, "\u00A77M\u00E1y ch\u1EE7: \u00A7b" + server, col1X, curY, 0xFFE2E8F0, false);
        curY += 12;

        String balance = DiscordManager.getPlayerBalance();
        ClickGuiTheme.drawText(g, font, "\u00A77S\u1ED1 d\u01B0: \u00A7e" + balance, col1X, curY, 0xFFE2E8F0, false);
        curY += 12;

        String coords;
        if (StreamerUtil.isStreamerModeActive()) {
            coords = "\u00A78[\u1EA8N V\u00CC B\u1EA2O M\u1EACT]";
        } else {
            coords = String.format("X: %d  Y: %d  Z: %d", player.getBlockX(), player.getBlockY(), player.getBlockZ());
        }
        ClickGuiTheme.drawText(g, font, "\u00A77T\u1ECDad\u1ED9: \u00A7a" + coords, col1X, curY, 0xFFE2E8F0, false);
        curY += 12;

        int hp = Math.round(player.getHealth());
        int maxHp = Math.round(player.getMaxHealth());
        int food = player.getFoodData().getFoodLevel();
        int armor = player.getArmorValue();
        ClickGuiTheme.drawText(g, font, "\u00A77Sinh t\u1ED3n: \u00A7c" + hp + "/" + maxHp + " \u2764 \u00A77| \u00A76" + food + " 🍗 \u00A77| \u00A79" + armor + " \u00A77Gi\u00E1p", col1X, curY, 0xFFE2E8F0, false);
        curY += 12;

        // Baritone Status
        String status = "\u00A77\u0110ang ch\u1EDD l\u1EC7nh";
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != null) {
            if (baritone.getMineProcess().isActive()) {
                status = "\u00A7a\u0110ang t\u1EF1 \u0111\u1ED9ng \u0111\u00E0o qu\u1EB7ng";
            } else if (baritone.getPathingBehavior().hasPath()) {
                status = "\u00A7b\u0110ang di chuy\u1EC3n theo \u0111\u01B0\u1EDDng";
            } else if (baritone.getFarmProcess().isActive()) {
                status = "\u00A7e\u0110ang t\u1EF1 \u0111\u1ED9ng l\u00E0m n\u00F4ng";
            }
        }
        ClickGuiTheme.drawText(g, font, "\u00A77Tr\u1EA1ng th\u00E1i: " + status, col1X, curY, 0xFFE2E8F0, false);

        // Column 2: Mining Stats (Quang & Khoi)
        int statY = cardY + headerH + 8;
        MiningStatsTracker stats = MiningStatsTracker.getInstance();
        int totalMined = stats.getTotalBlocksMined();
        ClickGuiTheme.drawText(g, font, "\u00A77Kh\u1ED1i \u0111\u00E3 \u0111\u00E0o: \u00A7f" + totalMined, col2X, statY, 0xFFE2E8F0, false);
        statY += 12;

        int diamond = stats.getOreCount(MiningStatsTracker.OreType.DIAMOND);
        int diamondDrops = stats.getDiamondDropCount();
        String diaText = diamondDrops > diamond ? diamond + " (" + diamondDrops + " vi\u00EAn)" : String.valueOf(diamond);
        ClickGuiTheme.drawText(g, font, "\u00A7b\u25C6 Kim C\u01B0\u01A1ng: \u00A7f" + diaText, col2X, statY, 0xFF38BDF8, false);
        statY += 12;

        int emerald = stats.getOreCount(MiningStatsTracker.OreType.EMERALD);
        ClickGuiTheme.drawText(g, font, "\u00A7a\u25C6 L\u1EE5c B\u1EA3o: \u00A7f" + emerald, col2X, statY, 0xFF34D399, false);
        statY += 12;

        int debris = stats.getOreCount(MiningStatsTracker.OreType.ANCIENT_DEBRIS);
        ClickGuiTheme.drawText(g, font, "\u00A7d\u25C6 M\u1EA3nh C\u1ED5 \u0110\u1EA1i: \u00A7f" + debris, col2X, statY, 0xFFC084FC, false);
        statY += 12;

        int gold = stats.getOreCount(MiningStatsTracker.OreType.GOLD);
        ClickGuiTheme.drawText(g, font, "\u00A7e\u25C6 Qu\u1EB7ng V\u00E0ng: \u00A7f" + gold, col2X, statY, 0xFFFBBF24, false);
        statY += 12;

        int iron = stats.getOreCount(MiningStatsTracker.OreType.IRON);
        ClickGuiTheme.drawText(g, font, "\u00A7f\u25C6 Qu\u1EB7ng S\u1EAFt: \u00A7f" + iron, col2X, statY, 0xFFE2E8F0, false);

        // Chat / Activity Section Divider
        int chatSectionY = Math.max(curY, statY) + 8;
        g.fill(cardX + 8, chatSectionY, cardX + cardW - 8, chatSectionY + 1, 0xFF1E293B);
        chatSectionY += 5;

        ClickGuiTheme.drawText(g, font, "\u00A78Tin nh\u1EAFn chat g\u1EA7n \u0111\u00E2y:", cardX + 10, chatSectionY, 0xFF94A3B8, false);
        chatSectionY += 11;

        List<String> recentChat = RECENT_CHAT;
        if (recentChat.isEmpty()) {
            ClickGuiTheme.drawText(g, font, "\u00A78(Ch\u01B0a c\u00F3 tin nh\u1EAFn n\u00E0o)", cardX + 14, chatSectionY, 0xFF64748B, false);
        } else {
            int maxChatLines = Math.min(3, recentChat.size());
            int startIndex = Math.max(0, recentChat.size() - maxChatLines);
            for (int i = startIndex; i < recentChat.size(); i++) {
                String chatLine = recentChat.get(i);
                int maxW = cardW - 24;
                if (font.width(chatLine) > maxW) {
                    while (chatLine.length() > 6 && font.width(chatLine + "...") > maxW) {
                        chatLine = chatLine.substring(0, chatLine.length() - 1);
                    }
                    chatLine += "...";
                }
                ClickGuiTheme.drawText(g, font, "\u00A77\u203A " + chatLine, cardX + 14, chatSectionY, 0xFFCBD5E1, false);
                chatSectionY += 10;
            }
        }

        // Footer Bar
        int footerH = 20;
        int footerY = cardY + cardH - footerH;
        g.fill(cardX + 2, footerY, cardX + cardW - 2, footerY + 1, 0xFF1E293B);
        g.fill(cardX + 2, footerY + 1, cardX + cardW - 2, cardY + cardH - 2, 0xFF080D18);
        String footerText = "\u00A7b[F4] \u00A77M\u1EDF ClickGUI  |  \u00A7d#botting name \u00A77M\u1EDF Spoiler  |  \u00A7f#botting off";
        int fW = font.width(footerText);
        ClickGuiTheme.drawText(g, font, footerText, cardX + (cardW - fW) / 2, footerY + 6, 0xFF94A3B8, false);
    }
}
