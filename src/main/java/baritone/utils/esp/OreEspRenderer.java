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

package baritone.utils.esp;

import baritone.Baritone;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Vẽ hộp ESP quặng. Render mỗi frame chỉ đọc snapshot do controller chốt,
 * không truy vấn cache hay tính toán gì thêm ở đây.
 */
public final class OreEspRenderer implements AbstractGameEventListener, IRenderer {

    private final Baritone baritone;
    private final OreEspController controller;

    OreEspRenderer(Baritone baritone, OreEspController controller) {
        this.baritone = baritone;
        this.controller = controller;
        baritone.getGameEventHandler().registerEventListener(this);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        try {
            if (!controller.isActive()) {
                return;
            }
            if (baritone.getPlayerContext().world() == null) {
                return;
            }
            List<OreEspController.EspBox> boxes = controller.getSnapshot();
            if (boxes.isEmpty()) {
                return;
            }
            boolean xray = Baritone.settings().oreEspXray.value;
            float lineWidth = Baritone.settings().goalRenderLineWidthPixels.value;
            BufferBuilder buffer = null;
            for (OreEspController.EspBox box : boxes) {
                if (buffer == null) {
                    buffer = IRenderer.startLines(box.color, 0.9f);
                } else {
                    IRenderer.glColor(box.color, 0.9f);
                }
                IRenderer.emitAABB(buffer, event.getModelViewStack(), new AABB(box.pos), .002D, lineWidth);
            }
            if (buffer != null) {
                IRenderer.endLines(buffer, xray);
            }
        } catch (Throwable ignored) {}
    }
}
