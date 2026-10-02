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
import baritone.api.utils.RayTraceUtils;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

public class MovementTraverse extends Movement {

    /**
     * Did we have to place a bridge block or was it always there
     */
    private boolean wasTheBridgeBlockAlwaysThere = true;
    private int ticksWithoutPlacement = 0;

    public MovementTraverse(IBaritone baritone, BetterBlockPos from, BetterBlockPos to) {
        super(baritone, from, to, Baritone.settings().crawlMineMode.value ? new BetterBlockPos[]{to} : new BetterBlockPos[]{to.above(), to}, to.below());
    }

    @Override
    public void reset() {
        super.reset();
        wasTheBridgeBlockAlwaysThere = true;
        ticksWithoutPlacement = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest); // src.above means that we don't get caught in an infinite loop in water
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
        BlockState pb0 = context.get(destX, y + 1, destZ);
        BlockState pb1 = context.get(destX, y, destZ);
        BlockState destOn = context.get(destX, y - 1, destZ);
        BlockState srcDown = context.get(x, y - 1, z);
        Block srcDownBlock = srcDown.getBlock();
        boolean standingOnABlock = MovementHelper.mustBeSolidToWalkOn(context, x, y - 1, z, srcDown);
        boolean frostWalker = standingOnABlock && !context.assumeWalkOnWater && MovementHelper.canUseFrostWalker(context, destOn);
        if (frostWalker || MovementHelper.canWalkOn(context, destX, y - 1, destZ, destOn)) { //this is a walk, not a bridge
            double WC = WALK_ONE_BLOCK_COST;
            boolean water = false;
            boolean sneaking = false;
            if (MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1)) {
                WC = context.waterWalkSpeed;
                water = true;
            } else {
                if (destOn.getBlock() == Blocks.SOUL_SAND) {
                    WC += (WALK_ONE_OVER_SOUL_SAND_COST - WALK_ONE_BLOCK_COST) / 2;
                } else if (frostWalker) {
                    // with frostwalker we can walk on water without the penalty, if we are sure we won't be using jesus
                } else if (destOn.getBlock() == Blocks.WATER) {
                    WC += context.walkOnWaterOnePenalty;
                }
                if (srcDownBlock == Blocks.SOUL_SAND) {
                    WC += (WALK_ONE_OVER_SOUL_SAND_COST - WALK_ONE_BLOCK_COST) / 2;
                } else if (context.allowWalkOnMagmaBlocks && srcDownBlock.equals(Blocks.MAGMA_BLOCK)) {
                    sneaking = true;
                    WC += (SNEAK_ONE_BLOCK_COST - WALK_ONE_BLOCK_COST) / 2;
                }
            }
            double hardness1 = MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false);
            if (hardness1 >= COST_INF) {
                return COST_INF;
            }
            // Crawl mode: chỉ cần clear 1 block ngang chân (y), không cần clear block trên đầu (y+1)
            double hardness2 = context.crawlMode ? 0 : MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, pb0, true);
            if (hardness1 == 0 && hardness2 == 0) {
                if (!water && !sneaking && context.canSprint) {
                    // If there's nothing in the way, and this isn't water, and we aren't sneak placing
                    // We can sprint =D
                    // Don't check for soul sand, since we can sprint on that too
                    WC *= SPRINT_MULTIPLIER;
                    if (context.tunnelSprintJump && !context.crawlMode) {
                        BlockState ceil1 = context.get(x, y + 2, z);
                        BlockState ceil2 = context.get(destX, y + 2, destZ);
                        if (!ceil1.isAir() && (ceil1.blocksMotion() || MovementHelper.isBlockNormalCube(ceil1))
                                && !ceil2.isAir() && (ceil2.blocksMotion() || MovementHelper.isBlockNormalCube(ceil2))) {
                            WC *= 0.7; // Chạy nhảy hầm 2 block (bhop) nhanh hơn sprint thường
                        }
                    }
                }
                return WC;
            }
            if (MovementHelper.isClimbable(srcDownBlock)) {
                hardness1 *= 5;
                hardness2 *= 5;
            }
            return WC + hardness1 + hardness2;
        } else {//this is a bridge, so we need to place a block
            if (MovementHelper.isClimbable(srcDownBlock)) {
                return COST_INF;
            }
            if (Baritone.settings().neverBridgeOverLava.value) {
                if (MovementHelper.isLava(destOn)) {
                    return COST_INF;
                }
                for (int dy = 1; dy <= 6; dy++) {
                    if (MovementHelper.isLava(context.get(destX, y - 1 - dy, destZ))) {
                        return COST_INF;
                    }
                }
                for (int i = 0; i < 4; i++) {
                    Direction dir = Direction.from2DDataValue(i);
                    if (MovementHelper.isLava(context.get(destX + dir.getStepX(), y - 1, destZ + dir.getStepZ()))) {
                        return COST_INF;
                    }
                }
            }
            if (MovementHelper.isReplaceable(destX, y - 1, destZ, destOn, context.bsi)) {
                boolean throughWater = MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1);
                if (MovementHelper.isWater(destOn) && throughWater) {
                    // this happens when assume walk on water is true and this is a traverse in water, which isn't allowed
                    return COST_INF;
                }
                double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, destOn);
                if (placeCost >= COST_INF) {
                    return COST_INF;
                }
                double hardness1 = MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false);
                if (hardness1 >= COST_INF) {
                    return COST_INF;
                }
                double hardness2 = context.crawlMode ? 0 : MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, pb0, true);
                double WC = throughWater ? context.waterWalkSpeed : WALK_ONE_BLOCK_COST;
                for (int i = 0; i < 5; i++) {
                    int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepX();
                    int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepY();
                    int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepZ();
                    if (againstX == x && againstZ == z) { // this would be a backplace
                        continue;
                    }
                    if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) { // found a side place option
                        return WC + placeCost + hardness1 + hardness2;
                    }
                }
                // now that we've checked all possible directions to side place, we actually need to backplace
                if (srcDownBlock == Blocks.SOUL_SAND || (srcDownBlock instanceof SlabBlock && srcDown.getValue(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    return COST_INF; // can't sneak and backplace against soul sand or half slabs (regardless of whether it's top half or bottom half) =/
                }
                if (!standingOnABlock) { // standing on water / swimming
                    return COST_INF; // this is obviously impossible
                }
                Block blockSrc = context.getBlock(x, y, z);
                if ((blockSrc == Blocks.LILY_PAD || blockSrc instanceof CarpetBlock) && !srcDown.getFluidState().isEmpty()) {
                    return COST_INF; // we can stand on these but can't place against them
                }
                WC = WC * (SNEAK_ONE_BLOCK_COST / WALK_ONE_BLOCK_COST);//since we are sneak backplacing, we are sneaking lol
                return WC + placeCost + hardness1 + hardness2;
            }
            return COST_INF;
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        BlockState pb0 = positionsToBreak.length > 1 ? BlockStateInterface.get(ctx, positionsToBreak[0]) : Blocks.AIR.defaultBlockState();
        BlockState pb1 = BlockStateInterface.get(ctx, positionsToBreak[positionsToBreak.length - 1]);
        if (state.getStatus() != MovementStatus.RUNNING) {
            // if the setting is enabled
            if (!Baritone.settings().walkWhileBreaking.value) {
                return state;
            }
            // and if we're prepping (aka mining the block in front)
            if (state.getStatus() != MovementStatus.PREPPING) {
                return state;
            }
            // and if it's fine to walk into the blocks in front
            if (MovementHelper.avoidWalkingInto(pb0)) {
                return state;
            }
            if (MovementHelper.avoidWalkingInto(pb1)) {
                return state;
            }
            // and we aren't already pressed up against the block
            double dist = Math.max(Math.abs(ctx.player().position().x - (dest.getX() + 0.5D)), Math.abs(ctx.player().position().z - (dest.getZ() + 0.5D)));
            if (dist < 0.83) {
                return state;
            }
            if (!state.getTarget().getRotation().isPresent()) {
                // this can happen rarely when the server lags and doesn't send the falling sand entity until you've already walked through the block and are now mining the next one
                return state;
            }

            // combine the yaw to the center of the destination, and the pitch to the specific block we're trying to break
            // it's safe to do this since the two blocks we break (in a traverse) are right on top of each other and so will have the same yaw
            float yawToDest = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.calculateBlockCenter(ctx.world(), dest), ctx.playerRotations()).getYaw();
            float pitchToBreak = state.getTarget().getRotation().get().getPitch();
            if ((MovementHelper.isBlockNormalCube(pb0) || pb0.getBlock() instanceof AirBlock && (MovementHelper.isBlockNormalCube(pb1) || pb1.getBlock() instanceof AirBlock))) {
                // in the meantime, before we're right up against the block, we can break efficiently at this angle
                pitchToBreak = 26;
            }

            return state.setTarget(new MovementState.MovementTarget(new Rotation(yawToDest, pitchToBreak), true))
                    .setInput(Input.MOVE_FORWARD, true)
                    .setInput(Input.SPRINT, true);
        }

        Block fd = BlockStateInterface.get(ctx, src.below()).getBlock();
        boolean ladder = MovementHelper.isClimbable(fd);

        //sneak may have been set to true in the PREPPING state while mining an adjacent block, but we still want it to be true if the player is about to go on magma
        state.setInput(Input.SNEAK, Baritone.settings().allowWalkOnMagmaBlocks.value && MovementHelper.steppingOnBlocks(ctx).stream().anyMatch(block -> ctx.world().getBlockState(block).is(Blocks.MAGMA_BLOCK)));

        if (pb0.getBlock() instanceof DoorBlock || pb1.getBlock() instanceof DoorBlock) {
            boolean notPassable = pb0.getBlock() instanceof DoorBlock && !MovementHelper.isDoorPassable(ctx, src, dest) || pb1.getBlock() instanceof DoorBlock && !MovementHelper.isDoorPassable(ctx, dest, src);
            boolean canOpen = !(Blocks.IRON_DOOR.equals(pb0.getBlock()) || Blocks.IRON_DOOR.equals(pb1.getBlock()));

            if (notPassable && canOpen) {
                return state.setTarget(new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.calculateBlockCenter(ctx.world(), positionsToBreak[0]), ctx.playerRotations()), true))
                        .setInput(Input.CLICK_RIGHT, true);
            }
        }

        if (pb0.getBlock() instanceof FenceGateBlock || pb1.getBlock() instanceof FenceGateBlock) {
            BlockPos blocked = !MovementHelper.isGatePassable(ctx, positionsToBreak[0], src.above()) ? positionsToBreak[0]
                    : !MovementHelper.isGatePassable(ctx, positionsToBreak[1], src) ? positionsToBreak[1]
                    : null;
            if (blocked != null) {
                Optional<Rotation> rotation = RotationUtils.reachable(ctx, blocked);
                if (rotation.isPresent()) {
                    return state.setTarget(new MovementState.MovementTarget(rotation.get(), true)).setInput(Input.CLICK_RIGHT, true);
                }
            }
        }

        boolean isTheBridgeBlockThere = MovementHelper.canWalkOn(ctx, positionToPlace) || ladder || MovementHelper.canUseFrostWalker(ctx, positionToPlace);
        BlockPos feet = ctx.playerFeet();
        if (feet.getY() != dest.getY() && !ladder) {
            logDebug("Wrong Y coordinate");
            if (isTheBridgeBlockThere) {
                MovementHelper.moveTowards(ctx, state, dest);
                if (feet.getY() < dest.getY()) {
                    return state.setInput(Input.JUMP, true);
                }
                return state;
            }
            // Block cầu chưa được đặt -> TUYỆT ĐỐI KHÔNG NHẢY!
            state.setInput(Input.JUMP, false);
            state.setInput(Input.SPRINT, false);
        }

        if (isTheBridgeBlockThere) {
            ticksWithoutPlacement = 0;
            if (feet.equals(dest)) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            if (Baritone.settings().overshootTraverse.value && (feet.equals(dest.offset(getDirection())) || feet.equals(dest.offset(getDirection()).offset(getDirection())))) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            Block low = BlockStateInterface.get(ctx, src).getBlock();
            Block high = BlockStateInterface.get(ctx, src.above()).getBlock();
            if (ctx.player().position().y > src.y + 0.1D && !ctx.player().onGround() && (MovementHelper.isClimbable(low) || MovementHelper.isClimbable(high))) {
                // hitting W could cause us to climb the ladder instead of going forward
                // wait until we're on the ground
                return state;
            }
            BlockPos into = dest.subtract(src).offset(dest);
            BlockState intoBelow = BlockStateInterface.get(ctx, into);
            BlockState intoAbove = BlockStateInterface.get(ctx, into.above());
            boolean nextIsParkour = false;
            boolean nextNeedsBridge = false;
            if (baritone.getPathingBehavior().isPathing()) {
                baritone.api.pathing.path.IPathExecutor executor = baritone.getPathingBehavior().getCurrent();
                if (executor != null && executor.getPath() != null) {
                    int nextIdx = executor.getPosition() + 1;
                    if (nextIdx < executor.getPath().movements().size()) {
                        baritone.api.pathing.movement.IMovement nextMvt = executor.getPath().movements().get(nextIdx);
                        nextIsParkour = nextMvt instanceof MovementParkour;
                        if (nextMvt instanceof MovementTraverse) {
                            nextNeedsBridge = !MovementHelper.canWalkOn(ctx, nextMvt.getDest().below());
                        }
                    }
                }
            }

            boolean lavaHazard = isLavaNearbyOrBelow(dest) || isLavaNearbyOrBelow(into) || isLavaNearbyOrBelow(src);
            boolean intoCanWalk = MovementHelper.canWalkOn(ctx, into.below());

            if (wasTheBridgeBlockAlwaysThere && !nextNeedsBridge && !lavaHazard && intoCanWalk && (!MovementHelper.isLiquid(ctx, feet) || Baritone.settings().sprintInWater.value) && (nextIsParkour || (!MovementHelper.avoidWalkingInto(intoBelow) || MovementHelper.isWater(intoBelow))) && !MovementHelper.avoidWalkingInto(intoAbove)) {
                state.setInput(Input.SPRINT, true);
            } else {
                state.setInput(Input.SPRINT, false);
                if (ctx.player().isSprinting()) {
                    ctx.player().setSprinting(false);
                }
            }

            BlockState destDown = BlockStateInterface.get(ctx, dest.below());
            if (feet.getY() != dest.getY() && ladder && MovementHelper.isClimbable(destDown.getBlock())) {
                state.setInput(Input.JUMP, true);
            }

            // Khi cầu đang bắc qua dung nham/khoảng trống và chuẩn bị bước sang block tiếp theo: đè Shift sớm để triệt tiêu đà quán tính
            if (nextNeedsBridge || lavaHazard) {
                double distToDest = Math.max(Math.abs(ctx.player().position().x - (dest.getX() + 0.5D)), Math.abs(ctx.player().position().z - (dest.getZ() + 0.5D)));
                if (distToDest < 0.40D || feet.equals(dest)) {
                    state.setInput(Input.SNEAK, true);
                }
            }

            MovementHelper.moveTowards(ctx, state, positionsToBreak[0]);
            return state;
        } else {
            wasTheBridgeBlockAlwaysThere = false;
            ticksWithoutPlacement++;

            // 1. TUYỆT ĐỐI KHÔNG NHẢY HOẶC SPRINT - LUÔN ĐÈ SHIFT 100% KHI BLOCK CẦU CHƯA CÓ
            state.setInput(Input.JUMP, false);
            state.setInput(Input.SPRINT, false);
            state.setInput(Input.SNEAK, true);
            if (ctx.player().isSprinting()) {
                ctx.player().setSprinting(false);
            }

            int dx = dest.getX() - src.getX();
            int dz = dest.getZ() - src.getZ();
            Direction againstFace;
            if (dx > 0) {
                againstFace = Direction.EAST;
            } else if (dx < 0) {
                againstFace = Direction.WEST;
            } else if (dz > 0) {
                againstFace = Direction.SOUTH;
            } else {
                againstFace = Direction.NORTH;
            }

            BlockPos against = src.below();
            double faceX = (dest.getX() + src.getX() + 1.0D) * 0.5D;
            double faceZ = (dest.getZ() + src.getZ() + 1.0D) * 0.5D;

            double playerX = ctx.player().position().x;
            double playerZ = ctx.player().position().z;
            // Signed progress past edge towards dest: negative = safely inside src, 0 = edge plane, positive = overhang over void
            double progressPastEdge = (playerX - faceX) * dx + (playerZ - faceZ) * dz;

            if (Baritone.settings().neverBridgeOverLava.value && isLavaNearbyOrBelow(dest)) {
                logDebug("neverBridgeOverLava is active and lava detected near bridge destination. Aborting movement.");
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.SPRINT, false);
                state.setInput(Input.JUMP, false);
                state.setInput(Input.SNEAK, true);
                if (progressPastEdge > -0.20 || feet.equals(dest)) {
                    MovementHelper.moveTowardsWithoutRotation(ctx, state, src);
                    state.setInput(Input.MOVE_BACK, true);
                    return state;
                }
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (!((Baritone) baritone).getInventoryBehavior().hasGenericThrowaway()) {
                logDebug("No throwaway blocks for bridging. Failing movement.");
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.SPRINT, false);
                state.setInput(Input.JUMP, false);
                state.setInput(Input.SNEAK, true);
                if (progressPastEdge > -0.20 || feet.equals(dest)) {
                    MovementHelper.moveTowardsWithoutRotation(ctx, state, src);
                    state.setInput(Input.MOVE_BACK, true);
                    return state;
                }
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (ticksWithoutPlacement > 60) {
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.SPRINT, false);
                state.setInput(Input.JUMP, false);
                state.setInput(Input.SNEAK, true);
                MovementHelper.moveTowardsWithoutRotation(ctx, state, src);
                state.setInput(Input.MOVE_BACK, true);
                if (progressPastEdge <= -0.20 && !feet.equals(dest)) {
                    logDebug("Bridging block placement timed out. Safely backed into src. Aborting.");
                    return state.setStatus(MovementStatus.UNREACHABLE);
                }
                return state;
            }

            Block standingOn = BlockStateInterface.get(ctx, feet.below()).getBlock();
            if (standingOn.equals(Blocks.SOUL_SAND) || standingOn instanceof SlabBlock) { // see issue #118
                double dist = Math.max(Math.abs(dest.getX() + 0.5 - ctx.player().position().x), Math.abs(dest.getZ() + 0.5 - ctx.player().position().z));
                if (dist < 0.85) { // 0.5 + 0.3 + epsilon
                    MovementHelper.moveTowards(ctx, state, dest);
                    return state.setInput(Input.MOVE_FORWARD, false)
                            .setInput(Input.MOVE_BACK, true);
                }
            }

            // 1. Kiểm tra nếu có thể đặt trực tiếp từ cự ly hiện tại (ví dụ có tường bên cạnh hoặc block bên dưới)
            MovementHelper.PlaceResult p = MovementHelper.attemptToPlaceABlock(state, baritone, dest.below(), false, true);
            if (p == MovementHelper.PlaceResult.READY_TO_PLACE) {
                state.setInput(Input.SNEAK, true);
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.MOVE_BACK, false);
                state.setInput(Input.CLICK_RIGHT, true);
                return state;
            }

            // 2. Open-air Bridging (Bắc cầu trên không/hố theo chuẩn Fail-Safe 3-Zone Engine):
            // Luôn đảm bảo chọn sẵn block bắc cầu trong tay
            ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, dest.below().getX(), dest.below().getY(), dest.below().getZ());

            // Raycast ngắm chính xác vào GIỮA MẶT BÊN (Center of Block Face):
            // faceX, faceZ là tâm mặt bên, against.getY() + 0.5D là chính giữa tâm của block
            Vec3 eyePos = RayTraceUtils.inferSneakingEyePosition(ctx.player());
            double reach = ctx.playerController().getBlockReachDistance();
            double centerY = against.getY() + 0.5D;
            double[] yOffsets = new double[]{centerY, against.getY() + 0.55D, against.getY() + 0.60D, against.getY() + 0.68D, against.getY() + 0.75D, against.getY() + 0.82D};
            Rotation bestRotation = null;
            boolean canHitSideFace = false;

            for (double targetY : yOffsets) {
                Rotation candidate = RotationUtils.calcRotationFromVec3d(eyePos, new Vec3(faceX, targetY, faceZ), ctx.playerRotations());
                HitResult res = RayTraceUtils.rayTraceTowards(ctx.player(), candidate, reach, true);
                if (res != null && res.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bhr = (BlockHitResult) res;
                    if (bhr.getBlockPos().equals(against) && bhr.getDirection() == againstFace) {
                        bestRotation = candidate;
                        canHitSideFace = true;
                        break;
                    }
                }
            }

            if (bestRotation == null) {
                bestRotation = RotationUtils.calcRotationFromVec3d(eyePos, new Vec3(faceX, centerY, faceZ), ctx.playerRotations());
            }
            state.setTarget(new MovementState.MovementTarget(bestRotation, true));

            // Kiểm tra crosshair hiện tại và kích hoạt click chuột phải khi ngắm trúng mặt bên againstFace
            HitResult mouseOver = ctx.objectMouseOver();
            if (mouseOver != null && mouseOver.getType() == HitResult.Type.BLOCK) {
                BlockHitResult bhr = (BlockHitResult) mouseOver;
                if (bhr.getBlockPos().equals(against) && bhr.getDirection() == againstFace) {
                    state.setInput(Input.CLICK_RIGHT, true);
                    state.setInput(Input.MOVE_FORWARD, false);
                    state.setInput(Input.MOVE_BACK, false);
                    return state;
                }
            }

            // 3-ZONE FAIL-SAFE BRIDGING ENGINE (Triệt tiêu 100% nguy cơ rơi vào dung nham/vực):
            if (progressPastEdge > 0.18) {
                // Zone 3: Vượt quá mép cho phép (> 18cm) -> lùi nhẹ lại
                MovementHelper.moveTowardsWithoutRotation(ctx, state, src);
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.MOVE_BACK, true);
            } else if (canHitSideFace && progressPastEdge >= -0.05) {
                // Zone 2 (Golden Placement Zone): -5cm đến +18cm và có góc nhìn thấy tâm mặt bên
                // Dừng hẳn mọi chuyển động ngang, giữ thăng bằng tuyệt đối trên block để camera khóa và click!
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.MOVE_BACK, false);
                state.setInput(Input.MOVE_LEFT, false);
                state.setInput(Input.MOVE_RIGHT, false);
            } else {
                // Zone 1 (Approach Zone): Còn ở trong lòng src, tiến từ từ (vẫn đè Shift) về phía mép để mở góc nhìn
                MovementHelper.moveTowardsWithoutRotation(ctx, state, dest);
            }

            return state;
        }
    }

    private boolean isLavaNearbyOrBelow(BlockPos pos) {
        if (MovementHelper.isLava(BlockStateInterface.get(ctx, pos))) {
            return true;
        }
        BlockState destDown = BlockStateInterface.get(ctx, pos.below());
        if (MovementHelper.isLava(destDown)) {
            return true;
        }
        for (int dy = 2; dy <= 6; dy++) {
            if (MovementHelper.isLava(BlockStateInterface.get(ctx, pos.below(dy)))) {
                return true;
            }
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (MovementHelper.isLava(BlockStateInterface.get(ctx, pos.relative(dir))) || MovementHelper.isLava(BlockStateInterface.get(ctx, pos.below().relative(dir)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we're in the process of breaking blocks before walking forwards
        // or if this isn't a sneak place (the block is already there)
        // then it's safe to cancel this
        return state.getStatus() != MovementStatus.RUNNING || MovementHelper.canWalkOn(ctx, dest.below());
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (ctx.playerFeet().equals(src) || ctx.playerFeet().equals(src.below())) {
            Block block = BlockStateInterface.getBlock(ctx, src.below());
            if (MovementHelper.isClimbable(block)) {
                state.setInput(Input.SNEAK, true);
            }
        }
        return super.prepared(state);
    }
}
