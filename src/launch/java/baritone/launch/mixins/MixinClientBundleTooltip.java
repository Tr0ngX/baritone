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

import baritone.utils.BundleTooltipHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientBundleTooltip.class)
public abstract class MixinClientBundleTooltip {

    @Shadow
    @Final
    private BundleContents contents;

    @Inject(
            method = "drawSelectedItemTooltip",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDrawSelectedItemTooltip(Font font, GuiGraphics guiGraphics, int x, int y, int width, CallbackInfo ci) {
        if (!BundleTooltipHelper.isEnabled()) {
            return;
        }

        if (this.contents != null && this.contents.hasSelectedItem()) {
            int selectedIdx = this.contents.getSelectedItem();
            ItemStack itemStack = this.contents.getItemUnsafe(selectedIdx);
            if (itemStack != null && !itemStack.isEmpty()) {
                BundleTooltipHelper.renderSelectedItemFullTooltip(font, guiGraphics, x, y, width, itemStack);
                ci.cancel();
            }
        }
    }
}
