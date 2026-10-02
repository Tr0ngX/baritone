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

package baritone.api.process;

import net.minecraft.core.BlockPos;

/**
 * An optimized, ultra-lightweight process dedicated to automatically mining sand
 * without heavy chunk scanning or path recalculation delays.
 *
 * @author leijurv
 */
public interface ISandProcess extends IBaritoneProcess {

    /**
     * Begin to auto-mine sand in the specified area from specified location with optional target count.
     *
     * @param targetCount Max number of sand blocks to mine (0 for unlimited)
     * @param range       The distance from center to mine sand from (0 for unlimited)
     * @param pos         The center position to base the range from (null for player feet)
     */
    void sand(int targetCount, int range, BlockPos pos);

    /**
     * Begin to auto-mine sand with a target count.
     *
     * @param targetCount Max number of sand blocks to mine (0 for unlimited)
     */
    default void sand(int targetCount) {
        sand(targetCount, 0, null);
    }

    /**
     * Begin to auto-mine sand without limit.
     */
    default void sand() {
        sand(0, 0, null);
    }

    /**
     * Stop the sand mining process.
     */
    void cancel();
}
