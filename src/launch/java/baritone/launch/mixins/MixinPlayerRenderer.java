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
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class MixinPlayerRenderer {

    /**
     * Ẩn hoàn toàn nametag (tên trên đầu nhân vật) của chính người chơi khi ở góc nhìn F5.
     */
    @Inject(
            method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderNameTag(PlayerRenderState state, Component component, PoseStack poseStack, MultiBufferSource buffer, int i, CallbackInfo ci) {
        if (StreamerUtil.isHidePlayerNameActive()) {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null && state != null) {
                if (state.id == localPlayer.getId()) {
                    ci.cancel();
                    return;
                }
            }
            String localName = StreamerUtil.getLocalPlayerName();
            if (!localName.isEmpty() && state != null && state.name != null && localName.equalsIgnoreCase(state.name)) {
                ci.cancel();
            }
        }
    }
}
