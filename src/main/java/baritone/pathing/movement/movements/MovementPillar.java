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

package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import baritone.api.utils.Helper;
import java.util.Set;

public class MovementPillar extends Movement {

    private int pillarTicks = 0;

    public MovementPillar(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
        super(baritone, start, end, new BetterBlockPos[]{start.above(2)}, start);
    }

    @Override
    public void reset() {
        super.reset();
        pillarTicks = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static double cost(CalculationContext context, int x, int y, int z) {
        if (context.crawlMode || context.noPillar) {
            return COST_INF;
        }
        BlockState fromState = context.get(x, y, z);
        Block from = fromState.getBlock();
        boolean ladder = MovementHelper.isClimbable(from);
        BlockState fromDown = context.get(x, y - 1, z);
        if (!ladder) {
            if (MovementHelper.isClimbable(fromDown.getBlock())) {
                return COST_INF; // can't pillar from a ladder or vine onto something that isn't also climbable
            }
            if (fromDown.getBlock() instanceof SlabBlock && fromDown.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                return COST_INF; // can't pillar up from a bottom slab onto a non ladder
            }
        }
        BlockState toBreak = context.get(x, y + 2, z);
        Block toBreakBlock = toBreak.getBlock();
        if (toBreakBlock instanceof FenceGateBlock) { // see issue #172
            return COST_INF;
        }
        BlockState srcUp = null;
        if (MovementHelper.isWater(toBreak) && MovementHelper.isWater(fromState)) { // TODO should this also be allowed if toBreakBlock is air?
            srcUp = context.get(x, y + 1, z);
            if (MovementHelper.isWater(srcUp)) {
                return LADDER_UP_ONE_COST; // allow ascending pillars of water, but only if we're already in one
            }
        }
        double placeCost = 0;
        if (!ladder) {
            // we need to place a block where we started to jump on it
            placeCost = context.costOfPlacingAt(x, y, z, fromState);
            if (placeCost >= COST_INF) {
                return COST_INF;
            }
            if (fromDown.getBlock() instanceof AirBlock) {
                placeCost += 0.1; // slightly (1/200th of a second) penalize pillaring on what's currently air
            }
        }
        if ((MovementHelper.isLiquid(fromState) && !MovementHelper.canPlaceAgainst(context.bsi, x, y - 1, z, fromDown)) || (MovementHelper.isLiquid(fromDown) && context.assumeWalkOnWater)) {
            // otherwise, if we're standing in water, we cannot pillar
            // if we're standing on water and assumeWalkOnWater is true, we cannot pillar
            // if we're standing on water and assumeWalkOnWater is false, we must have ascended to here, or sneak backplaced, so it is possible to pillar again
            return COST_INF;
        }
        if ((from == Blocks.LILY_PAD || from instanceof CarpetBlock) && !fromDown.getFluidState().isEmpty()) {
            // to ascend here we'd have to break the block we are standing on
            return COST_INF;
        }
        double hardness = MovementHelper.getMiningDurationTicks(context, x, y + 2, z, toBreak, true);
        if (hardness >= COST_INF) {
            return COST_INF;
        }
        if (hardness != 0) {
            if (MovementHelper.isClimbable(toBreakBlock)) {
                hardness = 0; // we won't actually need to break the ladder / vine because we're going to use it
            } else {
                BlockState check = context.get(x, y + 3, z); // the block on top of the one we're going to break, could it fall on us?
                if (check.getBlock() instanceof FallingBlock) {
                    // see MovementAscend's identical check for breaking a falling block above our head
                    if (srcUp == null) {
                        srcUp = context.get(x, y + 1, z);
                    }
                    if (!(toBreakBlock instanceof FallingBlock) || !(srcUp.getBlock() instanceof FallingBlock)) {
                        return COST_INF;
                    }
                }
                // this is commented because it may have had a purpose, but it's very unclear what it was. it's from the minebot era.
                //if (!MovementHelper.canWalkOn(context, chkPos, check) || MovementHelper.canWalkThrough(context, chkPos, check)) {//if the block above where we want to break is not a full block, don't do it
                // TODO why does canWalkThrough mean this action is COST_INF?
                // FallingBlock makes sense, and !canWalkOn deals with weird cases like if it were lava
                // but I don't understand why canWalkThrough makes it impossible
                //    return COST_INF;
                //}
            }
        }
        if (ladder) {
            return LADDER_UP_ONE_COST + hardness * 5;
        } else {
            return JUMP_ONE_BLOCK_COST + placeCost + context.jumpPenalty + hardness;
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.playerFeet().y < src.y) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        BlockState fromDown = BlockStateInterface.get(ctx, src);
        if (MovementHelper.isWater(fromDown) && MovementHelper.isWater(ctx, dest)) {
            // stay centered while swimming up a water column
            state.setTarget(new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.getBlockPosCenter(dest), ctx.playerRotations()), false));
            Vec3 destCenter = VecUtils.getBlockPosCenter(dest);
            if (Math.abs(ctx.player().position().x - destCenter.x) > 0.2 || Math.abs(ctx.player().position().z - destCenter.z) > 0.2) {
                state.setInput(Input.MOVE_FORWARD, true);
            }
            if (ctx.playerFeet().equals(dest)) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            return state;
        }
        boolean ladder = MovementHelper.isClimbable(fromDown.getBlock());

