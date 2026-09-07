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
import baritone.api.utils.input.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

public class PlayerMovementInput extends ClientInput {

    private final InputOverrideHandler handler;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick() {
        float leftImpulse = 0.0F;
        float forwardImpulse = 0.0F;
        boolean jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style

        boolean up = handler.isInputForcedDown(Input.MOVE_FORWARD);
        if (up) {
            forwardImpulse++;
        }

        boolean down = handler.isInputForcedDown(Input.MOVE_BACK);
        if (down) {
            forwardImpulse--;
        }

        boolean left = handler.isInputForcedDown(Input.MOVE_LEFT);
        if (left) {
            leftImpulse++;
        }

        boolean right = handler.isInputForcedDown(Input.MOVE_RIGHT);
        if (right) {
            leftImpulse--;
        }

        boolean sneaking = handler.isInputForcedDown(Input.SNEAK);
        if (sneaking) {
            leftImpulse *= 0.3D;
            forwardImpulse *= 0.3D;
        }
        this.moveVector = new Vec2(leftImpulse, forwardImpulse);

        boolean sprinting = handler.isInputForcedDown(Input.SPRINT);
        // AUTO SPRINT: Tự động chạy nhanh khi tiến về phía trước
        if (Baritone.settings().allowSprint.value && up && !sneaking) {
            sprinting = true;
        }

        // ULTRA-FAST TUNNEL BUNNY HOP (Không delay, cứ tiếp đất là nhảy tiếp)
        if (Baritone.settings().tunnelSprintJump.value && up && !sneaking) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                LocalPlayer p = mc.player;
                if (!p.isInWater() && !p.isSwimming() && !p.isCrouching() && !Baritone.settings().crawlMineMode.value && p.getFoodData().getFoodLevel() > 6) {
                    BlockPos pFeet = BlockPos.containing(p.getX(), p.getBoundingBox().minY + 0.1, p.getZ());
                    BlockState ceil = mc.level.getBlockState(pFeet.above(2));
                    BlockState head = mc.level.getBlockState(pFeet.above());
                    if (!ceil.isAir() && ceil.blocksMotion() && !head.blocksMotion()) {
                        jumping = true;
                        sprinting = true;
                    }
                }
            }
        }

        this.keyPresses = new net.minecraft.world.entity.player.Input(up, down, left, right, jumping, sneaking, sprinting);
    }
}
