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
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.BlockOptionalMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class MineCommand extends Command {

    public MineCommand(IBaritone baritone) {
        super(baritone, "mine");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        int quantity = args.getAsOrDefault(Integer.class, 0);
        List<BlockOptionalMeta> boms = new ArrayList<>();
        if (args.hasAny()) {
            while (args.hasAny()) {
                BlockOptionalMeta bom = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                boms.add(bom);
                Block block = bom.getBlock();
                if (block == Blocks.DIAMOND_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_DIAMOND_ORE));
                else if (block == Blocks.DEEPSLATE_DIAMOND_ORE) boms.add(new BlockOptionalMeta(Blocks.DIAMOND_ORE));
                else if (block == Blocks.IRON_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_IRON_ORE));
                else if (block == Blocks.DEEPSLATE_IRON_ORE) boms.add(new BlockOptionalMeta(Blocks.IRON_ORE));
                else if (block == Blocks.GOLD_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_GOLD_ORE));
                else if (block == Blocks.DEEPSLATE_GOLD_ORE) boms.add(new BlockOptionalMeta(Blocks.GOLD_ORE));
                else if (block == Blocks.COPPER_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_COPPER_ORE));
                else if (block == Blocks.DEEPSLATE_COPPER_ORE) boms.add(new BlockOptionalMeta(Blocks.COPPER_ORE));
                else if (block == Blocks.REDSTONE_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_REDSTONE_ORE));
                else if (block == Blocks.DEEPSLATE_REDSTONE_ORE) boms.add(new BlockOptionalMeta(Blocks.REDSTONE_ORE));
                else if (block == Blocks.LAPIS_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_LAPIS_ORE));
                else if (block == Blocks.DEEPSLATE_LAPIS_ORE) boms.add(new BlockOptionalMeta(Blocks.LAPIS_ORE));
                else if (block == Blocks.EMERALD_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_EMERALD_ORE));
                else if (block == Blocks.DEEPSLATE_EMERALD_ORE) boms.add(new BlockOptionalMeta(Blocks.EMERALD_ORE));
                else if (block == Blocks.COAL_ORE) boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_COAL_ORE));
                else if (block == Blocks.DEEPSLATE_COAL_ORE) boms.add(new BlockOptionalMeta(Blocks.COAL_ORE));
            }
        } else {
            // Default: Kim Cương, Lapis, Đá Đỏ
            boms.add(new BlockOptionalMeta(Blocks.DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.LAPIS_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_LAPIS_ORE));
            boms.add(new BlockOptionalMeta(Blocks.REDSTONE_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_REDSTONE_ORE));
            
            Baritone.settings().strictLiquidCheck.value = true;
            Baritone.settings().allowDownward.value = true;
            Baritone.settings().exploreForBlocks.value = true;
            Baritone.settings().legitMine.value = false;
            Baritone.settings().autoTool.value = true;
            Baritone.settings().allowInventory.value = true;
            Baritone.settings().avoidance.value = true;
            Baritone.settings().mobAvoidanceRadius.value = 10;
            Baritone.settings().mobSpawnerAvoidanceRadius.value = 16;
            Baritone.settings().primaryTimeoutMS.value = 2500L;
            Baritone.settings().failureTimeoutMS.value = 4000L;
            Baritone.settings().planAheadPrimaryTimeoutMS.value = 2500L;
            Baritone.settings().planAheadFailureTimeoutMS.value = 4000L;
            Baritone.settings().movementTimeoutTicks.value = 140;
            Baritone.settings().legitMineYLevel.value = -58;
            Baritone.settings().exploreMaintainY.value = -58;
        }
        BaritoneAPI.getProvider().getWorldScanner().repack(ctx);
        logDirect(String.format("Mining %s", boms.toString()));
        baritone.getMineProcess().mine(quantity, boms.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        args.getAsOrDefault(Integer.class, 0);
        while (args.has(2)) {
            args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        }
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Mine some blocks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The mine command allows you to tell Baritone to search for and mine individual blocks.",
                "",
                "The specified blocks can be ores, or any other block.",
                "",
                "Also see the legitMine settings (see #set l legitMine).",
                "",
                "Usage:",
                "> mine diamond_ore - Mines all diamonds it can find."
        );
    }
}
