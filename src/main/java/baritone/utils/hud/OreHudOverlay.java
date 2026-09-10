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
import baritone.utils.AutoMineScreen;
import baritone.utils.ClickGuiTheme;
import baritone.utils.MiningStatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HUD đào quặng độc lập: không đọc state của AutoMineScreen, chỉ đọc snapshot
 * từ MiningStatsTracker và vị trí từ OreHudConfig.
 *
 * LƯU Ý streamer: HUD này không bao giờ chứa tọa độ hay tên người chơi nên
 * không cần che thêm gì khi streamer mode bật.
 */
public final class OreHudOverlay {

    public static final int BASE_WIDTH = 160;
    public static final int MAX_ROWS = 6;
    private static final int HEADER_H = 16;
    private static final int ROW_H = 18;

    private static final OreHudOverlay INSTANCE = new OreHudOverlay();

    public static OreHudOverlay getInstance() {
        return INSTANCE;
    }

    private OreHudConfig config = new OreHudConfig();
    private boolean configLoaded = false;
    private boolean editMode = false;

    // Khung vẽ lần cuối (tọa độ màn hình đã scale), cho màn hình chỉnh HUD hit-test.
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;

    private OreHudOverlay() {}

    public OreHudConfig getConfig() {
        ensureLoaded();
        return config;
    }

    public void reload() {
        config = OreHudConfig.load();
        configLoaded = true;
    }

