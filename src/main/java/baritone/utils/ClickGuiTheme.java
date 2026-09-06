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

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * NextGen ClickGUI Theme Engine (Inspired by LiquidBounce Nextgen & Meteor Client).
 * Hệ thống giao diện đồ họa siêu cấp hiện đại: Dark Glass, Neon Accents, Pill Switches,
 * Smooth Cards, và Hardware Telemetry HUD.
 */
public final class ClickGuiTheme {

    // === BẢNG MÀU CHUẨN LIQUIDBOUNCE NEXTGEN & METEOR (32-bit ARGB) ===
    public static final int BG_SCREEN_TOP = 0xEA080C14;
    public static final int BG_SCREEN_BOTTOM = 0xF50B0F17;
    public static final int BG_CARD = 0xB00F172A;
    public static final int BG_CARD_HOVER = 0xD81E293B;
    public static final int BG_CARD_ACTIVE = 0xD0132338;
    public static final int BG_INPUT = 0xD00B0F17;

    public static final int BORDER_CARD = 0x3038BDF8;
    public static final int BORDER_CARD_HOVER = 0x8038BDF8;
    public static final int BORDER_ACTIVE = 0xFF38BDF8;

    public static final int ACCENT_CYAN = 0xFF38BDF8;
    public static final int ACCENT_BLUE = 0xFF60A5FA;
    public static final int ACCENT_EMERALD = 0xFF34D399;
    public static final int ACCENT_AMBER = 0xFFFBBF24;
    public static final int ACCENT_ROSE = 0xFFF87171;
    public static final int ACCENT_PURPLE = 0xFFC084FC;

    public static final int TEXT_TITLE = 0xFFF8FAFC;
    public static final int TEXT_BODY = 0xFFE2E8F0;
    public static final int TEXT_MUTED = 0xFF94A3B8;
    public static final int TEXT_DIM = 0xFF64748B;

    private ClickGuiTheme() {
    }

