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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
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
        if (!rightClickRequested || ctx.player() == null || ctx.player().isHandsBusy()) {
            return;
        }

        HitResult mouseOver = ctx.objectMouseOver();
        if (mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult hit = (BlockHitResult) mouseOver;
        BlockPos targetPos = hit.getBlockPos();
        BlockState targetState = ctx.world().getBlockState(targetPos);

        // Check if target is an interactive block (button, lever, chest, door, trapdoor, or block entity)
        boolean isInteractable = targetState.is(BlockTags.BUTTONS)
                || targetState.is(BlockTags.DOORS)
                || targetState.is(BlockTags.TRAPDOORS)
                || targetState.getBlock() instanceof LeverBlock
                || targetState.getBlock() instanceof ChestBlock
                || targetState.hasBlockEntity();

        ItemStack mainHand = ctx.player().getMainHandItem();
        ItemStack offHand = ctx.player().getOffhandItem();

        boolean isBuilder = (baritone != null && baritone.getBuilderProcess().isActive());

        // When target is NOT an interactive block, we are trying to place a block.
        if (!isInteractable) {
            // Check if mainHand holds an acceptable block to place.
            // When not in BuilderProcess, ONLY acceptable throwaway items (deepslate, tuff) are allowed.
            boolean mainHandAllowed = isBuilder
                    ? (mainHand.getItem() instanceof BlockItem)
                    : (mainHand.getItem() instanceof BlockItem bi && Baritone.settings().acceptableThrowawayItems.value.contains(bi.asItem()));

            if (!mainHandAllowed && baritone != null) {
                baritone.getInventoryBehavior().selectThrowawayForLocation(true, targetPos.getX(), targetPos.getY(), targetPos.getZ());
            }
        }

        rightClickTimer = Math.max(0, Baritone.settings().rightClickSpeed.value - BASE_PLACE_DELAY);

        // Process right click on hands - fully compatible with vanilla interaction and bridging
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = ctx.player().getItemInHand(hand);

            if (!isInteractable) {
                // Avoid right-clicking with mining tools or weapons against regular blocks when trying to place
                if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)) {
                    continue;
                }

                // KHÔNG BAO GIỜ đặt bất kỳ khối nào khác ngoài deepslate và tuff (trừ khi builder process đang chạy)
                if (!isBuilder) {
                    if (!(stack.getItem() instanceof BlockItem bi) || !Baritone.settings().acceptableThrowawayItems.value.contains(bi.asItem())) {
                        continue;
                    }
                }
            }

            InteractionResult result = ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), hand, hit);
            if (result != null && result.consumesAction()) {
                ctx.player().swing(hand);
                return;
            }

            // Chỉ cho phép click vào không khí (processRightClick) nếu là khối tương tác đặc biệt
            if (!stack.isEmpty() && isInteractable) {
                InteractionResult handResult = ctx.playerController().processRightClick(ctx.player(), ctx.world(), hand);
                if (handResult != null && handResult.consumesAction()) {
                    return;
                }
            }
        }
    }
}
