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

import baritone.api.BaritoneAPI;
import baritone.utils.CleanScreenshotHelper;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin khống chế FPS limit cho Chế độ Botting (Màn hình đen).
 * Giới hạn framerate của client về đúng 10 FPS (hoặc theo cấu hình bottingFps)
 * để giảm thiểu tối đa tải CPU và GPU khi cắm nhiều tài khoản botting cùng lúc.
 * Tự động bỏ qua giới hạn khi DiscordManager cần chụp ảnh màn hình sạch.
 */
@Mixin(FramerateLimitTracker.class)
public class MixinFramerateLimitTracker {

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void onGetFramerateLimit(CallbackInfoReturnable<Integer> cir) {
        if (BaritoneAPI.getSettings().bottingMode.value && !CleanScreenshotHelper.isCaptureRequested()) {
            int fps = BaritoneAPI.getSettings().bottingFps.value;
            cir.setReturnValue(fps > 0 ? fps : 10);
        }
    }
}
