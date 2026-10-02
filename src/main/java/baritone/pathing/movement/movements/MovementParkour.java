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
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.pathing.movement.MovementState.MovementTarget;
import baritone.utils.BlockStateInterface;
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;

import java.util.HashSet;
import java.util.Set;

public class MovementParkour extends Movement {

    private static final BetterBlockPos[] EMPTY = new BetterBlockPos[]{};

    private final Direction direction;
    private final int dist;
    private final boolean ascend;
    private int ticksWithoutPlacement = 0;

    private MovementParkour(IBaritone baritone, BetterBlockPos src, int dist, Direction dir, boolean ascend) {
        super(baritone, src, src.relative(dir, dist).above(ascend ? 1 : 0), EMPTY, src.relative(dir, dist).below(ascend ? 0 : 1));
        this.direction = dir;
        this.dist = dist;
        this.ascend = ascend;
    }

    @Override
    public void reset() {
        super.reset();
        ticksWithoutPlacement = 0;
    }

    public static MovementParkour cost(CalculationContext context, BetterBlockPos src, Direction direction) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, direction, res);
        int dist = Math.abs(res.x - src.x) + Math.abs(res.z - src.z);
        return new MovementParkour(context.getBaritone(), src, dist, direction, res.y > src.y);
    }

    public static void cost(CalculationContext context, int x, int y, int z, Direction dir, MutableMoveResult res) {
        if (context.crawlMode || !context.allowParkour) {
            return;
        }
        if (!context.allowJumpAtBuildLimit && y >= context.world.getMaxY()) {
            return;
        }
        int xDiff = dir.getStepX();
        int zDiff = dir.getStepZ();
        if (!MovementHelper.fullyPassable(context, x + xDiff, y, z + zDiff)) {
            // most common case at the top -- the adjacent block isn't air
            return;
        }
        BlockState adj = context.get(x + xDiff, y - 1, z + zDiff);
        if (MovementHelper.canWalkOn(context, x + xDiff, y - 1, z + zDiff, adj)) { // don't parkour if we could just traverse (for now)
            // second most common case -- we could just traverse not parkour
            return;
        }
        if (MovementHelper.avoidWalkingInto(adj) && !(adj.getFluidState().getType() instanceof WaterFluid)) { // magma sucks
            return;
        }
        if (!MovementHelper.fullyPassable(context, x + xDiff, y + 1, z + zDiff)) {
            return;
        }
        if (!MovementHelper.fullyPassable(context, x + xDiff, y + 2, z + zDiff)) {
            return;
        }
        if (!MovementHelper.fullyPassable(context, x, y + 2, z)) {
            return;
        }
        BlockState standingOn = context.get(x, y - 1, z);
        if (MovementHelper.isClimbable(standingOn.getBlock()) || standingOn.getBlock() instanceof StairBlock || MovementHelper.isBottomSlab(standingOn)) {
            return;
        }
        // we can't jump from (frozen) water with assumeWalkOnWater because we can't be sure it will be frozen
        if (context.assumeWalkOnWater && !standingOn.getFluidState().isEmpty()) {
            return;
        }
        if (!context.get(x, y, z).getFluidState().isEmpty()) {
            return; // can't jump out of water
        }
        int maxJump;
        if (context.allowWalkOnMagmaBlocks && standingOn.is(Blocks.MAGMA_BLOCK)) {
            maxJump = 2;
        } else if (standingOn.getBlock() == Blocks.SOUL_SAND) {
            maxJump = 2; // 1 block gap
        } else if (context.canSprint) {
            maxJump = 4;
        } else {
            maxJump = 2; // Đi bộ bình thường chỉ nhảy tối đa qua khoảng cách 2 block (hố 1 block), không thể nhảy 3 block nếu không sprint
        }

        // check parkour jumps from smallest to largest for obstacles/walls and landing positions
        int verifiedMaxJump = 1; // i - 1 (when i = 2)
        for (int i = 2; i <= maxJump; i++) {
            int destX = x + xDiff * i;
            int destZ = z + zDiff * i;

            // check head/feet
            if (!MovementHelper.fullyPassable(context, destX, y + 1, destZ)) {
                break;
            }
            if (!MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                break;
            }

            // check for ascend landing position
            BlockState destInto = context.bsi.get0(destX, y, destZ);
            if (!MovementHelper.fullyPassable(context, destX, y, destZ, destInto)) {
                if (i <= 3 && context.allowParkourAscend && context.canSprint && MovementHelper.canWalkOn(context, destX, y, destZ, destInto) && checkOvershootSafety(context.bsi, destX + xDiff, y + 1, destZ + zDiff)) {
                    res.x = destX;
                    res.y = y + 1;
                    res.z = destZ;
                    res.cost = i * SPRINT_ONE_BLOCK_COST + context.jumpPenalty;
                    return;
                }
                break;
            }

            // check for flat landing position
            BlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            // farmland needs to be canWalkOn otherwise farm can never work at all, but we want to specifically disallow ending a jump on farmland haha
            // frostwalker works here because we can't jump from possibly unfrozen water
            if ((landingOn.getBlock() != Blocks.FARMLAND && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn))
                    || (Math.min(16, context.frostWalker + 2) >= i && MovementHelper.canUseFrostWalker(context, landingOn))
            ) {
                if (checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                    res.x = destX;
                    res.y = y;
                    res.z = destZ;
                    res.cost = costFromJumpDistance(i) + context.jumpPenalty;
                    return;
                }
                break;
            }

            if (!MovementHelper.fullyPassable(context, destX, y + 3, destZ)) {
                break;
            }

            verifiedMaxJump = i;
        }

        // parkour place starts here
        if (!context.allowParkourPlace) {
            return;
        }
        // check parkour jumps from largest to smallest for positions to place blocks
        for (int i = verifiedMaxJump; i > 1; i--) {
            int destX = x + i * xDiff;
            int destZ = z + i * zDiff;
            BlockState toReplace = context.get(destX, y - 1, destZ);
            double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, toReplace);
            if (placeCost >= COST_INF) {
                continue;
            }
            if (!MovementHelper.isReplaceable(destX, y - 1, destZ, toReplace, context.bsi)) {
                continue;
            }
            if (!checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                continue;
            }
            for (int j = 0; j < 5; j++) {
                int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[j].getStepX();
                int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[j].getStepY();
                int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[j].getStepZ();
                if (againstX == destX - xDiff && againstZ == destZ - zDiff) { // we can't turn around that fast
                    continue;
                }
                if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                    res.x = destX;
                    res.y = y;
                    res.z = destZ;
                    res.cost = costFromJumpDistance(i) + placeCost + context.jumpPenalty;
                    return;
                }
            }
        }
    }

    private static boolean checkOvershootSafety(BlockStateInterface bsi, int x, int y, int z) {
        // we're going to walk into these two blocks after the landing of the parkour anyway, so make sure they aren't avoidWalkingInto
        return !MovementHelper.avoidWalkingInto(bsi.get0(x, y, z)) && !MovementHelper.avoidWalkingInto(bsi.get0(x, y + 1, z));
    }

    private static double costFromJumpDistance(int dist) {
        switch (dist) {
            case 2:
                return WALK_ONE_BLOCK_COST * 2; // IDK LOL
            case 3:
                return WALK_ONE_BLOCK_COST * 3;
            case 4:
                return SPRINT_ONE_BLOCK_COST * 4;
            default:
                throw new IllegalStateException("LOL " + dist);
        }
    }


    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, direction, res);
        if (res.x != dest.x || res.y != dest.y || res.z != dest.z) {
            return COST_INF;
        }
        return res.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        Set<BetterBlockPos> set = new HashSet<>();
        for (int i = 0; i <= dist; i++) {
            for (int y = 0; y < 2; y++) {
                set.add(src.relative(direction, i).above(y));
            }
        }
        return set;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // once this movement is instantiated, the state is default to PREPPING
        // but once it's ticked for the first time it changes to RUNNING
        // since we don't really know anything about momentum, it suffices to say Parkour can only be canceled on the 0th tick
        return state.getStatus() != MovementStatus.RUNNING;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        double px = ctx.player().position().x;
        double py = ctx.player().position().y;
        double pz = ctx.player().position().z;

        if (py < src.y - 0.75D || (ctx.playerFeet().y < src.y && ctx.player().onGround())) {
            // we have fallen
            logDebug("Parkour failed: fallen below takeoff level");
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        Block d = BlockStateInterface.getBlock(ctx, dest);
        boolean ladderOrVine = (d == Blocks.VINE || d == Blocks.LADDER);
        boolean isLandingBlockThere = MovementHelper.canWalkOn(ctx, positionToPlace)
                || (ascend && MovementHelper.canWalkOn(ctx, dest))
                || ladderOrVine
                || MovementHelper.canUseFrostWalker(ctx, positionToPlace);

        double dirX = direction.getStepX();
        double dirZ = direction.getStepZ();
        double srcCenterX = src.x + 0.5D;
        double srcCenterZ = src.z + 0.5D;
        double destCenterX = dest.x + 0.5D;
        double destCenterZ = dest.z + 0.5D;

        // Tiến độ di chuyển dọc theo hướng nhảy (0.0 = tâm src, 0.5 = mép src, > 0.5 = vượt ra ngoài hố)
        double progress = (px - srcCenterX) * dirX + (pz - srcCenterZ) * dirZ;
        // Độ lệch ngang so với trục nhảy thẳng (dương/âm là lệch trái/phải)
        double lateral = (px - srcCenterX) * (-dirZ) + (pz - srcCenterZ) * dirX;
        // Khoảng cách còn lại tới tâm dest theo hướng nhảy
        double toDest = (destCenterX - px) * dirX + (destCenterZ - pz) * dirZ;

        // 1. KIỂM TRA ĐIỂM ĐÁP VÀ ĐẶT BLOCK
        if (!isLandingBlockThere) {
            // Nếu block đáp chưa có và bot vẫn còn ở src, kiểm tra xem có thể đặt block trước khi nhảy không
            if (ctx.player().onGround() && progress < 0.5D) {
                if (dist == 2 && Baritone.settings().allowPlace.value && ((Baritone) baritone).getInventoryBehavior().hasGenericThrowaway()) {
                    ticksWithoutPlacement++;
                    if (ticksWithoutPlacement <= 20) {
                        MovementHelper.PlaceResult p = MovementHelper.attemptToPlaceABlock(state, baritone, positionToPlace, false, true);
                        if (p == MovementHelper.PlaceResult.READY_TO_PLACE) {
                            state.setInput(Input.SNEAK, true);
                            state.setInput(Input.MOVE_FORWARD, false);
                            state.setInput(Input.CLICK_RIGHT, true);
                            return state;
                        } else if (p == MovementHelper.PlaceResult.ATTEMPTING) {
                            state.setInput(Input.SNEAK, true);
                            state.setInput(Input.MOVE_FORWARD, false);
                            return state;
                        }
                    }
                }
                // Nếu không thể đặt trước, tuyệt đối không nhảy vào khoảng không/vực/lava
                logDebug("Landing block does not exist and cannot be placed before jump. Aborting parkour.");
                state.setInput(Input.JUMP, false);
                state.setInput(Input.SPRINT, false);
                state.setInput(Input.MOVE_FORWARD, false);
                return state.setStatus(MovementStatus.UNREACHABLE);
            }
            // Nếu đang trên không trung (midair) và allowPlace bật: thử đặt clutch
            if (!ctx.player().onGround() && Baritone.settings().allowPlace.value && ((Baritone) baritone).getInventoryBehavior().hasGenericThrowaway()) {
                if (MovementHelper.attemptToPlaceABlock(state, baritone, dest.below(), true, false) == MovementHelper.PlaceResult.READY_TO_PLACE) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
        }

        // 2. TIẾP ĐẤT & KIỂM TRA ĐÍCH (LANDING PHASE)
        boolean onDestFeet = ctx.playerFeet().equals(dest);
        boolean inLandingBox = toDest <= 0.35D && toDest >= -0.4D && ctx.player().onGround() && Math.abs(lateral) < 0.45D;
        if (onDestFeet || inLandingBox) {
            state.setInput(Input.JUMP, false);
            if (ladderOrVine) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            if (py - ctx.playerFeet().getY() < 0.094D) { // lilypads
                return state.setStatus(MovementStatus.SUCCESS);
            }

            // Kiểm tra xem phía trước block đích có đường băng tiếp nối không
            BetterBlockPos nextPos = dest.relative(direction);
            boolean hasRunwayAhead = MovementHelper.canWalkOn(ctx, nextPos.below());

            // Nếu phía trước có đường băng (như các nhịp 2 block trong Emerald Parkour):
            // GIỮ NGUYÊN ĐÀ CHẠY NƯỚC RÚT (SPRINT), KHÔNG ĐƯỢC PHANH HOẶC SNEAK
            if (hasRunwayAhead) {
                state.setInput(Input.MOVE_FORWARD, true);
                if (ctx.player().getFoodData().getFoodLevel() > 6) {
                    state.setInput(Input.SPRINT, true);
                }
                if (ctx.player().onGround()) {
                    return state.setStatus(MovementStatus.SUCCESS);
                }
                return state;
            }

            // Nếu là trụ đơn độc lập (xung quanh là khoảng không):
            // Chỉ phanh nhẹ khi tiến quá sát mép trước để chống trượt rơi khỏi trụ
            state.setInput(Input.SPRINT, false);
            if (toDest < 0.15D && ctx.player().getDeltaMovement().horizontalDistance() > 0.10D) {
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.SNEAK, true);
            } else {
                state.setInput(Input.MOVE_FORWARD, false);
            }
            if (ctx.player().onGround()) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            return state;
        }

        // 3. ĐANG TRÊN KHÔNG TRUNG (AIRBORNE PHASE)
        if (!ctx.player().onGround()) {
            state.setInput(Input.JUMP, false);
            state.setInput(Input.MOVE_FORWARD, true);
            if (ctx.player().getFoodData().getFoodLevel() > 6) {
                state.setInput(Input.SPRINT, true);
            }
            MovementHelper.moveTowards(ctx, state, dest);
            return state;
        }

        // 4. TRÊN MẶT ĐẤT LẤY ĐÀ VÀ DẬM NHẢY (TAKEOFF PHASE)
        if (ctx.player().getFoodData().getFoodLevel() > 6) {
            state.setInput(Input.SPRINT, true);
        }
        state.setInput(Input.MOVE_FORWARD, true);
        MovementHelper.moveTowards(ctx, state, dest);

        // ĐIỂM DẬM NHẢY CHUẨN XÁC (Late-edge Takeoff):
        // - dist == 2 (hố 1 block): dậm nhảy ở progress >= 0.28D
        // - dist >= 3 (hố 2 block hoặc 3 block sprint-jump):
        //   BẮT BUỘC phải lấy đủ gia tốc sprint và dậm nhảy ở sát mép block (progress >= 0.38D)
        //   để đạt 100% tầm bay, không bị rơi non vào mép block đích!
        double takeoffThreshold = dist >= 3 ? 0.38D : 0.28D;

        if (progress >= takeoffThreshold || !ctx.playerFeet().equals(src)) {
            state.setInput(Input.JUMP, true);
        } else {
            state.setInput(Input.JUMP, false);
        }

        return state;
    }
}
