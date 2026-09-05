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

package baritone.pathing.calc;

import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.movement.IMovement;
import baritone.api.utils.BetterBlockPos;
import baritone.utils.pathing.PathBase;

import java.util.Collections;
import java.util.List;

/**
 * Đường đi duy nhất (Composite Tour Path) kết nối liên tục từ cây này sang cây khác trong chế độ Chop Wood.
 * Đảm bảo không bị Baritone cắt ngắn (staticCutoff) và cho phép đi tuần tự qua toàn bộ các cây.
 */
public class ChopTourPath extends PathBase {

    private final List<BetterBlockPos> positions;
    private final List<IMovement> movements;
    private final int numNodes;
    private final Goal goal;

    public ChopTourPath(List<BetterBlockPos> positions, List<IMovement> movements, int numNodes, Goal goal) {
        this.positions = Collections.unmodifiableList(positions);
        this.movements = Collections.unmodifiableList(movements);
        this.numNodes = numNodes;
        this.goal = goal;
        sanityCheck();
    }

    @Override
    public List<IMovement> movements() {
        return movements;
    }

    @Override
    public List<BetterBlockPos> positions() {
        return positions;
    }

    @Override
    public int getNumNodesConsidered() {
        return numNodes;
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    @Override
    public int length() {
        return positions.size();
    }

    @Override
    public IPath postProcess() {
        return this;
    }

    @Override
    public PathBase cutoffAtLoadedChunks(Object bsi0) {
        // Đã được quét trong các chunk đã load
        return this;
    }

    @Override
    public PathBase staticCutoff(Goal destination) {
        // TUYỆT ĐỐI KHÔNG cắt ngắn đường tính toán siêu dài nối các cây!
        return this;
    }

    @Override
    public void sanityCheck() {
        if (positions.isEmpty() || movements.size() != positions.size() - 1) {
            throw new IllegalStateException("Size mismatch in ChopTourPath: pos=" + positions.size() + ", mov=" + movements.size());
        }
        for (int i = 0; i < movements.size(); i++) {
            if (!positions.get(i).equals(movements.get(i).getSrc())) {
                throw new IllegalStateException("Movement src mismatch at " + i + ": pos=" + positions.get(i) + ", movSrc=" + movements.get(i).getSrc());
            }
            if (!positions.get(i + 1).equals(movements.get(i).getDest())) {
                throw new IllegalStateException("Movement dest mismatch at " + i + ": pos=" + positions.get(i + 1) + ", movDest=" + movements.get(i).getDest());
            }
        }
    }
}
