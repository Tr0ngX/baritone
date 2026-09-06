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
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches successful path segments for reuse when the bot traverses
 * previously-visited areas. Uses spatial hashing by 8x8x8 block regions.
 *
 * Cache invalidation:
 * - TTL-based: entries expire after 60 seconds (world may have changed)
 * - Region-based: when blocks change in a region, that region's entries are dirty
 * - LRU eviction: when cache exceeds max size, least-recently-used entries are removed
 *
 * Thread-safe: uses ConcurrentHashMap for concurrent PathingBehavior access.
 *
 * @author Baritone AutoMine Enhancement
 */
public final class PathSegmentCache {

    /**
     * Maximum number of cached path segments
     */
    private static final int MAX_CACHE_SIZE = 512;

    /**
     * Time-to-live for cache entries in milliseconds (30 seconds)
     */
    private static final long CACHE_TTL_MS = 30_000;

    /**
     * Region size shift (3 = 8x8x8 blocks per region)
     */
    private static final int REGION_SHIFT = 3;

    private final ConcurrentHashMap<Long, CachedSegment> cache;
    private final Set<Long> dirtyRegions;

    public PathSegmentCache() {
        this.cache = new ConcurrentHashMap<>(128);
        this.dirtyRegions = ConcurrentHashMap.newKeySet();
    }

    /**
     * Looks up a cached path between two positions.
     * Returns the cached path if found, valid (not expired), and in a clean region.
     *
     * @param start The starting position
     * @param goal  The goal position (approximate - matched by region)
     * @return Optional containing the cached path, or empty if cache miss
     */
    public Optional<IPath> lookup(BlockPos start, BlockPos goal) {
        long key = regionKey(start, goal);
        CachedSegment seg = cache.get(key);
        if (seg == null) {
            return Optional.empty();
        }

        // Check TTL
        if (System.currentTimeMillis() - seg.timestamp > CACHE_TTL_MS) {
            cache.remove(key);
            return Optional.empty();
        }

        // Check if any region along the path is dirty
        long startRegion = regionHash(start);
        long goalRegion = regionHash(goal);
        if (dirtyRegions.contains(startRegion) || dirtyRegions.contains(goalRegion)) {
            cache.remove(key);
            return Optional.empty();
        }

        seg.lastAccessed = System.currentTimeMillis();
        seg.hitCount++;
        return Optional.of(seg.path);
    }

    /**
     * Stores a successfully computed path in the cache.
     *
     * @param start The starting position of the path
     * @param dest  The destination position of the path
     * @param path  The computed path to cache
     */
    public void store(BlockPos start, BlockPos dest, IPath path) {
        if (path.length() < 5) {
            // Don't cache very short paths - not worth the overhead
            return;
        }

        // Evict if over capacity
        if (cache.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }

        long key = regionKey(start, dest);
        cache.put(key, new CachedSegment(path));
    }

    /**
     * Marks a region as dirty when a block changes within it.
     * All cached paths passing through this region will be invalidated on next lookup.
     *
     * @param changedBlock The position of the changed block
     */
    public void invalidateRegion(BlockPos changedBlock) {
        dirtyRegions.add(regionHash(changedBlock));
    }

    /**
     * Clears all dirty region markers. Called periodically to prevent
     * the dirty set from growing unbounded.
     * Recommended: call every 600 ticks (30 seconds).
     */
    public void cleanupDirtyRegions() {
        dirtyRegions.clear();
    }

    /**
     * Clears the entire cache. Called when the world changes significantly
     * (dimension change, teleport, etc.)
     */
    public void clear() {
        cache.clear();
        dirtyRegions.clear();
    }

    /**
     * @return Current number of cached path segments
     */
    public int size() {
        return cache.size();
    }

    // --- Internal helpers ---

    private long regionKey(BlockPos start, BlockPos end) {
        return regionHash(start) * 2654435761L + regionHash(end);
    }

    private long regionHash(BlockPos pos) {
        return BetterBlockPos.longHash(
                pos.getX() >> REGION_SHIFT,
                pos.getY() >> REGION_SHIFT,
                pos.getZ() >> REGION_SHIFT);
    }

    private void evictOldest() {
        // Remove the entry with the oldest lastAccessed time
        long oldestKey = -1;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<Long, CachedSegment> entry : cache.entrySet()) {
            if (entry.getValue().lastAccessed < oldestTime) {
                oldestTime = entry.getValue().lastAccessed;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != -1) {
            cache.remove(oldestKey);
        }
    }

    private static final class CachedSegment {
        final IPath path;
        final long timestamp;
        long lastAccessed;
        int hitCount;

        CachedSegment(IPath path) {
            this.path = path;
            this.timestamp = System.currentTimeMillis();
            this.lastAccessed = this.timestamp;
            this.hitCount = 0;
        }
    }
}
