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
import baritone.api.utils.SettingsUtil;
import baritone.utils.AutoLogoutTracker;
import baritone.utils.AutoMineConfig;
import baritone.utils.AutoMineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import baritone.api.event.events.type.EventState;
import net.minecraft.core.NonNullList;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
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
    private int lavaTicks = 0;

    public EmergencySafetyBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT || event.getState() == EventState.PRE) {
            return;
        }

        tickCount++;

        boolean checkDanger = Baritone.settings().autoLogoutOnDanger.value;
        boolean checkPlayer = Baritone.settings().autoLogoutOnPlayer.value;
        if (!checkDanger && !checkPlayer) {
            lavaTicks = 0;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            lavaTicks = 0;
            return;
        }
        // Không logout nếu đang ở chế độ Sáng tạo (Creative) hoặc Khán giả (Spectator)
        if (ctx.player().isCreative() || ctx.player().isSpectator()) {
            lavaTicks = 0;
            return;
        }

        // 1. Kiểm tra trạng thái rơi vào và thực sự bốc cháy trong hồ Lava (chỉ khi checkDanger bật):
        // ĐIỀU KIỆN CHÍNH XÁC:
        // - Người chơi phải thực sự đang bốc cháy (isOnFire() || getRemainingFireTicks() > 0)
        // - Người chơi phải đang tiếp xúc trong khối Lava (isInLava() || getBlockState(playerFeet()).is(Blocks.LAVA))
        // - Người chơi KHÔNG có hiệu ứng kháng lửa (MobEffects.FIRE_RESISTANCE)
        if (checkDanger) {
            boolean hasFireResistance = ctx.player().hasEffect(MobEffects.FIRE_RESISTANCE);
            boolean isBurning = ctx.player().isOnFire() || ctx.player().getRemainingFireTicks() > 0;
            boolean inLavaBlock = ctx.player().isInLava() || ctx.world().getBlockState(ctx.playerFeet()).is(Blocks.LAVA);

            if (!hasFireResistance && isBurning && inLavaBlock) {
                lavaTicks++;
            } else {
                lavaTicks = 0;
            }
        } else {
            lavaTicks = 0;
        }

        String dangerReason = null;

        // TRƯỜNG HỢP 1: PHÁT HIỆN NGƯỜI CHƠI ĐẾN GẦN (KỂ CẢ DÙNG THUỐC TÀNG HÌNH / INVIS)
        // Ưu tiên cao nhất: nếu phát hiện player khác xâm nhập vùng an toàn -> Logout ngay lập tức!
        if (checkPlayer) {
            AutoLogoutTracker.DetectedPlayerInfo playerThreat = AutoLogoutTracker.scanForNearbyPlayer(ctx);
            if (playerThreat != null) {
                dangerReason = "Phát hiện người chơi: " + playerThreat.getFormattedDescription();
            }
        }

        // TRƯỜNG HỢP 2: LAVA (chỉ khi checkDanger bật, phải bốc cháy liên tục hơn 3 giây = 60 ticks)
        if (dangerReason == null && checkDanger && lavaTicks >= 60) {
            dangerReason = "Bị bốc cháy trong hồ LAVA liên tục quá 3 giây!";
        }

        // TRƯỜNG HỢP 3: QUÁI ĐÁNH, ĐÓI, TÉ NGÃ, v.v. (Phải Hết Totem + Nửa thanh máu, chỉ khi checkDanger bật)
        if (dangerReason == null && checkDanger) {
            int totemCount = getTotemCount();
            if (totemCount == 0) {
                float health = ctx.player().getHealth();
                float maxHealth = ctx.player().getMaxHealth();
                float threshold = Baritone.settings().autoLogoutHealthThreshold.value;
                boolean lowHealth = (health <= maxHealth * threshold) || (health <= 10.0f);
                if (lowHealth) {
                    String cause = detectDamageCause(ctx);
                    dangerReason = cause + " (Máu còn: " + String.format(java.util.Locale.ROOT, "%.1f", health) + "/" + (int) maxHealth + " HP) và ĐÃ HẾT TOTEM!";
                }
            }
        }

        if (dangerReason != null) {
            lavaTicks = 0;
            AutoLogoutTracker.performAutoLogout(ctx, dangerReason);
        }
    }

    public static String detectDamageCause(baritone.api.utils.IPlayerContext ctx) {
        if (ctx == null || ctx.player() == null) return "Bị mất máu nguy hiểm";

        // 1. Quái vật gây sát thương gần nhất
        try {
            LivingEntity mob = ctx.player().getLastHurtByMob();
            if (mob != null) {
                return "Bị quái vật (" + mob.getDisplayName().getString() + ") tấn công";
            }
        } catch (Throwable ignored) {}

        // 2. Quái vật ở cự ly nguy hiểm sát người chơi (5 block)
        if (ctx.world() != null) {
            try {
                var monsters = ctx.world().getEntitiesOfClass(
                        Monster.class,
                        ctx.player().getBoundingBox().inflate(5.0)
                );
                if (!monsters.isEmpty()) {
                    return "Bị quái vật (" + monsters.get(0).getDisplayName().getString() + ") tấn công";
                }
            } catch (Throwable ignored) {}
        }

        // 3. Đói ăn kiệt sức (thanh đói = 0)
        try {
            if (ctx.player().getFoodData().getFoodLevel() == 0) {
                return "Bị ĐÓI ĂN kiệt sức (Thanh đói = 0)";
            }
        } catch (Throwable ignored) {}

        // 4. Bị té ngã từ trên cao (Fall Damage)
        try {
            if (ctx.player().fallDistance > 2.0f) {
                return "Bị TÉ NGÃ từ trên cao (Fall Damage)";
            }
        } catch (Throwable ignored) {}

        // 5. Bị bốc cháy / lửa thiêu
        try {
            if (ctx.player().isOnFire()) {
                return "Bị BỐC CHÁY (Lửa thiêu)";
            }
        } catch (Throwable ignored) {}

        // 6. Chết đuối / ngạt nước
        try {
            if (ctx.player().getAirSupply() <= 0) {
                return "Bị CHẾT ĐUỐI (Ngạt nước)";
            }
        } catch (Throwable ignored) {}

        // 7. Ngạt thở trong tường
        try {
            if (ctx.player().isInWall()) {
                return "Bị NGẠT THỞ trong tường";
            }
        } catch (Throwable ignored) {}

        // 8. DamageSource từ Minecraft nếu có
        try {
            DamageSource src = ctx.player().getLastDamageSource();
            if (src != null) {
                String msgId = src.getMsgId();
                if (msgId != null) {
                    if (msgId.contains("mob") || msgId.contains("arrow")) {
                        return "Bị quái vật tấn công";
                    } else if (msgId.contains("fall")) {
                        return "Bị TÉ NGÃ từ trên cao (Fall Damage)";
                    } else if (msgId.contains("starve")) {
                        return "Bị ĐÓI ĂN kiệt sức";
                    } else if (msgId.contains("explosion")) {
                        return "Bị NỔ sát thương";
                    } else if (msgId.contains("fire") || msgId.contains("lava")) {
                        return "Bị BỐC CHÁY (Lửa thiêu)";
                    } else if (msgId.contains("drown")) {
                        return "Bị CHẾT ĐUỐI";
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "Bị mất máu nguy hiểm";
    }

    @Override
    public void onWorldEvent(baritone.api.event.events.WorldEvent event) {
        // Khi thoát thế giới hoặc disconnect: tự động tắt đi để lần sau vào lại game không bị kick lặp lại
        if (event.getWorld() == null) {
            Baritone.settings().autoLogoutOnDanger.value = false;
            Baritone.settings().autoLogoutOnPlayer.value = false;
            AutoMineScreen.optAutoLogout = false;
            AutoMineConfig.save();
            SettingsUtil.save(Baritone.settings());
        } else {
            AutoLogoutTracker.onWorldJoined();
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
