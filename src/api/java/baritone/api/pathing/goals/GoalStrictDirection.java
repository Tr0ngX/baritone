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

package baritone.api.pathing.goals;

import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/**
 * Intelligent strict directional mining goal.
 * Features:
 * - Finite goal detection (isInGoal = true when reaching target distance) -> A* solves in 2ms instead of freezing for 2.5s!
 * - Reward for forward progress (-forward * 100)
 * - Severe penalty for standing still or going backward (+5000 - forward * 500)
 * - Heavy penalty for lateral off-path deviation (+lateral * 1500)
 * - Heavy penalty for vertical deviation (+vertical * 2000)
 * - Huge reward bonus when approaching target ores (-3500 / distance)
 */
public class GoalStrictDirection implements Goal {

    public final int x;
    public final int y;
    public final int z;
    public final int dx;
    public final int dz;
    public final int targetDistance;
    public final Integer targetY;
    public final List<BlockPos> targetOres;

    public GoalStrictDirection(BlockPos origin, Direction direction) {
        this(origin, direction, 24, origin.getY(), null);
    }

    public GoalStrictDirection(BlockPos origin, Direction direction, int targetDistance, Integer targetY, List<BlockPos> targetOres) {
        this.x = origin.getX();
        this.y = origin.getY();
        this.z = origin.getZ();
        this.dx = direction.getStepX();
        this.dz = direction.getStepZ();
        this.targetDistance = targetDistance;
        this.targetY = targetY;
        this.targetOres = targetOres;
        if (dx == 0 && dz == 0) {
            throw new IllegalArgumentException(direction + "");
        }
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int forward = (x - this.x) * dx + (z - this.z) * dz;
        int lateral = Math.abs((x - this.x) * dz) + Math.abs((z - this.z) * dx);
        int vertical = Math.abs(y - (targetY != null ? targetY : this.y));
        // Đã đào thông tới cự ly mục tiêu trong hành lang hầm mà không lệch quá 1 block
        return forward >= targetDistance && lateral <= 1 && vertical <= 1;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        int forward = (x - this.x) * dx + (z - this.z) * dz;
        int lateral = Math.abs((x - this.x) * dz) + Math.abs((z - this.z) * dx);
        int vertical = Math.abs(y - (targetY != null ? targetY : this.y));

        double heuristic;
        if (forward <= 0) {
            // PHẠT ĐỨNG YÊN / ĐI LÙI: Phạt cực nặng khi lùi lại phía sau hoặc dậm chân tại chỗ
            heuristic = 5000.0 - forward * 500.0;
        } else {
            // THƯỞNG TIẾN LÊN: Càng tiến về phía trước càng được điểm thưởng (heuristic âm hơn)
            heuristic = -forward * 100.0;
        }

        // PHẠT LỆCH HÀNG (LATERAL): Phạt 1500 điểm cho mỗi block lệch sang 2 bên
        heuristic += lateral * 1500.0;

        // PHẠT LỆCH TẦNG Y (VERTICAL): Phạt 2000 điểm cho mỗi block lệch độ cao
        heuristic += vertical * 2000.0;

        // ĐIỂM THƯỞNG QUẶNG: Nếu node này ở gần quặng mục tiêu phía trước, thưởng điểm cực lớn
        if (targetOres != null && !targetOres.isEmpty()) {
            double minOreDistSq = Double.MAX_VALUE;
            for (BlockPos ore : targetOres) {
                int oreForward = (ore.getX() - this.x) * dx + (ore.getZ() - this.z) * dz;
                if (oreForward >= forward - 2) {
                    double d = (ore.getX() - x) * (ore.getX() - x)
                            + (ore.getY() - y) * (ore.getY() - y)
                            + (ore.getZ() - z) * (ore.getZ() - z);
                    if (d < minOreDistSq) {
                        minOreDistSq = d;
                    }
                }
            }
            if (minOreDistSq <= 25.0) { // trong phạm vi 5 block quanh quặng
                heuristic -= 3500.0 / (Math.sqrt(minOreDistSq) + 0.5);
            }
        }

        return heuristic;
    }

    @Override
    public double heuristic() {
        return -targetDistance * 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GoalStrictDirection goal = (GoalStrictDirection) o;
        return x == goal.x
                && y == goal.y
                && z == goal.z
                && dx == goal.dx
                && dz == goal.dz
                && targetDistance == goal.targetDistance
                && Objects.equals(targetY, goal.targetY);
    }

    @Override
    public int hashCode() {
        int hash = (int) BetterBlockPos.longHash(x, y, z);
        hash = hash * 630627507 + dx;
        hash = hash * -283028380 + dz;
        hash = hash * 31 + targetDistance;
        hash = hash * 31 + (targetY != null ? targetY.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return String.format(
                "GoalStrictDirection{x=%s, y=%s, z=%s, dx=%s, dz=%s, dist=%s, targetY=%s}",
                SettingsUtil.maybeCensor(x),
                SettingsUtil.maybeCensor(y),
                SettingsUtil.maybeCensor(z),
                SettingsUtil.maybeCensor(dx),
                SettingsUtil.maybeCensor(dz),
                targetDistance,
                SettingsUtil.maybeCensor(targetY)
        );
    }
}
