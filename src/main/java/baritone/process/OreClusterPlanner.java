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

package baritone.process;

import baritone.pathing.movement.CalculationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Ore Cluster Planning: Groups ore locations into clusters and evaluates
 * which cluster provides the best value/distance ratio.
 *
 * Instead of pathfinding to each individual ore block one at a time
 * (requiring N separate A* calls), this groups nearby ores into clusters
 * and paths to the best cluster (requiring only 1 A* call per cluster).
 *
 * Algorithm: Union-Find with distance threshold of 5 blocks.
 * Two ore blocks within 5 blocks of each other belong to the same cluster.
 *
 * Cluster value formula:
 *   clusterValue = sum(oreValue[i]) / sqrt(distSqr(playerPos, clusterCenter))
 *
 * @author Baritone AutoMine Enhancement
 */
public final class OreClusterPlanner {

    /**
     * Maximum squared distance between two ore blocks to be in the same cluster.
     * 5 blocks radius -> 25 squared distance.
     */
    private static final double CLUSTER_RADIUS_SQ = 25.0;

    /**
     * Ore value mapping for cluster evaluation.
     * Higher value = more desirable to mine first.
     */
    private static final Map<Block, Double> ORE_VALUES = new HashMap<>();

    static {
        // Diamond: highest value
        ORE_VALUES.put(Blocks.DIAMOND_ORE, 10.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_DIAMOND_ORE, 10.0);

        // Emerald: rare, high value
        ORE_VALUES.put(Blocks.EMERALD_ORE, 8.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_EMERALD_ORE, 8.0);

        // Ancient Debris: nether, very valuable
        ORE_VALUES.put(Blocks.ANCIENT_DEBRIS, 9.0);

        // Gold
        ORE_VALUES.put(Blocks.GOLD_ORE, 5.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_GOLD_ORE, 5.0);
        ORE_VALUES.put(Blocks.NETHER_GOLD_ORE, 4.0);

        // Lapis
        ORE_VALUES.put(Blocks.LAPIS_ORE, 4.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_LAPIS_ORE, 4.0);

        // Iron
        ORE_VALUES.put(Blocks.IRON_ORE, 3.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_IRON_ORE, 3.0);

        // Redstone
        ORE_VALUES.put(Blocks.REDSTONE_ORE, 2.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_REDSTONE_ORE, 2.0);

        // Copper
        ORE_VALUES.put(Blocks.COPPER_ORE, 1.5);
        ORE_VALUES.put(Blocks.DEEPSLATE_COPPER_ORE, 1.5);

        // Coal
        ORE_VALUES.put(Blocks.COAL_ORE, 1.0);
        ORE_VALUES.put(Blocks.DEEPSLATE_COAL_ORE, 1.0);

        // Quartz
        ORE_VALUES.put(Blocks.NETHER_QUARTZ_ORE, 1.5);
    }

    private OreClusterPlanner() {
        // Utility class, no instances
    }

    /**
     * Groups ore positions into clusters using Union-Find.
     * Two ores within CLUSTER_RADIUS_SQ of each other are in the same cluster.
     * Transitive: if A is near B and B is near C, then {A, B, C} are one cluster.
     *
     * @param ores List of known ore positions (already pruned)
     * @return List of clusters, each cluster is a List of BlockPos
     */
    public static List<List<BlockPos>> clusterOres(List<BlockPos> ores) {
        if (ores.isEmpty()) {
            return Collections.emptyList();
        }

        int n = ores.size();

        // Union-Find (Disjoint Set Union) with path compression and union by rank
        int[] parent = new int[n];
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        // Merge ores that are within CLUSTER_RADIUS_SQ of each other
        for (int i = 0; i < n; i++) {
            BlockPos a = ores.get(i);
            for (int j = i + 1; j < n; j++) {
                BlockPos b = ores.get(j);
                if (a.distSqr(b) <= CLUSTER_RADIUS_SQ) {
                    union(parent, rank, i, j);
                }
            }
        }

        // Group by root
        Map<Integer, List<BlockPos>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(ores.get(i));
        }

        return new ArrayList<>(groups.values());
    }

    /**
     * Evaluates the value of a cluster relative to the player's position.
     *
     * Formula: clusterValue = totalOreValue / distance
     *
     * @param cluster   The cluster of ore positions
     * @param playerPos Current player feet position
     * @param ctx       Calculation context for reading block states
     * @return Cluster value score (higher = better to mine first)
     */
    public static double evaluateCluster(List<BlockPos> cluster, BlockPos playerPos,
                                          CalculationContext ctx) {
        if (cluster.isEmpty()) {
            return 0.0;
        }

        double totalValue = 0.0;
        long centerX = 0, centerY = 0, centerZ = 0;

        for (BlockPos pos : cluster) {
            // Get the block at this position to determine ore type
            BlockState state = ctx.bsi.get0(pos);
            Block block = state.getBlock();
            Double value = ORE_VALUES.get(block);
            totalValue += (value != null) ? value : 1.0; // default 1.0 for unknown ore types

            centerX += pos.getX();
            centerY += pos.getY();
            centerZ += pos.getZ();
        }

        // Calculate cluster center
        int cx = (int) (centerX / cluster.size());
        int cy = (int) (centerY / cluster.size());
        int cz = (int) (centerZ / cluster.size());
        BlockPos center = new BlockPos(cx, cy, cz);

        // Distance from player to cluster center
        double distance = Math.sqrt(playerPos.distSqr(center));
        if (distance < 1.0) {
            distance = 1.0; // Prevent division by zero; if already at cluster, value is maximal
        }

        // Bonus for larger clusters (more ores = less wasted pathfinding time)
        double sizeBonus = 1.0 + (cluster.size() - 1) * 0.3;

        return (totalValue * sizeBonus) / distance;
    }

    /**
     * Selects the best cluster to mine based on value/distance ratio.
     *
     * @param clusters  List of ore clusters
     * @param playerPos Current player feet position
     * @param ctx       Calculation context
     * @return The best cluster to mine, or empty list if none
     */
    public static List<BlockPos> selectBestCluster(List<List<BlockPos>> clusters,
                                                    BlockPos playerPos,
                                                    CalculationContext ctx) {
        if (clusters.isEmpty()) {
            return Collections.emptyList();
        }

        List<BlockPos> best = null;
        double bestValue = -1.0;

        for (List<BlockPos> cluster : clusters) {
            double value = evaluateCluster(cluster, playerPos, ctx);
            if (value > bestValue) {
                bestValue = value;
                best = cluster;
            }
        }

        return best != null ? best : Collections.emptyList();
    }

    // --- Union-Find helpers ---

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]; // Path compression (halving)
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int[] rank, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA == rootB) return;

        // Union by rank
        if (rank[rootA] < rank[rootB]) {
            parent[rootA] = rootB;
        } else if (rank[rootA] > rank[rootB]) {
            parent[rootB] = rootA;
        } else {
            parent[rootB] = rootA;
            rank[rootA]++;
        }
    }
}
