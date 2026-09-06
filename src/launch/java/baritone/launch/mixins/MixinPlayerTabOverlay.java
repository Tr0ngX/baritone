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
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {

    /**
     * Che tên người chơi trên bảng danh sách người chơi (Tab list).
     */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void onGetNameForDisplay(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (StreamerUtil.isHidePlayerNameActive() && playerInfo != null && playerInfo.getProfile() != null) {
            String localName = StreamerUtil.getLocalPlayerName();
            if (!localName.isEmpty() && localName.equalsIgnoreCase(playerInfo.getProfile().getName())) {
                Component original = cir.getReturnValue();
                if (original != null) {
                    cir.setReturnValue(StreamerUtil.censorComponent(original));
                } else {
                    cir.setReturnValue(Component.literal(StreamerUtil.getCensoredName()));
                }
            }
        }
    }
}
