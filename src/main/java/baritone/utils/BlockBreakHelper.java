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

import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {
    // base ticks between block breaks caused by tick logic
    private static final int BASE_BREAK_DELAY = 1;

    private final IPlayerContext ctx;
    private boolean wasHitting;
    private int breakDelayTimer = 0;

    BlockBreakHelper(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    public void stopBreakingBlock() {
        if (ctx.player() == null || ctx.minecraft().gameMode == null) {
            wasHitting = false;
            breakDelayTimer = 0;
            return;
        }
        IPlayerControllerMP controller = (IPlayerControllerMP) ctx.minecraft().gameMode;
        BlockPos current = controller.getCurrentBlock();

        // 1. Gui packet ABORT_DESTROY_BLOCK den server de server reset hoan toan trang thai dao block
        if (current != null && !current.equals(BlockPos.ZERO) && ctx.player().connection != null) {
            try {
                ctx.player().connection.send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        current,
                        Direction.DOWN
                ));
            } catch (Throwable ignored) {}
        }

        // 2. Xoa hieu ung vo block tren client level
        if (ctx.minecraft().level != null && current != null && !current.equals(BlockPos.ZERO)) {
            try {
                ctx.minecraft().level.destroyBlockProgress(ctx.player().getId(), current, -1);
            } catch (Throwable ignored) {}
        }

        // 3. Bat buoc set isDestroying = true truoc khi goi stopDestroyBlock de pass qua check if (this.isDestroying) cua Vanilla
        try {
            controller.setIsHittingBlock(true);
            ctx.playerController().resetBlockRemoving();
        } catch (Throwable ignored) {}

        // 4. Xoa triet de cac bien trang thai trong controller de khong bi ket hoac lech nhip
        try {
            controller.setIsHittingBlock(false);
            controller.setDestroyDelay(0);
            controller.setDestroyProgress(0.0F);
            controller.setCurrentBlock(new BlockPos(0, -1, 0));
        } catch (Throwable ignored) {}

        wasHitting = false;
        breakDelayTimer = 0;
    }

    public void tick(boolean isLeftClick) {
        if (breakDelayTimer > 0) {
            breakDelayTimer--;
            return;
        }
        HitResult trace = ctx.objectMouseOver();
        boolean isBlockTrace = trace != null && trace.getType() == HitResult.Type.BLOCK;

        if (isLeftClick && isBlockTrace) {
            ctx.playerController().setHittingBlock(wasHitting);
            if (ctx.playerController().hasBrokenBlock()) {
                ctx.playerController().syncHeldItem();
                ctx.playerController().clickBlock(((BlockHitResult) trace).getBlockPos(), ((BlockHitResult) trace).getDirection());
                ctx.player().swing(InteractionHand.MAIN_HAND);
            } else {
                if (ctx.playerController().onPlayerDamageBlock(((BlockHitResult) trace).getBlockPos(), ((BlockHitResult) trace).getDirection())) {
                    ctx.player().swing(InteractionHand.MAIN_HAND);
                }
                if (ctx.playerController().hasBrokenBlock()) { // block broken this tick
                    // break delay timer only applies for multi-tick block breaks like vanilla
                    breakDelayTimer = BaritoneAPI.getSettings().blockBreakSpeed.value - BASE_BREAK_DELAY;
                    // must reset controller's destroy delay to prevent the client from delaying itself unnecessarily
                    ((IPlayerControllerMP) ctx.minecraft().gameMode).setDestroyDelay(0);
                }
            }
            // if true, we're breaking a block. if false, we broke the block this tick
            wasHitting = !ctx.playerController().hasBrokenBlock();
            // this value will be reset by the MC client handling mouse keys
            // since we're not spoofing the click keybind to the client, the client will stop the break if isDestroyingBlock is true
            // we store and restore this value on the next tick to determine if we're breaking a block
            ctx.playerController().setHittingBlock(false);
        } else {
            if (wasHitting) {
                stopBreakingBlock();
            }
        }
    }
}
