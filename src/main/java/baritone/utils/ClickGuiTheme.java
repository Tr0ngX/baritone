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
 * NextGen Tr0ngX ClickGUI Theme Engine (Taste-Skill Edition).
 * Thiết kế chuẩn Awwwards / Apple-meets-Linear với:
 * - Double-Bezel (Doppelrand) Architecture: Vỏ viền ngoài + Lõi kính tối sâu bên trong.
 * - Dynamic Laser Accent & Ambient Bloom: Vạch phát quang 1px kèm tán xạ ánh sáng mềm.
 * - Haptic 3D Pill Switches: Công tắc xúc giác có phản xạ ánh sáng Specular Highlight.
 * - Filter Chips & Badges: Huy hiệu lọc thông minh (Tất cả / Đang bật / Đang tắt).
 * - Hero CTA Buttons: Nút hành động nổi bật phân cấp thị giác rõ ràng.
 */
public final class ClickGuiTheme {

    // === BẢNG MÀU CHUẨN OLED OBSIDIAN GLASS (32-bit ARGB) ===
    public static final int BG_SCREEN_TOP = 0xF2040711;     // OLED Midnight sâu thẳm
    public static final int BG_SCREEN_BOTTOM = 0xF9070B16;  // Deep Obsidian gradient
    public static final int BG_PANEL = 0xDE0A0F1E;          // Khung panel chính
    public static final int BG_CARD = 0xB50E1729;           // Nền card mặc định
    public static final int BG_CARD_HOVER = 0xE018263E;     // Nền card khi hover
    public static final int BG_CARD_ACTIVE = 0xDE0C1F38;    // Nền card khi active
    public static final int BG_INPUT = 0xE8080D18;          // Nền ô tìm kiếm

    // === HỆ THỐNG VIỀN & ÁNH SÁNG ===
    public static final int BORDER_CARD = 0x2838BDF8;       // Viền hairline 1px nhẹ
    public static final int BORDER_CARD_HOVER = 0x9038BDF8; // Viền sáng khi hover
    public static final int BORDER_ACTIVE = 0xFF38BDF8;     // Viền neon khi active

    // === BẢNG MÀU NEON SPECTRUM THEO CHUYÊN MỤC ===
    public static final int ACCENT_CYAN = 0xFF38BDF8;       // Quặng Kim Cương & Công Nghệ
    public static final int ACCENT_BLUE = 0xFF6366F1;       // Di Chuyển & Tốc Độ
    public static final int ACCENT_EMERALD = 0xFF10B981;    // Sinh Tồn & Cây Cối
    public static final int ACCENT_AMBER = 0xFFF59E0B;      // Cảnh Báo & Thống Kê
    public static final int ACCENT_ROSE = 0xFFF43F5E;       // Nguy Hiểm & Thoát Hiểm
    public static final int ACCENT_PURPLE = 0xFFA855F7;     // Giao Diện & Huyền Bí

