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

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.BlockOptionalMeta;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ChopCommand extends Command {

    public ChopCommand(IBaritone baritone) {
        super(baritone, "chop", "wood", "lumber", "lumberjack");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Baritone.settings().autoTool.value = true;
        Baritone.settings().assumeExternalAutoTool.value = false;
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1;
        Baritone.settings().allowDownward.value = true;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowPlace.value = true;
        Baritone.settings().allowPlaceInFluidsSource.value = true;
        Baritone.settings().allowPlaceInFluidsFlow.value = true;

        Baritone.settings().legitMine.value = false;
        Baritone.settings().exploreForBlocks.value = true;
        Baritone.settings().mineScanDroppedItems.value = true;
        Baritone.settings().blacklistClosestOnFailure.value = true;
        Baritone.settings().mineMaxOreLocationsCount.value = 256;
        Baritone.settings().maxCachedWorldScanCount.value = 1000;
        Baritone.settings().extendCacheOnThreshold.value = true;

        // CẤU HÌNH TIMEOUT CHO 1 LẦN TÍNH TOÁN SIÊU DÀI QUA NHIỀU CÂY:
        Baritone.settings().primaryTimeoutMS.value = 20000L;
        Baritone.settings().failureTimeoutMS.value = 30000L;
        Baritone.settings().planAheadPrimaryTimeoutMS.value = 10000L;
        Baritone.settings().planAheadFailureTimeoutMS.value = 15000L;

        Baritone.settings().autoEat.value = true;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = true;
        Baritone.settings().avoidance.value = true;
        Baritone.settings().mobAvoidanceRadius.value = 14;
        Baritone.settings().mobAvoidanceCoefficient.value = 500.0;

        Baritone.settings().allowParkour.value = true;
        Baritone.settings().allowParkourPlace.value = true;
        Baritone.settings().allowParkourAscend.value = true;
        Baritone.settings().allowDiagonalAscend.value = true;
        Baritone.settings().allowDiagonalDescend.value = true;
        Baritone.settings().noPillar.value = false;

        List<BlockOptionalMeta> boms = new ArrayList<>();
        boms.add(new BlockOptionalMeta(Blocks.OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.BIRCH_LOG));
        boms.add(new BlockOptionalMeta(Blocks.SPRUCE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.ACACIA_LOG));
        boms.add(new BlockOptionalMeta(Blocks.JUNGLE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.CHERRY_LOG));
        boms.add(new BlockOptionalMeta(Blocks.MANGROVE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.CRIMSON_STEM));
        boms.add(new BlockOptionalMeta(Blocks.WARPED_STEM));

        boms.add(new BlockOptionalMeta(Blocks.OAK_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.BIRCH_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.SPRUCE_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.DARK_OAK_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.ACACIA_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.JUNGLE_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.CHERRY_WOOD));
        boms.add(new BlockOptionalMeta(Blocks.MANGROVE_WOOD));

        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_BIRCH_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_SPRUCE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_DARK_OAK_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_ACACIA_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_JUNGLE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CHERRY_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_MANGROVE_LOG));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_CRIMSON_STEM));
        boms.add(new BlockOptionalMeta(Blocks.STRIPPED_WARPED_STEM));

        BaritoneAPI.getProvider().getWorldScanner().repack(ctx);
        logDirect("§a[AutoChop] Đã bắt đầu chế độ TỰ ĐỘNG CHẶT CÂY (Lumberjack)!");
        logDirect("§a  ✔ Mục tiêu: Sồi, Bạch Dương, Thông, Sồi Sẫm, Keo, Rừng, Anh Đào, Đước...");
        logDirect("§a  ✔ Tự đổi Rìu (Auto-Tool) & tự động gom gỗ rơi rớt!");
        baritone.getMineProcess().setChopMode(true);
        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Automatically chop all trees/logs nearby";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The chop command automatically searches for and chops all nearby trees of any wood type.",
                "It uses the best axe, breaks from trunk to branches, and collects all dropped wood.",
                "",
                "Aliases: #chop, #wood, #lumber, #lumberjack"
        );
    }
}
