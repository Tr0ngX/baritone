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

package baritone.utils.pathing;

import baritone.Baritone;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.IPlayerContext;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.block.Blocks;

public class Avoidance {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final double coefficient;
    private final int radius;
    private final int radiusSq;

    public Avoidance(BlockPos center, double coefficient, int radius) {
        this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
    }

    public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.coefficient = coefficient;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    public double coefficient(int x, int y, int z) {
        int xDiff = x - centerX;
        int yDiff = y - centerY;
        int zDiff = z - centerZ;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= radiusSq ? coefficient : 1.0D;
    }

    public static List<Avoidance> create(IPlayerContext ctx) {
        if (!Baritone.settings().avoidance.value) {
            return Collections.emptyList();
        }
        List<Avoidance> res = new ArrayList<>();
        double mobSpawnerCoeff = Baritone.settings().mobSpawnerAvoidanceCoefficient.value;
        double mobCoeff = Baritone.settings().mobAvoidanceCoefficient.value;
        if (mobSpawnerCoeff != 1.0D) {
            int spawnerRadius = Baritone.settings().mobSpawnerAvoidanceRadius.value;
            double coeff = Math.max(500.0D, mobSpawnerCoeff);
            Set<BlockPos> spawnerPositions = new HashSet<>();
            String spawnerName = BlockUtils.blockToString(Blocks.SPAWNER);
            spawnerPositions.addAll(ctx.worldData().getCachedWorld().getLocationsOf(spawnerName, 1, ctx.playerFeet().x, ctx.playerFeet().z, 2));
            spawnerPositions.addAll(ctx.worldData().getCachedWorld().getLocationsOf("mob_spawner", 1, ctx.playerFeet().x, ctx.playerFeet().z, 2));
            spawnerPositions.addAll(ctx.worldData().getCachedWorld().getLocationsOf("trial_spawner", 1, ctx.playerFeet().x, ctx.playerFeet().z, 2));

            // Quét thêm các lồng spawner trong các chunk đang load xung quanh người chơi
            if (ctx.world() != null && ctx.player() != null) {
                BetterBlockPos pf = ctx.playerFeet();
                int scanDist = Math.max(spawnerRadius, 24);
                int minChunkX = (pf.x - scanDist) >> 4;
                int maxChunkX = (pf.x + scanDist) >> 4;
                int minChunkZ = (pf.z - scanDist) >> 4;
                int maxChunkZ = (pf.z + scanDist) >> 4;
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        net.minecraft.world.level.chunk.LevelChunk chunk = ctx.world().getChunkSource().getChunk(cx, cz, false);
                        if (chunk != null && !chunk.isEmpty()) {
                            for (BlockPos bPos : chunk.getBlockEntitiesPos()) {
                                if (ctx.world().getBlockState(bPos).is(Blocks.SPAWNER)) {
                                    spawnerPositions.add(bPos);
                                }
                            }
                        }
                    }
                }
            }

            spawnerPositions.forEach(mobspawner -> res.add(new Avoidance(mobspawner, coeff, spawnerRadius)));
        }
        if (mobCoeff != 1.0D) {
            ctx.entitiesStream()
                    .filter(entity -> entity instanceof Mob)
                    .filter(entity -> (!(entity instanceof Spider)) || ctx.player().getLightLevelDependentMagicValue() < 0.5)
                    .filter(entity -> !(entity instanceof ZombifiedPiglin) || ((ZombifiedPiglin) entity).getLastHurtByMob() != null)
                    .filter(entity -> !(entity instanceof EnderMan) || ((EnderMan) entity).isCreepy())
                    .forEach(entity -> {
                        double coeff = mobCoeff;
                        int rad = Baritone.settings().mobAvoidanceRadius.value;

                        if (entity instanceof net.minecraft.world.entity.monster.warden.Warden) {
                            // 1. WARDEN - Nguy hiểm bậc nhất tuyệt đối (Bán kính né 24 block, hệ số phạt 1000.0)
                            coeff = 1000.0D;
                            rad = 24;
                        } else if (entity instanceof net.minecraft.world.entity.monster.Creeper) {
                            // 2. CREEPER - Chống nổ tan xác (Bán kính né 16 block, hệ số phạt 500.0)
                            coeff = 500.0D;
                            rad = 16;
                        } else if (entity instanceof net.minecraft.world.entity.monster.Zombie) {
                            // 3. ZOMBIE - Đánh cận chiến đông đảo (Bán kính né 14 block, hệ số phạt 250.0)
                            coeff = 250.0D;
                            rad = 14;
                        } else if (entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton) {
                            // 4. SKELETON - Bắn tỉa tầm xa (Bán kính né 16 block, hệ số phạt 200.0)
                            coeff = 200.0D;
                            rad = 16;
                        }

                        res.add(new Avoidance(entity.blockPosition(), coeff, rad));
                    });
        }
        return res;
    }

    public void applySpherical(Long2DoubleOpenHashMap map) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        long hash = BetterBlockPos.longHash(centerX + x, centerY + y, centerZ + z);
                        map.put(hash, map.get(hash) * coefficient);
                    }
                }
            }
        }
    }
}
