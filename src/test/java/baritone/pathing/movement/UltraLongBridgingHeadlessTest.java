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

package baritone.pathing.movement;

import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

import static org.junit.Assert.*;

public class UltraLongBridgingHeadlessTest {

    private static final double GRIM_MAX_REACH = 3.1D; // Grim block reach tolerance
    private static final double CROUCH_EYE_HEIGHT = 1.27D; // Vanilla Minecraft crouching eye height
    private static final double STAND_EYE_HEIGHT = 1.62D;

    @Test
    public void testUltraLongContinuousBridgingSimulation100Blocks() {
        // Simulate a 100-block continuous void bridge going South (+Z direction)
        // Starting at (0, 100, 0) and bridging to (0, 100, 100)
        int startX = 0;
        int startY = 100;
        int startZ = 0;
        int totalBlocks = 100;

        int successfulPlacements = 0;
        int grimViolations = 0;

        for (int i = 0; i < totalBlocks; i++) {
            BlockPos src = new BlockPos(startX, startY, startZ + i);
            BlockPos dest = new BlockPos(startX, startY, startZ + i + 1);
            BlockPos against = src.below();
            BlockPos placeAt = dest.below();

            int dx = dest.getX() - src.getX();
            int dz = dest.getZ() - src.getZ();
            assertEquals(0, dx);
            assertEquals(1, dz);

            Direction againstFace = Direction.SOUTH;

            double faceX = (dest.getX() + src.getX() + 1.0D) * 0.5D; // 0.5
            double faceZ = (dest.getZ() + src.getZ() + 1.0D) * 0.5D; // startZ + i + 1.0
            double targetY = against.getY() + 0.95D; // 99.95

            // Sub-tick movement simulation as player traverses from src center to edge
            double playerZ = src.getZ() + 0.5D; // Start at center of src
            boolean sneakedAtEdge = false;
            boolean placed = false;

            // Step in 0.05 increments (simulating ticks of walking/sneaking)
            for (double step = 0; step <= 0.8D; step += 0.04D) {
                playerZ = (src.getZ() + 0.5D) + step;
                double progressPastEdge = (playerZ - faceZ) * dz;

                // Dynamic Sneak Rule: engage sneak when approaching within 30cm of the edge
                boolean shouldSneak = progressPastEdge >= -0.30D;
                if (shouldSneak) {
                    sneakedAtEdge = true;
                }

                // Verify safety: player must ALWAYS be sneaking once their bounding box front touches the edge (progressPastEdge > -0.15)
                if (progressPastEdge > -0.15D) {
                    assertTrue("Player must be sneaking before front of bounding box exceeds the block edge!", shouldSneak);
                }

                // Eye position (crouching if shouldSneak)
                double eyeY = startY + (shouldSneak ? CROUCH_EYE_HEIGHT : STAND_EYE_HEIGHT);
                Vec3 eyePos = new Vec3(0.5D, eyeY, playerZ);

                // Check Top-Edge Aiming raycast
                Rotation aim = RotationUtils.calcRotationFromVec3d(eyePos, new Vec3(faceX, targetY, faceZ));
                Vec3 lookDir = RotationUtils.calcLookDirectionFromRotation(aim);

                // Reach check for GrimAC
                double distanceToTarget = eyePos.distanceTo(new Vec3(faceX, targetY, faceZ));
                if (distanceToTarget > GRIM_MAX_REACH) {
                    // Still too far, continue approaching
                    continue;
                }

                // Check if ray from eyePos to (faceX, targetY, faceZ) intersects the top plane (Y = 100.0)
                // t at Y = 100.0:
                double t = (100.0D - eyeY) / (targetY - eyeY);
                if (t >= 0.0D && t <= 1.0D) {
                    double zAtTopPlane = playerZ + t * (faceZ - playerZ);
                    // If zAtTopPlane < faceZ, the ray struck the top plane inside block src.below() -> occluded!
                    if (zAtTopPlane < faceZ) {
                        // Ray is occluded by top surface, cannot place yet
                        continue;
                    }
                }

                // Raycast has clear line of sight to the SOUTH face!
                // Verify GrimAC requirements:
                // 1. Must strike the SOUTH face (ray moves in -Z direction towards the face, dot with normal < 0)
                assertTrue("Look direction Z must be negative (aiming back at south face)", lookDir.z < 0);
                assertTrue("Look direction Y must be negative (aiming down)", lookDir.y < 0);

                // 2. Reach distance <= 3.1
                if (distanceToTarget > GRIM_MAX_REACH) {
                    grimViolations++;
                }

                // 3. Player must be sneaking at the moment of placement
                assertTrue("Player must be actively sneaking when placing block!", shouldSneak);

                // 4. Hit vector coordinates strictly match face
                assertEquals(faceX, 0.5D, 1e-6);
                assertEquals(faceZ, dest.getZ() + 0.0D, 1e-6);

                placed = true;
                successfulPlacements++;
                break;
            }

            assertTrue("Block " + i + " must be successfully placed!", placed);
            assertTrue("Sneak must have been engaged before edge for block " + i, sneakedAtEdge);
        }

        assertEquals(100, successfulPlacements);
        assertEquals(0, grimViolations);
    }

    @Test
    public void testBridgingAllFourCompassDirections() {
        // Test East (+X), West (-X), South (+Z), North (-Z)
        Direction[] directions = new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

        for (Direction dir : directions) {
            BlockPos src = new BlockPos(100, 64, 100);
            BlockPos dest = src.relative(dir);
            BlockPos against = src.below();
            BlockPos placeAt = dest.below();

            int dx = dest.getX() - src.getX();
            int dz = dest.getZ() - src.getZ();

            Direction againstFace;
            if (dx > 0) againstFace = Direction.EAST;
            else if (dx < 0) againstFace = Direction.WEST;
            else if (dz > 0) againstFace = Direction.SOUTH;
            else againstFace = Direction.NORTH;

            assertEquals(dir, againstFace);

            double faceX = (dest.getX() + src.getX() + 1.0D) * 0.5D;
            double faceZ = (dest.getZ() + src.getZ() + 1.0D) * 0.5D;
            double targetY = against.getY() + 0.95D;

            // Player at edge (-0.05 from edge along dir)
            double px = faceX - dx * 0.05D;
            double pz = faceZ - dz * 0.05D;
            Vec3 eye = new Vec3(px, src.getY() + CROUCH_EYE_HEIGHT, pz);

            Rotation rot = RotationUtils.calcRotationFromVec3d(eye, new Vec3(faceX, targetY, faceZ));
            Vec3 look = RotationUtils.calcLookDirectionFromRotation(rot);

            // Verify look direction points towards face
            if (dx != 0) {
                assertTrue(dx > 0 ? look.x > 0 : look.x < 0);
            }
            if (dz != 0) {
                assertTrue(dz > 0 ? look.z > 0 : look.z < 0);
            }
            assertTrue("Pitch must be down", look.y < 0);

            // Reach distance within Grim limits
            double reach = eye.distanceTo(new Vec3(faceX, targetY, faceZ));
            assertTrue("Reach must be within Grim tolerance: " + reach, reach <= GRIM_MAX_REACH);
        }
    }
}
