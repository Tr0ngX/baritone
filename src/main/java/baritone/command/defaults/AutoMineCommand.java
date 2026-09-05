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
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AutoMineCommand extends Command {

    public AutoMineCommand(IBaritone baritone) {
        super(baritone, "automine", "diamondmine", "orefarm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // === CẤU HÌNH TỐI ƯU CHO SERVER (BYPASS ANTI-CHEAT KINGMC) ===
        Baritone.settings().autoTool.value = true;
        Baritone.settings().assumeExternalAutoTool.value = false;
        Baritone.settings().allowInventory.value = true;
        Baritone.settings().ticksBetweenInventoryMoves.value = 1;
        Baritone.settings().strictLiquidCheck.value = true;
        Baritone.settings().antiLavaOnly.value = true;
        Baritone.settings().waterCheck.value = true;
        Baritone.settings().allowDownward.value = true;
        Baritone.settings().allowBreak.value = true;
        Baritone.settings().allowPlace.value = true;
        Baritone.settings().allowPlaceInFluidsSource.value = true;
        Baritone.settings().allowPlaceInFluidsFlow.value = true;
        Baritone.settings().allowSprint.value = true;
        Baritone.settings().sprintAscends.value = true;
        Baritone.settings().overshootTraverse.value = true;
        Baritone.settings().sprintInWater.value = true;
        Baritone.settings().assumeStep.value = false;
        Baritone.settings().allowWaterBucketFall.value = true;
        Baritone.settings().preferWaterBucketOverDigging.value = true;

        Baritone.settings().legitMine.value = false;
        Baritone.settings().exploreForBlocks.value = true;
        Baritone.settings().mineScanDroppedItems.value = true;
        Baritone.settings().blacklistClosestOnFailure.value = true;
        Baritone.settings().mineMaxOreLocationsCount.value = 256;
        Baritone.settings().maxCachedWorldScanCount.value = 1000;
        Baritone.settings().extendCacheOnThreshold.value = true;
        Baritone.settings().mineDropLoiterDurationMSThanksLouca.value = 200L;

        Baritone.settings().blockBreakSpeed.value = 6;
        Baritone.settings().rightClickSpeed.value = 1;
        Baritone.settings().blockBreakAdditionalPenalty.value = 2.0;
        Baritone.settings().blockPlacementPenalty.value = 0.0;
        Baritone.settings().jumpPenalty.value = 0.0;

        Baritone.settings().useAnytimeSearch.value = true;
        Baritone.settings().anytimeSearchEpsilon.value = 2.0;
        Baritone.settings().planningTickLookahead.value = 400;
        Baritone.settings().mineGoalUpdateInterval.value = 5;
        Baritone.settings().primaryTimeoutMS.value = 2500L;
        Baritone.settings().failureTimeoutMS.value = 4000L;
        Baritone.settings().planAheadPrimaryTimeoutMS.value = 2500L;
        Baritone.settings().planAheadFailureTimeoutMS.value = 4000L;
        Baritone.settings().movementTimeoutTicks.value = 140;

        Baritone.settings().avoidance.value = true;
        Baritone.settings().mobAvoidanceRadius.value = 14;
        Baritone.settings().mobAvoidanceCoefficient.value = 500.0;
        Baritone.settings().mobSpawnerAvoidanceRadius.value = 16;
        Baritone.settings().mobSpawnerAvoidanceCoefficient.value = 500.0;
        Baritone.settings().autoEat.value = true;
        Baritone.settings().autoEatThreshold.value = 19;
        Baritone.settings().autoTotem.value = true;
        Baritone.settings().autoShulkerStorage.value = true;
        Baritone.settings().autoDrop.value = true;

        Baritone.settings().allowParkour.value = true;
        Baritone.settings().allowParkourPlace.value = true;
        Baritone.settings().allowParkourAscend.value = true;
        Baritone.settings().allowDiagonalAscend.value = true;
        Baritone.settings().allowDiagonalDescend.value = true;
        Baritone.settings().tunnelSprintJump.value = true;
        Baritone.settings().fastJump.value = true;

        Baritone.settings().legitMineYLevel.value = -58;
        Baritone.settings().exploreMaintainY.value = -58;
        Baritone.settings().mineStrictOneDirection.value = true;

        List<BlockOptionalMeta> boms = new ArrayList<>();
        if (args.hasAny()) {
            while (args.hasAny()) {
                boms.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
            }
        } else {
            // Default: Kim Cương (Diamond), Lapis, Đá Đỏ (Redstone), Ngọc Lục Bảo (Emerald)
            boms.add(new BlockOptionalMeta(Blocks.DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_DIAMOND_ORE));
            boms.add(new BlockOptionalMeta(Blocks.LAPIS_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_LAPIS_ORE));
            boms.add(new BlockOptionalMeta(Blocks.REDSTONE_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_REDSTONE_ORE));
            boms.add(new BlockOptionalMeta(Blocks.EMERALD_ORE));
            boms.add(new BlockOptionalMeta(Blocks.DEEPSLATE_EMERALD_ORE));
        }

        BaritoneAPI.getProvider().getWorldScanner().repack(ctx);
        logDirect("§a================================================================");
        logDirect("§a[AutoMine] Đã kích hoạt chế độ Tự Động Đào Quặng & Hầm Thông Minh!");
        logDirect("§a  ✔ Tầng mục tiêu: §eY = -58 §a(Tự động đi xuống an toàn, né Lava 100%)");
        logDirect("§a  ✔ Mục tiêu quặng: §bKim Cương§a, §9Lapis§a, §cĐá Đỏ§a, §2Ngọc Lục Bảo");
        logDirect("§a  ✔ Tự động TRÁNH ZOMBIE & QUÁI VẬT (Bán kính 10 block)");
        logDirect("§a  ✔ Tự động LẮP TOTEM BẤT TỬ vào tay phụ (Auto-Totem)");
        logDirect("§a  ✔ Tự động ĂN KHI ĐÓI (<5 cục thịt) & HỒI MÁU khi bị thương");
        logDirect("§a  ✔ Tự đào hầm ngang ➔ Gặp quặng tự rẽ đào sạch ➔ Tiếp tục đào hầm!");
        logDirect("§a  ✔ Chạy liên tục không bao giờ dừng/hủy lệnh!");
        logDirect("§a================================================================");
        baritone.getMineProcess().mine(0, boms.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        while (args.has(2)) {
            args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        }
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Auto descend to Y=-58, tunnel, and mine Diamonds/Lapis/Redstone safely without stopping";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The automine command safely descends down to Y=-58, digs a horizontal tunnel, and automatically mines all Diamonds, Lapis, and Redstone nearby.",
                "Whenever ores are cleared, it seamlessly resumes the tunnel forward infinitely without cancelling.",
                "",
                "Usage:",
                "> automine - Mines Diamonds, Lapis, and Redstone at Y=-58.",
                "> automine <blocks...> - Custom target blocks with auto-tunneling."
        );
    }
}
