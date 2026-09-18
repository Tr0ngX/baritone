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

public class BlockPlacementGeometryTest {

    @Test
    public void testBridgingLookBackGeometry() {
        BlockPos src = new BlockPos(10, 64, 10);
        BlockPos dest = new BlockPos(11, 64, 10);

        // Bridging face: middle of face between src.below() and dest.below()
        double faceX = (dest.getX() + src.getX() + 1.0D) * 0.5D; // 11.0
        double faceY = (dest.getY() + src.getY() - 1.0D) * 0.5D; // 63.5
        double faceZ = (dest.getZ() + src.getZ() + 1.0D) * 0.5D; // 10.5
        Vec3 faceCenter = new Vec3(faceX, faceY, faceZ);

        assertEquals(11.0, faceX, 1e-6);
        assertEquals(63.5, faceY, 1e-6);
        assertEquals(10.5, faceZ, 1e-6);

        // When player is standing at the edge, crouching, eyes slightly over the edge
        Vec3 eyeAtEdge = new Vec3(11.08, 65.27, 10.5);
        Rotation backToFace = RotationUtils.calcRotationFromVec3d(eyeAtEdge, faceCenter);

        // Facing towards -X (WEST): Yaw should be approximately 90 or -270 degrees
        // Look direction vector should point backwards towards the face
        Vec3 lookDir = RotationUtils.calcLookDirectionFromRotation(backToFace);
        assertTrue("Look vector X component must be negative (pointing back at src.below())", lookDir.x < 0);
        assertTrue("Look vector Y component must be negative (pointing down at face)", lookDir.y < 0);
        assertEquals("Look vector Z component should be nearly zero", 0.0, lookDir.z, 0.05);

        // Verify face visibility: Face normal is (+1, 0, 0) for EAST face of src.below()
        // Ray direction from eye to face is faceCenter - eyeAtEdge
        Vec3 rayDir = faceCenter.subtract(eyeAtEdge);
        double dotProduct = rayDir.x * 1.0 + rayDir.y * 0.0 + rayDir.z * 0.0;
        assertTrue("Ray must hit the front of the face (dot with normal < 0)", dotProduct < 0);
    }

    @Test
    public void testBehindEdgeOcclusionProof() {
        // Mathematical proof test: When eyes are behind edge (e.g. X = 10.6):
        // Ray from eye to side face must intersect top plane (Y = 64.0) strictly inside block boundaries
        double eyeX = 10.6;
        double eyeY = 65.27;
        double faceX = 11.0;
        double faceY = 63.5;
        double topPlaneY = 64.0;

        // t at topPlaneY:
        double t = (topPlaneY - eyeY) / (faceY - eyeY);
        assertTrue("Intersection parameter t must be between 0 and 1", t > 0 && t < 1);

        double xAtTopPlane = eyeX + t * (faceX - eyeX);
        assertTrue("Ray crosses top plane strictly inside top face (X < 11.0), proving occlusion", xAtTopPlane < 11.0);
        assertTrue("Ray crosses top plane inside block 10 (X >= 10.0)", xAtTopPlane >= 10.0);
    }

    @Test
    public void testBlockPlacementPerformanceBenchmark() {
        Vec3 eye = new Vec3(11.05, 65.27, 10.5);
        Vec3 target = new Vec3(11.0, 63.5, 10.5);

        // Warm up
        for (int i = 0; i < 10000; i++) {
            RotationUtils.calcRotationFromVec3d(eye, target);
        }

        long start = System.nanoTime();
        int iterations = 100_000;
        float checksum = 0;
        for (int i = 0; i < iterations; i++) {
            Rotation r = RotationUtils.calcRotationFromVec3d(eye, target);
            checksum += r.getYaw() + r.getPitch();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue("Benchmark checksum must be non-zero", checksum != 0);
        assertTrue("100k rotation calculations should finish in under 100ms", elapsedMs < 100);
    }
}
