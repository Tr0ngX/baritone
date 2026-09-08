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

package baritone.launch.mixins;

import baritone.utils.AutoLogoutTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen extends Screen {

    @Unique
    private Button baritoneCopyBtn;

    @Unique
    private Button baritoneTpBtn;

    @Unique
    private Button baritoneOpenPhotoBtn;

    @Unique
    private Button baritoneZoomBtn;

    protected MixinDisconnectedScreen(Component title) {
        super(title);
    }


    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!AutoLogoutTracker.hasLoggedOut()) {
            return;
        }

        // Tìm nút có sẵn của DisconnectedScreen (thường là nút Back to Server List hoặc To Title Screen)
        Button backButton = null;
        for (var child : this.children()) {
            if (child instanceof Button b) {
                backButton = b;
            }
        }

        int btnH = 20;
        int btnW = backButton != null ? Math.max(200, backButton.getWidth()) : 200;
        int btnX = backButton != null ? backButton.getX() : (this.width - btnW) / 2;

        int btnY;
        int totalExtraH = 2 * btnH + 4;
        if (backButton != null) {
            // Nút tiện ích đặt ngay DƯỚI nút Back to Server List đúng theo yêu cầu người dùng
            int desiredY = backButton.getY() + backButton.getHeight() + 6;
            // Kiểm tra responsive: nếu nút bị chạm hoặc tràn mép dưới màn hình (cách mép dưới < 6px)
            if (desiredY + totalExtraH > this.height - 6) {
                int shiftUp = (desiredY + totalExtraH) - (this.height - 6);
                backButton.setY(Math.max(10, backButton.getY() - shiftUp));
                btnY = backButton.getY() + backButton.getHeight() + 6;
            } else {
                btnY = desiredY;
            }
        } else {
            btnY = this.height - totalExtraH - 10;
        }

        // Chia đôi thành 2 hàng nút đối xứng thanh lịch ngay bên dưới nút Back:
        // Hàng 1: [📋 Chép XYZ (C)]  |  [📍 Chép /tp (T)]
        // Hàng 2: [📸 Mở Ảnh (O)]     |  [🔍 Phóng To (Z)]
        int halfW = (btnW - 4) / 2;
        int row1Y = btnY;
        int row2Y = btnY + btnH + 4;

        this.baritoneCopyBtn = Button.builder(
                Component.literal("§e📋 Chép XYZ §7(C)"),
                btn -> copyCoordsAction()
        ).bounds(btnX, row1Y, halfW, btnH).build();

        this.baritoneTpBtn = Button.builder(
                Component.literal("§b📍 Chép /tp §7(T)"),
                btn -> copyTpAction()
        ).bounds(btnX + halfW + 4, row1Y, btnW - halfW - 4, btnH).build();

        this.baritoneOpenPhotoBtn = Button.builder(
                Component.literal("§a📸 Mở Ảnh §7(O)"),
                btn -> AutoLogoutTracker.openScreenshotFile()
        ).bounds(btnX, row2Y, halfW, btnH).build();

        this.baritoneZoomBtn = Button.builder(
                Component.literal("§d🔍 Phóng To §7(Z)"),
                btn -> AutoLogoutTracker.setZoomed(!AutoLogoutTracker.isZoomed())
        ).bounds(btnX + halfW + 4, row2Y, btnW - halfW - 4, btnH).build();

        this.addRenderableWidget(this.baritoneCopyBtn);
        this.addRenderableWidget(this.baritoneTpBtn);
        this.addRenderableWidget(this.baritoneOpenPhotoBtn);
        this.addRenderableWidget(this.baritoneZoomBtn);
    }

    @Unique
    private void copyCoordsAction() {
        String coords = String.format(Locale.ROOT, "%.2f %.2f %.2f",
                AutoLogoutTracker.getLastX(), AutoLogoutTracker.getLastY(), AutoLogoutTracker.getLastZ());
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(coords);
            if (this.baritoneCopyBtn != null) {
                this.baritoneCopyBtn.setMessage(Component.literal("§a✔ Đã chép XYZ!"));
            }
        } catch (Throwable ignored) {}
    }

    @Unique
    private void copyTpAction() {
        String dim = AutoLogoutTracker.getLastDimension();
        String tpCommand = String.format(Locale.ROOT, "/execute in %s run tp @s %.2f %.2f %.2f",
                dim, AutoLogoutTracker.getLastX(), AutoLogoutTracker.getLastY(), AutoLogoutTracker.getLastZ());
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(tpCommand);
            if (this.baritoneTpBtn != null) {
                this.baritoneTpBtn.setMessage(Component.literal("§a✔ Đã chép /tp!"));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (AutoLogoutTracker.hasLoggedOut()) {
            if (AutoLogoutTracker.handleDisconnectedClick(event.x(), event.y(), event.button())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (AutoLogoutTracker.hasLoggedOut()) {
            if (AutoLogoutTracker.handleDisconnectedKey(event.key())) {
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_C) {
                copyCoordsAction();
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_T) {
                copyTpAction();
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
