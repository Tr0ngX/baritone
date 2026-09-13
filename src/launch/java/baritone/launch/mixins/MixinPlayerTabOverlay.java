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

import baritone.utils.DiscordManager;
import baritone.utils.StreamerUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {

    @Shadow
    private Component header;

    @Shadow
    private Component footer;

    @Inject(method = "setHeader", at = @At("RETURN"))
    private void onSetHeader(Component component, CallbackInfo ci) {
        if (component != null) {
            DiscordManager.updateTabListHeader(component);
        }
    }

    @Inject(method = "setFooter", at = @At("RETURN"))
    private void onSetFooter(Component component, CallbackInfo ci) {
        if (component != null) {
            DiscordManager.updateTabListFooter(component);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics guiGraphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (this.header != null) {
            DiscordManager.updateTabListHeader(this.header);
        }
        if (this.footer != null) {
            DiscordManager.updateTabListFooter(this.footer);
        }
    }

    /**
     * Che tên người chơi trên bảng danh sách người chơi (Tab list).
     */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void onGetNameForDisplay(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (StreamerUtil.isHidePlayerNameActive() && playerInfo != null && playerInfo.getProfile() != null) {
            String localName = StreamerUtil.getLocalPlayerName();
            if (!localName.isEmpty() && localName.equalsIgnoreCase(playerInfo.getProfile().name())) {
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