    private void ensureLoaded() {
        if (!configLoaded) {
            reload();
        }
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public int getLastX() {
        return lastX;
    }

    public int getLastY() {
        return lastY;
    }

    public int getLastW() {
        return lastW;
    }

    public int getLastH() {
        return lastH;
    }

    /**
     * Vẽ HUD lên màn hình game. Tự ẩn khi tắt GUI (F1), khi mở debug (F3),
     * khi tắt thống kê, và khi không mining (trừ chế độ chỉnh HUD).
     */
    public void render(GuiGraphics graphics, Font font) {
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return;
        }
        if (mc == null || font == null) {
            return;
        }
        if (mc.options.hideGui && !editMode) {
            return;
        }
        if (!editMode && mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        if (!AutoMineScreen.optMiningStats && !editMode) {
            return;
        }

        boolean mining = editMode || isMining();
        if (!mining) {
            return;
        }
        boolean chop = isChopMode();

        ensureLoaded();
        List<Row> rows = buildRows(chop);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float scale = config.scale;
        int contentH = contentHeight(rows);
        int scaledW = Math.round(BASE_WIDTH * scale);
        int scaledH = Math.round(contentH * scale);
        // Clamp toàn bộ HUD trong màn hình, kể cả sau resize/đổi scale.
        int x = Math.max(2, Math.min(config.x, Math.max(2, screenW - scaledW - 2)));
        int y = Math.max(2, Math.min(config.y, Math.max(2, screenH - scaledH - 2)));
        lastX = x;
        lastY = y;
        lastW = scaledW;
        lastH = scaledH;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        try {
            drawCard(graphics, font, chop, rows, isMining());
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private int contentHeight(List<Row> rows) {
        if (config.collapsed) {
            return HEADER_H + 13;
        }
        int itemsH = rows.isEmpty() ? 15 : rows.size() * ROW_H + 4;
        return HEADER_H + 12 + 16 + itemsH + 5;
    }

    private void drawCard(GuiGraphics g, Font font, boolean chop, List<Row> rows, boolean mining) {
        int w = BASE_WIDTH;
        int h = contentHeight(rows);
        int themeColor = chop ? 0xFF34D399 : 0xFF38BDF8;

        ClickGuiTheme.drawGlowPanel(g, 0, 0, w, h, themeColor);
        ClickGuiTheme.drawDoubleBezelCard(g, 0, 0, w, h, themeColor, mining, false);
        g.fill(1, 2, 3, h - 2, themeColor);

        String title = chop ? "CHẶT CÂY" : "ĐÀO QUẶNG";
        ClickGuiTheme.drawText(g, font, title, 7, 4, themeColor, true);
        String dot = mining ? "§a●" : "§7○";
        ClickGuiTheme.drawText(g, font, dot, w - 13, 4, 0xFFFFFFFF, true);
        if (editMode) {
            ClickGuiTheme.drawText(g, font, "kéo", w - 32, 4, 0xFF94A3B8, false);
        }

        if (config.collapsed) {
            String summary = collapsedSummary(chop);
            ClickGuiTheme.drawText(g, font, summary, 7, HEADER_H + 2, 0xFFE2E8F0, true);
            return;
        }

        int curY = HEADER_H;
        String rate = formatRate(MiningStatsTracker.getInstance().getBlocksPerHour(), chop);
        ClickGuiTheme.drawText(g, font,
                "§8" + MiningStatsTracker.getInstance().getFormattedDuration() + " §7| §e" + rate,
                6, curY, 0xFF94A3B8, true);
        curY += 12;
        g.fill(5, curY, w - 5, curY + 1, 0x25FFFFFF);
        curY += 3;

        String totalLabel = chop ? "Đã chặt:" : "Đã đào:";
        String totalStr = String.format(Locale.ROOT, "%,d%s",
                MiningStatsTracker.getInstance().getTotalBlocksMined(), chop ? " khúc" : " khối");
        ClickGuiTheme.drawText(g, font, totalLabel, 6, curY, 0xFFE2E8F0, true);
        ClickGuiTheme.drawText(g, font, "§e" + totalStr, w - 6 - font.width(totalStr), curY, 0xFFFBBF24, true);
        curY += 13;

        if (rows.isEmpty()) {
            g.fill(5, curY, w - 5, curY + 1, 0x25FFFFFF);
            curY += 3;
            ClickGuiTheme.drawText(g, font, "Chưa có dữ liệu", 6, curY + 2, 0xFF64748B, false);
            return;
        }
        g.fill(5, curY, w - 5, curY + 1, 0x25FFFFFF);
        curY += 3;

        for (Row row : rows) {
            if (row.overflow > 0) {
                ClickGuiTheme.drawText(g, font, "+" + row.overflow + " loại khác",
                        6, curY + 4, 0xFF64748B, false);
                curY += ROW_H;
                continue;
            }
            int iconBg = row.count > 0 ? ((row.color & 0x00FFFFFF) | 0x22000000) : 0x10FFFFFF;
            int iconBorder = row.count > 0 ? ((row.color & 0x00FFFFFF) | 0x50000000) : 0x18FFFFFF;
            g.fill(4, curY - 1, 22, curY + 17, iconBg);
            ClickGuiTheme.drawOutline(g, 4, curY - 1, 18, 18, iconBorder);
            g.renderFakeItem(row.icon, 5, curY);
            ClickGuiTheme.drawText(g, font, row.name, 25, curY + 4, 0xFFE2E8F0, true);
            String countStr = String.format(Locale.ROOT, "%,d", row.count);
            int countColor = row.count > 0 ? 0xFFFFFFFF : 0xFF64748B;
            ClickGuiTheme.drawText(g, font, countStr, w - 6 - font.width(countStr), curY + 4, countColor, true);
            curY += ROW_H;
        }
    }

    /**
     * Chỉ hiện loại đào được > 0 trong phiên hiện tại, tối đa 6 dòng.
     */
    private List<Row> buildRows(boolean chop) {
        MiningStatsTracker tracker = MiningStatsTracker.getInstance();
        List<Row> all = new ArrayList<>();
        if (chop) {
            for (MiningStatsTracker.WoodType wood : MiningStatsTracker.WoodType.values()) {
                int count = tracker.getWoodCount(wood);
                if (count > 0) {
                    all.add(new Row(wood.getItemStack(), wood.getNameVi(), count, wood.getColor()));
                }
            }
            for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                int count = tracker.getOreCount(ore);
                if (count > 0) {
                    all.add(new Row(ore.getItemStack(), ore.getNameVi(), count, ore.getColor()));
                }
            }
        } else {
            for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                int oreCount = tracker.getOreCount(ore);
                int dropCount = ore.getDropItem() != null ? tracker.getDropItemCount(ore.getDropItem()) : 0;
                if (oreCount <= 0 && dropCount <= 0) {
                    continue;
                }
                if (ore.getDropItem() != null && ore.getDropItem() != ore.getItemStack().getItem()) {
                    all.add(new Row(new ItemStack(ore.getDropItem()), ore.getDropItemNameVi(), dropCount, ore.getColor()));
                    all.add(new Row(ore.getItemStack(), "Quặng " + ore.getNameVi(), oreCount, ore.getColor()));
                } else {
                    all.add(new Row(ore.getItemStack(), ore.getNameVi(), oreCount, ore.getColor()));
                }
            }
            for (MiningStatsTracker.WoodType wood : MiningStatsTracker.WoodType.values()) {
                int count = tracker.getWoodCount(wood);
                if (count > 0) {
                    all.add(new Row(wood.getItemStack(), wood.getNameVi(), count, wood.getColor()));
                }
            }
        }
        if (all.size() <= MAX_ROWS) {
            return all;
        }
        List<Row> shown = new ArrayList<>(all.subList(0, MAX_ROWS));
        shown.add(Row.overflow(all.size() - MAX_ROWS));
        return shown;
    }

    private String collapsedSummary(boolean chop) {
        MiningStatsTracker tracker = MiningStatsTracker.getInstance();
        int kinds = 0;
        if (chop) {
            for (MiningStatsTracker.WoodType wood : MiningStatsTracker.WoodType.values()) {
                if (tracker.getWoodCount(wood) > 0) {
                    kinds++;
                }
            }
        } else {
            for (MiningStatsTracker.OreType ore : MiningStatsTracker.OreType.values()) {
                if (tracker.getOreCount(ore) > 0) {
                    kinds++;
                }
            }
        }
        return kinds + (chop ? " loại gỗ" : " quặng") + " | " + formatRate(tracker.getBlocksPerHour(), chop);
    }

    private static String formatRate(int perHour, boolean chop) {
        String unit = chop ? " khúc/h" : " khối/h";
        if (perHour >= 1000) {
            double k = perHour / 1000.0;
            String text = k >= 100 ? String.valueOf((int) k) : String.format(Locale.ROOT, "%.1f", k);
            return text + "k" + unit;
        }
        return perHour + unit;
    }

    private static boolean isMining() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isChopMode() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isChopMode();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class Row {
        final ItemStack icon;
        final String name;
        final int count;
        final int color;
        final int overflow;

        Row(ItemStack icon, String name, int count, int color) {
            this.icon = icon;
            this.name = name;
            this.count = count;
            this.color = color;
            this.overflow = 0;
        }

        private Row(int overflow) {
            this.icon = ItemStack.EMPTY;
            this.name = "";
            this.count = 0;
            this.color = 0;
            this.overflow = overflow;
        }

        static Row overflow(int overflow) {
            return new Row(overflow);
        }
    }
}