    // === BẢNG MÀU TYPOGRAPHY TƯƠNG PHẢN CAO ===
    public static final int TEXT_TITLE = 0xFFF8FAFC;        // Trắng tuyết sắc nét
    public static final int TEXT_BODY = 0xFFE2E8F0;         // Xám bạc dễ đọc
    public static final int TEXT_MUTED = 0xFF94A3B8;        // Xám trung tính
    public static final int TEXT_DIM = 0xFF64748B;          // Xám mờ cho mô tả phụ

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
     * Vẽ viền hình chữ nhật 1px chuẩn nét.
     */
    public static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        int argb = (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /**
     * Vẽ Card giao diện truyền thống (tương thích ngược).
     */
    public static void drawCard(GuiGraphics g, int x, int y, int w, int h, int bgColor, int borderColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        drawOutline(g, x, y, w, h, borderColor);
    }

    /**
     * Vẽ Card cao cấp với kiến trúc Double-Bezel (Doppelrand):
     * 1. Lớp vỏ ngoài (Outer Bezel) với viền hairline.
     * 2. Lớp lõi sâu bên trong (Inner Core) thụt vào 1px.
     * 3. Vạch ánh sáng phản chiếu Specular Highlight trên đỉnh lõi.
     * 4. Vạch Laser Accent trên cùng khi Active/Hover kèm hiệu ứng tán xạ ánh sáng (Bloom).
     */
    public static void drawDoubleBezelCard(GuiGraphics g, int x, int y, int w, int h, int accentColor, boolean active, boolean hover) {
        // 1. Lớp vỏ ngoài (Outer Shell)
        int outerBorder = hover ? ((accentColor & 0x00FFFFFF) | 0x90000000)
                : (active ? ((accentColor & 0x00FFFFFF) | 0x60000000) : 0x2238BDF8);
        drawOutline(g, x, y, w, h, outerBorder);

        // 2. Lớp lõi bên trong (Inner Core) thụt vào 1px
        int innerTop = hover ? 0xEE142136 : (active ? 0xE60D1D33 : 0xCC091120);
        int innerBottom = hover ? 0xFA0D1728 : (active ? 0xF2081424 : 0xE0060B16);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, innerTop, innerBottom);

        // 3. Phản chiếu ánh sáng Specular Highlight tinh tế ở đỉnh lõi
        int highlightColor = active ? 0x28FFFFFF : (hover ? 0x1EFFFFFF : 0x0EFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlightColor);

        // 4. Vạch Laser Glow trên đỉnh card khi Active hoặc Hover
        if (active || hover) {
            int laserAlpha = active ? 0xFF000000 : 0x95000000;
            int laserColor = (accentColor & 0x00FFFFFF) | laserAlpha;
            g.fill(x + 2, y, x + w - 2, y + 1, laserColor);

            // Vạch tán xạ ánh sáng mềm (Bloom) 1px phía dưới vạch laser
            int bloomAlpha = active ? 0x35000000 : 0x18000000;
            g.fill(x + 4, y + 1, x + w - 4, y + 2, (accentColor & 0x00FFFFFF) | bloomAlpha);
        }
    }

    /**
     * Tương thích ngược với hàm drawGlowingCard cũ.
     */
    public static void drawGlowingCard(GuiGraphics g, int x, int y, int w, int h, int bgColor, int borderColor, int accentColor, boolean active, boolean hover) {
        drawDoubleBezelCard(g, x, y, w, h, accentColor, active, hover);
    }

    /**
     * Vẽ ánh sáng nền Ambient Glow (Soft Shadow 3 tầng) xung quanh khung Panel chính.
     */
    public static void drawGlowPanel(GuiGraphics g, int x, int y, int w, int h, int accentColor) {
        int glowColor1 = (accentColor & 0x00FFFFFF) | 0x22000000;
        int glowColor2 = (accentColor & 0x00FFFFFF) | 0x0E000000;
        int glowColor3 = (accentColor & 0x00FFFFFF) | 0x05000000;

        // Tầng 3 (offset 3px ngoài cùng)
        g.fill(x - 3, y - 3, x + w + 3, y - 2, glowColor3);
        g.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, glowColor3);
        g.fill(x - 3, y - 2, x - 2, y + h + 2, glowColor3);
        g.fill(x + w + 2, y - 2, x + w + 3, y + h + 2, glowColor3);

