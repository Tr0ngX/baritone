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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class MixinPlayerRenderer {

    /**
     * Ẩn hoàn toàn nametag (tên trên đầu nhân vật) của chính người chơi khi ở góc nhìn F5.
     */
    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onShouldShowName(LivingEntity entity, double dist, CallbackInfoReturnable<Boolean> cir) {
        if (StreamerUtil.isHidePlayerNameActive()) {
            if (entity == Minecraft.getInstance().player) {
                cir.setReturnValue(false);
            }
        }
    }
}
