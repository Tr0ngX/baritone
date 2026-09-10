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

import baritone.utils.ClickGuiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Chế độ "Chỉnh HUD": màn hình duy nhất được kéo-thả HUD.
 *
 * Không bắt chuột ngoài world hay ở GUI bất kỳ khác, đúng đặc tả.
 * Nút "Reset vị trí" ở đây chỉ reset vị trí, không xóa thống kê.
 */
public final class HudEditScreen extends Screen {

    private final Screen parent;
    private boolean dragging = false;
    private double grabDX;
    private double grabDY;

    // Nút đáy, tính lại mỗi frame theo chiều rộng màn hình.
    private int btnScaleX, btnCollapseX, btnResetX, btnDoneX;
    private int btnY, btnW = 86, btnH = 20;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Chỉnh HUD"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        OreHudOverlay.getInstance().setEditMode(false);
        OreHudOverlay.getInstance().getConfig().save();
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Nền tối nhẹ để thấy HUD đang nổi.
        graphics.fill(0, 0, this.width, this.height, 0x96000000);

        OreHudOverlay overlay = OreHudOverlay.getInstance();
        overlay.setEditMode(true);
        overlay.render(graphics, this.font);

        String hint = "Giữ chuột trái vào thanh tiêu đề HUD để kéo. HUD tự kẹp trong màn hình.";
        int hintX = (this.width - this.font.width(hint)) / 2;
        ClickGuiTheme.drawText(graphics, this.font, hint, hintX, 10, ClickGuiTheme.TEXT_MUTED, false);

        btnY = this.height - btnH - 12;
        int totalW = btnW * 4 + 12;
        int startX = (this.width - totalW) / 2;
        btnScaleX = startX;
        btnCollapseX = startX + btnW + 4;
        btnResetX = startX + (btnW + 4) * 2;
        btnDoneX = startX + (btnW + 4) * 3;

        String scaleLabel = overlay.getConfig().scaleLabel();
        String collapseLabel = overlay.getConfig().collapsed ? "Mở rộng" : "Thu gọn";
        drawButton(graphics, btnScaleX, scaleLabel, mouseX, mouseY, ClickGuiTheme.ACCENT_CYAN);
        drawButton(graphics, btnCollapseX, collapseLabel, mouseX, mouseY, ClickGuiTheme.ACCENT_BLUE);
        drawButton(graphics, btnResetX, "Reset vị trí", mouseX, mouseY, ClickGuiTheme.ACCENT_AMBER);
        drawButton(graphics, btnDoneX, "Xong", mouseX, mouseY, ClickGuiTheme.ACCENT_EMERALD);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void drawButton(GuiGraphics graphics, int x, String text, int mouseX, int mouseY, int accent) {
        boolean hover = mouseX >= x && mouseX <= x + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        ClickGuiTheme.drawActionButton(graphics, this.font, ItemStack.EMPTY, text, x, btnY, btnW, btnH, accent, hover);
    }

    private boolean onButton(int x, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        OreHudOverlay overlay = OreHudOverlay.getInstance();

        if (onButton(btnScaleX, mouseX, mouseY)) {
            overlay.getConfig().cycleScale();
            return true;
        }
        if (onButton(btnCollapseX, mouseX, mouseY)) {
            overlay.getConfig().collapsed = !overlay.getConfig().collapsed;
            overlay.getConfig().save();
            return true;
        }
        if (onButton(btnResetX, mouseX, mouseY)) {
            overlay.getConfig().resetPosition();
            return true;
        }
        if (onButton(btnDoneX, mouseX, mouseY)) {
            onClose();
            return true;
        }

        // Giữ chuột trái vào thanh tiêu đề HUD để kéo.
        int hx = overlay.getLastX();
        int hy = overlay.getLastY();
        int hw = overlay.getLastW();
        int headerH = Math.max(14, Math.round(16 * overlay.getConfig().scale));
        if (mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + headerH) {
            dragging = true;
            grabDX = mouseX - overlay.getConfig().x;
            grabDY = mouseY - overlay.getConfig().y;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            OreHudOverlay overlay = OreHudOverlay.getInstance();
            // Tọa độ GUI-scaled; chỉ lưu khi thả chuột hoặc đổi setting.
            overlay.getConfig().x = (int) Math.round(event.x() - grabDX);
            overlay.getConfig().y = (int) Math.round(event.y() - grabDY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            OreHudOverlay.getInstance().getConfig().save();
            return true;
        }
        return super.mouseReleased(event);
    }
}