        Rotation rotation = RotationUtils.calcRotationFromVec3d(ctx.playerHead(),
                VecUtils.getBlockPosCenter(positionToPlace),
                ctx.playerRotations());
        if (!ladder) {
            state.setTarget(new MovementState.MovementTarget(ctx.playerRotations().withPitch(rotation.getPitch()), true));
        }

        boolean blockIsThere = MovementHelper.canWalkOn(ctx, src) || ladder;
        if (ladder) {
            if (ctx.playerFeet().equals(dest)) {
                return state.setStatus(MovementStatus.SUCCESS);
            }

            MovementHelper.moveTowards(ctx, state, dest);
            state.setInput(Input.JUMP, true);
            return state;
        } else {
            pillarTicks++;
            if (pillarTicks > 80) {
                // Kẹt hành động pillar quá 80 tick (4 giây) mà không leo lên được
                logDebug("MovementPillar timeout (" + pillarTicks + " ticks). Failing movement.");
                return state.setStatus(MovementStatus.FAILED);
            }

            // 1. Kiểm tra xem có trần hầm cản trở nhảy lên không (đập đầu)
            BetterBlockPos ceiling = src.above(2);
            BlockState ceilingState = BlockStateInterface.get(ctx, ceiling);
            if (!MovementHelper.canWalkThrough(ctx, ceiling)) {
                var rotCeil = RotationUtils.reachable(ctx, ceiling, ctx.playerController().getBlockReachDistance());
                if (rotCeil.isPresent()) {
                    state.setTarget(new MovementState.MovementTarget(rotCeil.get(), true));
                    state.setInput(Input.JUMP, false);
                    MovementHelper.switchToBestToolFor(ctx, ceilingState);
                    state.setInput(Input.CLICK_LEFT, true);
                    return state;
                }
            }

            // 2. Chuẩn bị block xây dựng để kê chân
            if (!((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, src.x, src.y, src.z)) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            double diffX = ctx.player().position().x - (dest.getX() + 0.5);
            double diffZ = ctx.player().position().z - (dest.getZ() + 0.5);
            double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

            // 3. Căn chỉnh vị trí & Nhảy
            if (dist > 0.22 && ctx.player().onGround()) {
                // Đang đứng lệch tâm block -> Bước về tâm block trước khi nhảy
                state.setInput(Input.MOVE_FORWARD, true);
                state.setTarget(new MovementState.MovementTarget(rotation, true));
                state.setInput(Input.JUMP, false);
            } else {
                // Đã đứng trong phạm vi an toàn của block -> Nhìn thẳng xuống sàn và nhảy lên
                state.setTarget(new MovementState.MovementTarget(ctx.playerRotations().withPitch(90.0F), true));
                // Giữ phím nhảy trong suốt pha đi lên cho tới khi chân chạm ngưỡng dest.getY()
                state.setInput(Input.JUMP, ctx.player().position().y < dest.getY() || ctx.player().onGround());
            }

            // 4. Đặt block khi người chơi đã đạt tới đỉnh cú nhảy
            if (!blockIsThere) {
                BlockState frState = BlockStateInterface.get(ctx, src);
                Block fr = frState.getBlock();
                if (!(fr instanceof AirBlock || frState.canBeReplaced())) {
                    if (!MovementHelper.canWalkOn(ctx, src)) {
                        RotationUtils.reachable(ctx, src, ctx.playerController().getBlockReachDistance())
                                .map(rot -> new MovementState.MovementTarget(rot, true))
                                .ifPresent(state::setTarget);
                        state.setInput(Input.JUMP, false);
                        state.setInput(Input.CLICK_LEFT, true);
                        blockIsThere = false;
                    }
                } else if (ctx.player().position().y >= dest.getY() - 0.05
                        && (ctx.isLookingAt(src.below()) || ctx.isLookingAt(src) || ctx.playerRotations().getPitch() >= 80.0F)) {
                    state.setInput(Input.CLICK_RIGHT, true);
                    state.setInput(Input.JUMP, false);
                }
            }
        }

        // If we are at our goal and the block below us is placed
        if (ctx.playerFeet().equals(dest) && blockIsThere) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        return state;
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (ctx.playerFeet().equals(src) || ctx.playerFeet().equals(src.below())) {
            Block block = BlockStateInterface.getBlock(ctx, src.below());
            if (MovementHelper.isClimbable(block)) {
                state.setInput(Input.SNEAK, true);
            }
        }
        if (MovementHelper.isWater(ctx, dest.above())) {
            return true;
        }
        return super.prepared(state);
    }
}
