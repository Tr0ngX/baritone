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

package baritone.utils;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import baritone.behavior.Behavior;
import baritone.api.utils.Helper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.KeyboardInput;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * An interface with the game's control system allowing the ability to
 * force down certain controls, having the same effect as if we were actually
 * physically forcing down the assigned key.
 *
 * @author Brady
 * @since 7/31/2018
 */
public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {

    /**
     * Maps inputs to whether or not we are forcing their state down.
     */
    private final Map<Input, Boolean> inputForceStateMap = new HashMap<>();

    private final BlockBreakHelper blockBreakHelper;
    private final BlockPlaceHelper blockPlaceHelper;
    private boolean f4WasDown = false;

    public InputOverrideHandler(Baritone baritone) {
        super(baritone);
        this.blockBreakHelper = new BlockBreakHelper(baritone.getPlayerContext());
        this.blockPlaceHelper = new BlockPlaceHelper(baritone.getPlayerContext());
    }

    /**
     * Returns whether or not we are forcing down the specified {@link Input}.
     *
     * @param input The input
     * @return Whether or not it is being forced down
     */
    @Override
    public final boolean isInputForcedDown(Input input) {
        return input == null ? false : this.inputForceStateMap.getOrDefault(input, false);
    }

    /**
     * Sets whether or not the specified {@link Input} is being forced down.
     *
     * @param input  The {@link Input}
     * @param forced Whether or not the state is being forced
     */
    @Override
    public final void setInputForceState(Input input, boolean forced) {
        this.inputForceStateMap.put(input, forced);
    }

    /**
     * Clears the override state for all keys
     */
    @Override
    public final void clearAllKeys() {
        this.inputForceStateMap.clear();
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        checkF4Key();
        if (isInputForcedDown(Input.CLICK_LEFT)) {
            setInputForceState(Input.CLICK_RIGHT, false);
        }
        blockBreakHelper.tick(isInputForcedDown(Input.CLICK_LEFT));
        blockPlaceHelper.tick(isInputForcedDown(Input.CLICK_RIGHT));

        if (inControl()) {
            if (ctx.player().input.getClass() != PlayerMovementInput.class) {
                ctx.player().input = new PlayerMovementInput(this);
            }
        } else {
            if (ctx.player().input.getClass() == PlayerMovementInput.class) { // allow other movement inputs that aren't this one, e.g. for a freecam
                ctx.player().input = new KeyboardInput(ctx.minecraft().options);
            }
        }
        // only set it if it was previously incorrect
        // gotta do it this way, or else it constantly thinks you're beginning a double tap W sprint lol
    }

    private void checkF4Key() {
        if (ctx.minecraft() == null || ctx.minecraft().getWindow() == null || ctx.player() == null) {
            return;
        }
        if (ctx.minecraft().screen != null) {
            f4WasDown = true;
            return;
        }
        try {
            com.mojang.blaze3d.platform.Window window = ctx.minecraft().getWindow();
            boolean altDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
            
            // 1. Kiểm tra phím mở AutoMine Menu (qua KeyMapping tùy chỉnh trong Controls -> Key Binds)
            boolean keyTriggered = BaritoneKeyBindings.KEY_AUTOMINE_GUI.consumeClick();
            if (!keyTriggered && BaritoneKeyBindings.KEY_AUTOMINE_GUI.isDefault()) {
                boolean f4Down = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F4);
                if (f4Down && !f4WasDown) {
                    keyTriggered = true;
                }
                f4WasDown = f4Down;
            } else {
                f4WasDown = BaritoneKeyBindings.KEY_AUTOMINE_GUI.isDown();
            }

            if (!altDown && keyTriggered) {
                ctx.minecraft().setScreen(new AutoMineScreen(baritone));
            }

            // 2. Phím Hủy / Dừng Baritone (nếu người chơi có gán phím)
            if (BaritoneKeyBindings.KEY_CANCEL.consumeClick()) {
                baritone.getPathingBehavior().cancelEverything();
                baritone.getPathingBehavior().forceCancel();
                baritone.getMineProcess().cancel();
                clearAllKeys();
                blockBreakHelper.stopBreakingBlock();
                if (ctx.player() != null && ctx.player().containerMenu != ctx.player().inventoryMenu) {
                    ctx.player().closeContainer();
                }
                Helper.HELPER.logDirect("§c[Baritone] Đã hủy / dừng toàn bộ tiến trình!");
            }

            // 3. Phím Tạm dừng Baritone (nếu người chơi có gán phím)
            if (BaritoneKeyBindings.KEY_PAUSE.consumeClick()) {
                baritone.getPathingBehavior().requestPause();
                Helper.HELPER.logDirect("§e[Baritone] Đã yêu cầu tạm dừng tiến trình!");
            }
        } catch (Throwable ignored) {}
    }

    private boolean inControl() {
        for (Input input : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SNEAK, Input.JUMP}) {
            if (isInputForcedDown(input)) {
                return true;
            }
        }
        // if we are not primary (a bot) we should set the movementinput even when idle (not pathing)
        return baritone.getPathingBehavior().isPathing() || baritone != BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public BlockBreakHelper getBlockBreakHelper() {
        return blockBreakHelper;
    }
}
