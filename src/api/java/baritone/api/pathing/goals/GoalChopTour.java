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

import baritone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Goal đại diện cho một lộ trình (tour) chặt cây liên tục qua nhiều cây trong rừng.
 * Mục tiêu cuối cùng là hoàn thành toàn bộ hành trình qua danh sách các gốc cây (treeBases).
 */
public class GoalChopTour implements Goal, IGoalRenderPos {

    private final List<BlockPos> treeBases;
    private final BlockPos finalGoalPos;

    public GoalChopTour(List<BlockPos> treeBases) {
        this.treeBases = treeBases != null ? Collections.unmodifiableList(new ArrayList<>(treeBases)) : Collections.emptyList();
        this.finalGoalPos = this.treeBases.isEmpty() ? null : this.treeBases.get(this.treeBases.size() - 1);
    }

    public List<BlockPos> getTreeBases() {
        return treeBases;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        if (finalGoalPos == null) {
            return true;
        }
        int xDiff = x - finalGoalPos.getX();
        int yDiff = y - finalGoalPos.getY();
        int zDiff = z - finalGoalPos.getZ();
        return Math.abs(xDiff) + Math.abs(yDiff < 0 ? yDiff + 1 : yDiff) + Math.abs(zDiff) <= 1;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        if (finalGoalPos == null) {
            return 0;
        }
        int xDiff = x - finalGoalPos.getX();
        int yDiff = y - finalGoalPos.getY();
        int zDiff = z - finalGoalPos.getZ();
        return GoalBlock.calculate(xDiff, yDiff, zDiff);
    }

    @Override
    public BlockPos getGoalPos() {
        return finalGoalPos != null ? finalGoalPos : new BlockPos(0, 0, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoalChopTour that = (GoalChopTour) o;
        return Objects.equals(treeBases, that.treeBases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(treeBases);
    }

    @Override
    public String toString() {
        return "GoalChopTour[trees=" + treeBases.size() + ", final=" + (finalGoalPos != null ? finalGoalPos.toShortString() : "null") + "]";
    }
}
