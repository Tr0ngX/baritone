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

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BlockPlaceHelper {
    // base ticks between places caused by tick logic
    private static final int BASE_PLACE_DELAY = 1;

    private final IPlayerContext ctx;
    private final Baritone baritone;
    private int rightClickTimer;

    BlockPlaceHelper(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
    }

    BlockPlaceHelper(IPlayerContext playerContext) {
        this.ctx = playerContext;
        this.baritone = (playerContext != null && playerContext.player() != null)
                ? (Baritone) BaritoneAPI.getProvider().getBaritoneForPlayer(playerContext.player())
                : null;
    }

    public void tick(boolean rightClickRequested) {
        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        HitResult mouseOver = ctx.objectMouseOver();
        if (!rightClickRequested || ctx.player().isHandsBusy() || mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }

        ItemStack mainHand = ctx.player().getMainHandItem();
        ItemStack offHand = ctx.player().getOffhandItem();

        // If neither hand has a BlockItem, try to auto-select a throwaway block from inventory
        if (!(mainHand.getItem() instanceof BlockItem) && !(offHand.getItem() instanceof BlockItem) && baritone != null) {
            BlockPos targetPos = ((BlockHitResult) mouseOver).getBlockPos();
            baritone.getInventoryBehavior().selectThrowawayForLocation(true, targetPos.getX(), targetPos.getY(), targetPos.getZ());
            mainHand = ctx.player().getMainHandItem();
            offHand = ctx.player().getOffhandItem();
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = mainHand;
        if (stack.isEmpty() || (!(stack.getItem() instanceof BlockItem) && offHand.getItem() instanceof BlockItem)) {
            hand = InteractionHand.OFF_HAND;
            stack = offHand;
        }

        if (stack.isEmpty()) {
            return;
        }

        // Never right-click a block with a mining/harvesting tool or weapon (pickaxe, axe, shovel, hoe, sword)
        // when attempting to place a block. Right-clicking blocks with tools strips logs, tills dirt,
        // or loses weapon charge without ever placing a block!
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.is(ItemTags.SWORDS)) {
            return;
        }

        rightClickTimer = Math.max(1, Baritone.settings().rightClickSpeed.value - BASE_PLACE_DELAY);

        InteractionResult result = ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), hand, (BlockHitResult) mouseOver);
        if (result != null && result.consumesAction()) {
            ctx.player().swing(hand);
            return;
        }

        // Only call processRightClick (use item in air) if holding a non-block item (e.g. water bucket)
        if (!(stack.getItem() instanceof BlockItem)) {
            InteractionResult handResult = ctx.playerController().processRightClick(ctx.player(), ctx.world(), hand);
            if (handResult != null && handResult.consumesAction()) {
                return;
            }
        }

        // Throttle failed or pending placement attempts by at least 2 ticks to prevent packet spam kicks
        rightClickTimer = Math.max(2, rightClickTimer);
    }
}
