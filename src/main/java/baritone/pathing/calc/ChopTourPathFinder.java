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
import baritone.api.pathing.goals.GoalChopTour;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.movement.IMovement;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.pathing.Favoring;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PathFinder thực hiện 1 LẦN TÍNH TOÁN SIÊU DÀI để tạo ra 1 ĐƯỜNG TÍNH DUY NHẤT
 * nối từ gốc cây này sang gốc cây khác trong toàn bộ lộ trình chặt cây (Chop Tour).
 */
public class ChopTourPathFinder extends AbstractNodeCostSearch {

    private final GoalChopTour chopGoal;
    private final CalculationContext calcContext;

    public ChopTourPathFinder(BetterBlockPos realStart, int startX, int startY, int startZ,
                              GoalChopTour goal, CalculationContext context) {
        super(realStart, startX, startY, startZ, goal, context);
        this.chopGoal = goal;
        this.calcContext = context;
    }

    @Override
    protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
        List<BlockPos> bases = chopGoal.getTreeBases();
        if (bases == null || bases.isEmpty()) {
            return Optional.empty();
        }

        long startTime = System.currentTimeMillis();
        // Cho phép tính toán siêu dài (ít nhất 20s-30s nếu có nhiều cây)
        long maxDuration = Math.max(primaryTimeout, 20000L);
        long deadline = startTime + maxDuration;

        BetterBlockPos currentPos = realStart;
        List<BetterBlockPos> allPositions = new ArrayList<>();
        List<IMovement> allMovements = new ArrayList<>();
        allPositions.add(currentPos);
        int totalNodesConsidered = 0;
        int connectedTrees = 0;

        Helper.HELPER.logDirect("§a[AutoChop] Bắt đầu 1 LẦN TÍNH TOÁN SIÊU DÀI cho " + bases.size() + " cây...");

        for (int i = 0; i < bases.size(); i++) {
            if (cancelRequested || System.currentTimeMillis() >= deadline) {
                break;
            }

            BlockPos targetBase = bases.get(i);
            Goal legGoal = new GoalGetToBlock(targetBase);

            // Nếu vị trí hiện tại đã đứng sát cây này rồi
            if (legGoal.isInGoal(currentPos.x, currentPos.y, currentPos.z)) {
                connectedTrees++;
                continue;
            }

            Favoring favoring = new Favoring(calcContext.getBaritone().getPlayerContext(), null, calcContext);
            AStarPathFinder legFinder = new AStarPathFinder(
                    currentPos, currentPos.x, currentPos.y, currentPos.z,
                    legGoal, favoring, calcContext
            );

            long timeLeft = Math.max(1000L, deadline - System.currentTimeMillis());
            long legPrimaryTimeout = Math.min(3500L, timeLeft);
            long legFailureTimeout = legPrimaryTimeout * 2;

            baritone.api.utils.PathCalculationResult legCalc = legFinder.calculate(legPrimaryTimeout, legFailureTimeout);
            if (legCalc.getPath().isPresent()) {
                IPath legPath = legCalc.getPath().get();
                if (!legPath.movements().isEmpty()) {
                    List<IMovement> legMoves = legPath.movements();
                    List<BetterBlockPos> legPositions = legPath.positions();

                    allMovements.addAll(legMoves);
                    allPositions.addAll(legPositions.subList(1, legPositions.size()));
                    totalNodesConsidered += legPath.getNumNodesConsidered();
                    currentPos = legPath.getDest();
                    connectedTrees++;
                }
            }
        }

        if (allMovements.isEmpty()) {
            return Optional.empty();
        }

        Helper.HELPER.logDirect("§a[AutoChop] Đã tạo thành công 1 ĐƯỜNG TÍNH DUY NHẤT nối " + connectedTrees + " cây (" + allMovements.size() + " bước di chuyển)!");
        return Optional.of(new ChopTourPath(allPositions, allMovements, totalNodesConsidered, goal));
    }
}
