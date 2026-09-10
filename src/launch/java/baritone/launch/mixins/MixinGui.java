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

import baritone.utils.StreamerUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    /**
     * Ẩn hoàn toàn bảng Scoreboard Sidebar (Bảng điểm bên phải màn hình).
     */
    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (StreamerUtil.isHideScoreboardActive()) {
            ci.cancel();
        }
    }

    /**
     * Ẩn displayScoreboardSidebar nếu được gọi trực tiếp.
     */
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onDisplayScoreboardSidebar(GuiGraphics guiGraphics, Objective objective, CallbackInfo ci) {
        if (StreamerUtil.isHideScoreboardActive()) {
            ci.cancel();
        }
    }

    /**
     * Che tên người chơi nếu xuất hiện trên Actionbar / Overlay Message,
     * đồng thời quét cập nhật số dư/tiền tệ cho Discord Webhook.
     */
    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component onSetOverlayMessage(Component message) {
        if (message != null) {
            try {
                baritone.utils.DiscordManager.updateBalanceIfDetected(message.getString());
            } catch (Throwable ignored) {}
        }
        return StreamerUtil.censorComponent(message);
    }

    /**
     * Chế độ Botting (Màn hình đen):
     * Khi bottingMode bật và không có yêu cầu chụp ảnh Webhook,
     * vẽ toàn bộ màn hình màu đen tuyền (0xFF000000) và vẽ bảng điều khiển BottingDashboardOverlay
     * nếu người chơi không mở Screen nào (F4 ClickGUI, ESC, Inventory).
     * Huỷ bỏ hoàn toàn render HUD vanilla để tiết kiệm triệt để CPU/draw calls.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderHead(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (baritone.api.BaritoneAPI.getSettings().bottingMode.value && !baritone.utils.CleanScreenshotHelper.isCaptureRequested()) {
            guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0xFF000000);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen == null && mc.font != null) {
                try {
                    baritone.utils.hud.BottingDashboardOverlay.render(guiGraphics, mc);
                } catch (Throwable ignored) {}
            }
            ci.cancel();
        }
    }

    /**
     * Vẽ HUD đào quặng độc lập (OreHudOverlay) lên màn hình.
     * Overlay tự ẩn khi tắt GUI (F1), mở debug (F3), tắt thống kê hoặc không mining.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.font != null) {
            try {
                baritone.utils.hud.OreHudOverlay.getInstance().render(guiGraphics, mc.font);
            } catch (Throwable ignored) {}
        }
        if (baritone.utils.CleanScreenshotHelper.isReadyForCapture()) {
            try {
                baritone.utils.CleanScreenshotHelper.captureOnRenderThread(mc.getMainRenderTarget());
            } catch (Throwable ignored) {}
        }
    }
}