        // Tầng 2 (offset 2px)
        g.fill(x - 2, y - 2, x + w + 2, y - 1, glowColor2);
        g.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, glowColor2);
        g.fill(x - 2, y - 1, x - 1, y + h + 1, glowColor2);
        g.fill(x + w + 1, y - 1, x + w + 2, y + h + 1, glowColor2);

        // Tầng 1 (offset 1px bên trong)
        g.fill(x - 1, y - 1, x + w + 1, y, glowColor1);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, glowColor1);
        g.fill(x - 1, y, x, y + h, glowColor1);
        g.fill(x + w, y, x + w + 1, y + h, glowColor1);
    }

    /**
     * Vẽ công tắc dạng viên thuốc (Haptic 3D Pill Switch) chuẩn phong cách Apple & LiquidBounce Nextgen.
     */
    public static void drawPillSwitch(GuiGraphics g, Font font, int x, int y, int w, int h, boolean active, boolean hover) {
        // Màu thân rãnh switch (Track)
        int trackTop = active ? (hover ? 0xFF0284C7 : 0xFF0EA5E9) : (hover ? 0xFF2A374A : 0xFF182232);
        int trackBottom = active ? (hover ? 0xFF0369A1 : 0xFF0284C7) : (hover ? 0xFF1E293B : 0xFF0F172A);
        int borderColor = active ? (hover ? 0xFF7DD3FC : 0xFF38BDF8) : (hover ? 0x8064748B : 0x4064748B);

        // Vẽ thân rãnh switch có gradient và viền hairline
        g.fillGradient(x, y, x + w, y + h, trackTop, trackBottom);
        drawOutline(g, x, y, w, h, borderColor);

        // Bóng tối lõm xuống ở đỉnh rãnh tạo cảm giác rãnh thụt sâu
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30000000);

        // Kích thước nút trượt (Thumb)
        int thumbW = h - 4;
        int thumbH = h - 4;
        int thumbY = y + 2;
        int thumbX = active ? (x + w - thumbW - 2) : (x + 2);

        // Thumb nổi 3D với viền và specular highlight
        int thumbColorTop = active ? 0xFFFFFFFF : 0xFFCBD5E1;
        int thumbColorBottom = active ? 0xFFE2E8F0 : 0xFF94A3B8;
        g.fillGradient(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbColorTop, thumbColorBottom);

        // Viền quanh thumb
        int thumbBorder = active ? 0xFF38BDF8 : 0xFF475569;
        drawOutline(g, thumbX, thumbY, thumbW, thumbH, thumbBorder);

        // Specular highlight trên nửa trên thumb
        g.fill(thumbX + 1, thumbY + 1, thumbX + thumbW - 1, thumbY + 2, 0x90FFFFFF);

        // Chấm tròn nhỏ hiển thị trạng thái ở giữa thumb
        int dotColor = active ? 0xFF0284C7 : 0xFF64748B;
        int dotX = thumbX + (thumbW - 2) / 2;
        int dotY = thumbY + (thumbH - 2) / 2;
        g.fill(dotX, dotY, dotX + 2, dotY + 2, dotColor);

        // Nhãn chữ nhỏ BẬT / TẮT
        if (w >= 30) {
            String label = active ? "BẬT" : "TẮT";
            int labelColor = active ? 0xFFFFFFFF : 0xFF64748B;
            int labelX = active ? (x + 3) : (x + w - font.width(label) - 3);
            int labelY = y + (h - 8) / 2;
            drawText(g, font, label, labelX, labelY, labelColor, false);
        }
    }

    /**
     * Vẽ Chip / Badge lọc danh mục nhỏ gọn (Tất cả / Đang bật / Đang tắt).
     */
    public static void drawFilterChip(GuiGraphics g, Font font, String label, int x, int y, int w, int h, boolean active, boolean hover, int accentColor) {
        int bg = active ? ((accentColor & 0x00FFFFFF) | 0x35000000)
                : (hover ? 0x25FFFFFF : 0x14FFFFFF);
        int border = active ? accentColor : (hover ? 0x60FFFFFF : 0x25FFFFFF);
        int textCol = active ? 0xFFFFFFFF : (hover ? 0xFFE2E8F0 : 0xFF94A3B8);

        g.fill(x, y, x + w, y + h, bg);
        drawOutline(g, x, y, w, h, border);

        if (active) {
            // Vạch phát sáng trên đỉnh chip
            g.fill(x + 1, y, x + w - 1, y + 1, accentColor);
        }

        int textW = font.width(label);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        drawText(g, font, label, textX, textY, textCol, active);
    }

    /**
     * Vẽ thanh Tab danh mục trên đỉnh màn hình kèm Minecraft Item Icon thật.
     */
    public static void drawTab(GuiGraphics g, Font font, ItemStack iconItem, String label, int x, int y, int w, int h, boolean active, boolean hover, int accentColor) {
        int bg = active ? ((accentColor & 0x00FFFFFF) | 0x30000000)
                : (hover ? 0x1EFFFFFF : 0x00000000);
        if (bg != 0) {
            g.fill(x, y, x + w, y + h, bg);
        }

        // Đường gạch chân neon 2px khi tab được chọn
        if (active) {
            g.fill(x, y + h - 2, x + w, y + h, accentColor);
            // Tán xạ ánh sáng nhẹ phía trên đường gạch chân
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, (accentColor & 0x00FFFFFF) | 0x50000000);
        }

        int textColor = active ? 0xFFFFFFFF : (hover ? 0xFFF1F5F9 : 0xFF94A3B8);
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

    // === LIQUIDBOUNCE DROPDOWN STYLE (sidebar + module rows) ===

    public static final int BG_SIDEBAR = 0xE6060B16;
    public static final int BG_ROW = 0xCC0B1220;
    public static final int BG_ROW_HOVER = 0xE0131F33;
    public static final int BG_DROPDOWN = 0xF5060C16;
    public static final int BORDER_ROW = 0x1E38BDF8;

    /**
     * Nút sidebar dọc kiểu LiquidBounce: icon + label, active có vạch accent trái + nền tint.
     */
    public static void drawSidebarButton(GuiGraphics g, Font font, ItemStack iconItem, String label,
                                         int x, int y, int w, int h,
                                         boolean active, boolean hover, int accentColor) {
        int bg = active ? ((accentColor & 0x00FFFFFF) | 0x28000000)
                : (hover ? 0x1AFFFFFF : 0x00000000);
        if (bg != 0) {
            g.fill(x, y, x + w, y + h, bg);
        }
        // Vạch accent trái 2px khi active/hover
        if (active) {
            g.fill(x, y + 2, x + 2, y + h - 2, accentColor);
        } else if (hover) {
            g.fill(x, y + 2, x + 2, y + h - 2, (accentColor & 0x00FFFFFF) | 0x60000000);
        }
        boolean hasIcon = (iconItem != null && !iconItem.isEmpty());
        int textX = x + (hasIcon ? 24 : 10);
        if (hasIcon) {
            g.renderFakeItem(iconItem, x + 5, y + (h - 16) / 2);
        }
        int textColor = active ? 0xFFFFFFFF : (hover ? 0xFFE2E8F0 : 0xFF94A3B8);
        String shown = label;
        int maxW = w - (textX - x) - 6;
        if (font.width(shown) > maxW) {
            shown = font.plainSubstrByWidth(shown, Math.max(8, maxW - 6)) + "..";
        }
        drawText(g, font, shown, textX, y + (h - 8) / 2, textColor, active);
        if (active) {
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, (accentColor & 0x00FFFFFF) | 0x50000000);
        }
    }

    /**
     * Header nhóm trong dropdown (vd: QUẶNG QUÝ HIẾM / QUẶNG THƯỜNG).
     */
    public static void drawGroupHeader(GuiGraphics g, Font font, String text, int x, int y, int w, int accentColor) {
        drawText(g, font, text, x + 2, y + 2, accentColor, false);
        int lineY = y + 11;
        g.fill(x + 2 + font.width(text) + 6, lineY, x + w - 2, lineY + 1, 0x1EFFFFFF);
    }

    /**
     * Row module full-width kiểu LB: nền + viền hairline + laser trên khi active/hover.
     * Phần dropdown body (nếu expanded) do caller vẽ tiếp bên dưới bằng drawDropdownBody.
     */
    public static void drawModuleRow(GuiGraphics g, int x, int y, int w, int h,
                                     int accentColor, boolean active, boolean hover, boolean expanded) {
        int bgTop = hover ? 0xEE142136 : (active ? 0xE60D1D33 : 0xCC0B1220);
        int bgBottom = hover ? 0xFA0D1728 : (active ? 0xF2081424 : 0xE0060B16);
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBottom);
        int border = hover ? ((accentColor & 0x00FFFFFF) | 0x90000000)
                : (active ? ((accentColor & 0x00FFFFFF) | 0x60000000) : BORDER_ROW);
        drawOutline(g, x, y, w, h, border);
        // Vạch đứng trái
        g.fill(x + 1, y + 2, x + 3, y + h - 2,
                active ? accentColor : (hover ? ((accentColor & 0x00FFFFFF) | 0x80000000) : 0x3064748B));
        // Laser trên khi active/hover
        if (active || hover) {
            int laserAlpha = active ? 0xFF000000 : 0x95000000;
            g.fill(x + 2, y, x + w - 2, y + 1, (accentColor & 0x00FFFFFF) | laserAlpha);
        }
        // Viền dưới nối dropdown khi expanded
        if (expanded) {
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, (accentColor & 0x00FFFFFF) | 0x40000000);
        }
    }

    /**
     * Thân dropdown mở rộng bên dưới row: nền tối sâu + viền 2 bên + đáy.
     */
    public static void drawDropdownBody(GuiGraphics g, int x, int y, int w, int h, int accentColor) {
        g.fill(x, y, x + w, y + h, BG_DROPDOWN);
        int edge = (accentColor & 0x00FFFFFF) | 0x35000000;
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
    }

    /**
     * Mũi tên expand ▼/▲ vẽ bằng text để nhẹ, hitbox do caller quản lý.
     */
    public static void drawExpandArrow(GuiGraphics g, Font font, int x, int y, boolean expanded, boolean hover, int accentColor) {
        String arrow = expanded ? "▾" : "▸";
        int col = hover ? 0xFFFFFFFF : (expanded ? accentColor : 0xFF94A3B8);
        drawText(g, font, arrow, x, y, col, hover);
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
            // Highlight phản quang trên nửa trên
            g.fill(x, y, x + fillW, y + 1, 0x50FFFFFF);
        }
    }

    /**
     * Vẽ nút bấm hành động (Action Button) chuẩn Hero CTA với gradient và specular shine.
     */
    public static void drawActionButton(GuiGraphics g, Font font, ItemStack iconItem, String text, int x, int y, int w, int h, int accentColor, boolean hover) {
        int bgTop = hover ? ((accentColor & 0x00FFFFFF) | 0x85000000) : 0xB80E1729;
        int bgBottom = hover ? ((accentColor & 0x00FFFFFF) | 0xC5000000) : 0xDD070C16;
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBottom);

        int borderColor = hover ? accentColor : ((accentColor & 0x00FFFFFF) | 0x50000000);
        drawOutline(g, x, y, w, h, borderColor);

        // Specular highlight trên đỉnh nút
        int shineAlpha = hover ? 0x80FFFFFF : 0x22FFFFFF;
        g.fill(x + 1, y + 1, x + w - 1, y + 2, shineAlpha);

        if (hover) {
            // Vạch laser trên cùng khi hover
            g.fill(x + 2, y, x + w - 2, y + 1, accentColor);
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
                int totalW = 16 + 5 + textW;
                int startX = x + (w - totalW) / 2;
                g.renderFakeItem(iconItem, startX, iconY);
                int textY = y + (h - 8) / 2;
                drawText(g, font, text, startX + 21, textY, textColor, hover);
            }
        } else {
            int textX = x + (w - textW) / 2;
            int textY = y + (h - 8) / 2;
            drawText(g, font, text, textX, textY, textColor, hover);
        }
    }
}