    /**
     * Helper vẽ text luôn đảm bảo có alpha channel (chống tàng hình trong MC 1.21).
     */
    public static void drawText(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        int argb = (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
        g.drawString(font, text, x, y, argb, shadow);
    }

    /**
     * Vẽ viền hình chữ nhật 1px.
     */
    public static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        int argb = (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /**
     * Vẽ Card giao diện hiện đại với nền kính mờ và viền phản chiếu ánh sáng.
     */
    public static void drawCard(GuiGraphics g, int x, int y, int w, int h, int bgColor, int borderColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        drawOutline(g, x, y, w, h, borderColor);
    }

    /**
     * Vẽ công tắc dạng viên thuốc (Modern Pill Switch) chuẩn LiquidBounce Nextgen.
     */
    public static void drawPillSwitch(GuiGraphics g, Font font, int x, int y, int w, int h, boolean active, boolean hover) {
        int trackColor = active ? (hover ? 0xFF0284C7 : 0xFF0EA5E9) : (hover ? 0xFF334155 : 0xFF1E293B);
        int borderColor = active ? 0xFF38BDF8 : 0x5064748B;

        // Vẽ thân rãnh switch
        g.fill(x, y, x + w, y + h, trackColor);
        drawOutline(g, x, y, w, h, borderColor);

        // Kích thước nút trượt (Thumb)
        int thumbW = h - 4;
        int thumbH = h - 4;
        int thumbY = y + 2;
        int thumbX = active ? (x + w - thumbW - 2) : (x + 2);
        int thumbColor = active ? 0xFFFFFFFF : 0xFF94A3B8;

        g.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbColor);

        // Nhãn chữ nhỏ ON / OFF (chỉ vẽ khi bề rộng switch đủ hiển thị)
        if (w >= 30) {
            String label = active ? "ON" : "OFF";
            int labelColor = active ? 0xFFFFFFFF : 0xFF64748B;
            int labelX = active ? (x + 3) : (x + w - font.width(label) - 3);
            int labelY = y + (h - 8) / 2;
            drawText(g, font, label, labelX, labelY, labelColor, false);
        }
    }

    /**
     * Vẽ thanh Tab danh mục trên đỉnh màn hình kèm Minecraft Item Icon thật (LiquidBounce & Meteor Style).
     */
    public static void drawTab(GuiGraphics g, Font font, ItemStack iconItem, String label, int x, int y, int w, int h, boolean active, boolean hover, int accentColor) {
        int bg = active ? 0x4038BDF8 : (hover ? 0x2038BDF8 : 0x00000000);
        if (bg != 0) {
            g.fill(x, y, x + w, y + h, bg);
        }

        // Đường gạch chân neon khi tab được chọn
        if (active) {
            g.fill(x, y + h - 2, x + w, y + h, accentColor);
        }

        int textColor = active ? 0xFFFFFFFF : (hover ? 0xFFCBD5E1 : 0xFF94A3B8);
        boolean hasIcon = (iconItem != null && !iconItem.isEmpty());
        int labelW = font.width(label);

        if (hasIcon) {
            int iconY = y + (h - 16) / 2;
            if (label.isEmpty() || w < labelW + 24) {
                // Không đủ chỗ cho cả chữ và icon: Vẽ Item Icon thật 16x16 căn giữa
                int iconX = x + (w - 16) / 2;
                g.renderFakeItem(iconItem, iconX, iconY);
            } else {
                // Đủ chỗ: Vẽ cả Item Icon và nhãn chữ
                int totalW = 16 + 4 + labelW;
                int startX = x + (w - totalW) / 2;
                g.renderFakeItem(iconItem, startX, iconY);
                int textY = y + (h - 8) / 2;
                drawText(g, font, label, startX + 20, textY, textColor, active);
            }
        } else {
            int textX = x + (w - labelW) / 2;
            int textY = y + (h - 8) / 2;
            drawText(g, font, label, textX, textY, textColor, active);
        }
    }

    /**
     * Vẽ thanh tiến trình hiện đại (Modern Progress Bar).
     */
    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float percent, int fillColor, int emptyColor) {
        g.fill(x, y, x + w, y + h, emptyColor);
        drawOutline(g, x, y, w, h, 0x30FFFFFF);
        int fillW = (int) (w * Mth.clamp(percent, 0.0f, 1.0f));
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, fillColor);
        }
    }

    /**
     * Vẽ nút bấm hành động (Action Button) với icon vật phẩm Minecraft thật và hiệu ứng hover phản hồi.
     */
    public static void drawActionButton(GuiGraphics g, Font font, ItemStack iconItem, String text, int x, int y, int w, int h, int accentColor, boolean hover) {
        int bgTop = hover ? (accentColor | 0x80000000) : 0xB00F172A;
        int bgBottom = hover ? (accentColor | 0xC0000000) : 0xD00B0F17;
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBottom);

        int borderColor = hover ? accentColor : 0x5038BDF8;
        drawOutline(g, x, y, w, h, borderColor);

        if (hover) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, accentColor);
        }

        int textColor = hover ? 0xFFFFFFFF : 0xFFE2E8F0;
        boolean hasIcon = (iconItem != null && !iconItem.isEmpty());
        int textW = font.width(text);

        if (hasIcon) {
            int iconY = y + (h - 16) / 2;
            if (text.isEmpty() || w < textW + 24) {
                // Nút hẹp: Chỉ vẽ Item Icon căn giữa
                int iconX = x + (w - 16) / 2;
                g.renderFakeItem(iconItem, iconX, iconY);
            } else {
                // Đủ rộng: Vẽ Icon + Chữ
                int totalW = 16 + 4 + textW;
                int startX = x + (w - totalW) / 2;
                g.renderFakeItem(iconItem, startX, iconY);
                int textY = y + (h - 8) / 2;
                drawText(g, font, text, startX + 20, textY, textColor, true);
            }
        } else {
            int textX = x + (w - textW) / 2;
            int textY = y + (h - 8) / 2;
            drawText(g, font, text, textX, textY, textColor, true);
        }
    }
}
