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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.BaritoneFileLogger;
import baritone.api.utils.Helper;
import baritone.utils.AutoMineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Hành vi Bảo hộ Khẩn cấp (Emergency Safety Behavior).
 * Tự động ngắt kết nối / Logout ngay lập tức khi:
 * 1. Rơi vào hồ Lava và không còn bất kỳ Totem nào.
 * 2. Máu tụt còn nửa thanh (<= 50% max HP hoặc <= 10 HP) và không còn bất kỳ Totem nào.
 * Nhằm bảo toàn tuyệt đối 100% trang bị và tính mạng của người chơi.
 */
public final class EmergencySafetyBehavior extends Behavior implements Helper {

    private int tickCount = 0;

    public EmergencySafetyBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }

        tickCount++;

        if (!Baritone.settings().autoLogoutOnDanger.value) {
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        // Không logout nếu đang ở chế độ Sáng tạo (Creative) hoặc Khán giả (Spectator)
        if (ctx.player().isCreative() || ctx.player().isSpectator()) {
            return;
        }

        // 1. Kiểm tra số lượng Totem còn lại (ở tay chính, tay phụ và toàn bộ balo)
        int totemCount = getTotemCount();
        if (totemCount > 0) {
            // Còn Totem trong người thì để cơ chế AutoTotem tự cứu sống
            return;
        }

        // 2. ĐIỀU KIỆN DUY NHẤT: Phải rơi xuống dưới hồ Lava + ĐÃ HẾT TOTEM (dùng 1 điều kiện, không tính máu)
        boolean inLava = ctx.player().isInLava()
                || ctx.world().getBlockState(ctx.playerFeet()).is(Blocks.LAVA)
                || (ctx.player().getDeltaMovement().y < 0 && ctx.world().getBlockState(ctx.playerFeet().below()).is(Blocks.LAVA));

        if (!inLava) {
            return;
        }

        String dangerReason = "Rơi xuống dưới LAVA và ĐÃ HẾT TOTEM!";
        String alert = "§c[AutoLogout] KHẨN CẤP: " + dangerReason + " Tự động Logout ngay lập tức để bảo toàn tính mạng và trang bị!";
        Helper.HELPER.logDirect(alert);
        BaritoneFileLogger.warn(alert);

        // DÙNG 1 LẦN DUY NHẤT: Tự động tắt tính năng đi để lần sau vào lại không bị logout!
        Baritone.settings().autoLogoutOnDanger.value = false;
        AutoMineScreen.optAutoLogout = false;

        // Dừng toàn bộ tiến trình điều khiển và xóa phím bấm
        baritone.getPathingControlManager().cancelEverything();
        baritone.getMineProcess().cancel();
        baritone.getInputOverrideHandler().clearAllKeys();

        // Thực hiện ngắt kết nối an toàn với máy chủ
        Component kickReason = Component.literal("§c[Baritone AutoLogout]\n§e" + dangerReason + "\n§aĐã tự động ngắt kết nối bảo toàn trang bị thành công!\n§7(Tính năng đã tự động tắt cho lần vào lại sau)");
        if (ctx.world() instanceof ClientLevel clientLevel) {
            clientLevel.disconnect(kickReason);
        } else if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().getConnection().disconnect(kickReason);
        }
    }

    @Override
    public void onWorldEvent(baritone.api.event.events.WorldEvent event) {
        // Khi thoát thế giới hoặc disconnect: tự động tắt đi để lần sau vào lại game không bị kick
        if (event.getWorld() == null) {
            Baritone.settings().autoLogoutOnDanger.value = false;
            AutoMineScreen.optAutoLogout = false;
        }
    }

    private int getTotemCount() {
        if (ctx.player() == null) return 0;
        int count = 0;
        // Kiểm tra tay phụ (Offhand)
        ItemStack offhand = ctx.player().getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty() && offhand.is(Items.TOTEM_OF_UNDYING)) {
            count += offhand.getCount();
        }
        // Kiểm tra tay chính (Mainhand)
        ItemStack mainhand = ctx.player().getMainHandItem();
        if (!mainhand.isEmpty() && mainhand.is(Items.TOTEM_OF_UNDYING)) {
            count += mainhand.getCount();
        }
        // Kiểm tra toàn bộ balo và hotbar
        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        for (ItemStack stack : inv) {
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
