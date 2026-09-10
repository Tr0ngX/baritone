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
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.utils.AutoMineConfig;
import baritone.utils.AutoMineScreen;
import baritone.utils.hud.BottingDashboardOverlay;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class BottingCommand extends Command {

    public BottingCommand(IBaritone baritone) {
        super(baritone, "botting", "blackout", "ecobot", "farmnang");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);

        if (!args.hasAny()) {
            boolean newState = !Baritone.settings().bottingMode.value;
            setBottingMode(newState);
            return;
        }

        String firstArg = args.getString().toLowerCase();

        if (firstArg.equals("on") || firstArg.equals("true") || firstArg.equals("enable") || firstArg.equals("1")) {
            setBottingMode(true);
            return;
        }

        if (firstArg.equals("off") || firstArg.equals("false") || firstArg.equals("disable") || firstArg.equals("0")) {
            setBottingMode(false);
            return;
        }

        if (firstArg.equals("name") || firstArg.equals("reveal") || firstArg.equals("spoiler")) {
            BottingDashboardOverlay.toggleNameSpoiler();
            boolean rev = BottingDashboardOverlay.isNameSpoilerRevealed();
            logDirect(rev
                    ? "\u00A7a[Botting] \u0110\u00E3 M\u1EDF \u00F4 spoiler hi\u1EC3n th\u1ECB t\u00EAn t\u00E0i kho\u1EA3n tr\u00EAn m\u00E0n h\u00ECnh!"
                    : "\u00A7e[Botting] \u0110\u00E3 \u0110\u00D3NG \u00F4 spoiler che t\u00EAn t\u00E0i kho\u1EA3n tr\u00EAn m\u00E0n h\u00ECnh!");
            return;
        }

        if (firstArg.equals("fps")) {
            if (!args.hasAny()) {
                logDirect("\u00A7e[Botting] Gi\u1EDBi h\u1EA1n FPS botting hi\u1EC7n t\u1EA1i: " + Baritone.settings().bottingFps.value + " FPS");
                return;
            }
            try {
                int fps = Integer.parseInt(args.getString());
                if (fps <= 0) fps = 10;
                Baritone.settings().bottingFps.value = fps;
                AutoMineScreen.optBottingFps = fps;
                AutoMineConfig.save();
                logDirect("\u00A7a[Botting] \u0110\u00E3 ch\u1EC9nh gi\u1EDBi h\u1EA1n FPS Botting th\u00E0nh: \u00A7f" + fps + " FPS");
            } catch (NumberFormatException e) {
                logDirect("\u00A7c[Botting] Gi\u00E1 tr\u1ECB FPS kh\u00F4ng h\u1EE3p l\u1EC7! Vui l\u00F2ng nh\u1EADp s\u1ED1 (v\u00ED d\u1EE5: #botting fps 10)");
            }
            return;
        }

        // Kiem tra neu nguoi dung go thang so FPS: #botting 10
        try {
            int fps = Integer.parseInt(firstArg);
            if (fps <= 0) fps = 10;
            Baritone.settings().bottingFps.value = fps;
            AutoMineScreen.optBottingFps = fps;
            setBottingMode(true);
            logDirect("\u00A7a[Botting] \u0110\u00E3 ch\u1EC9nh FPS th\u00E0nh " + fps + " v\u00E0 k\u00EDch ho\u1EA1t Ch\u1EBF \u0111\u1ED9 Botting!");
        } catch (NumberFormatException e) {
            logDirect("\u00A7eC\u00FA ph\u00E1p: \u00A7f#botting [on/off] \u00A77ho\u1EB7c \u00A7f#botting fps <s\u1ED1> \u00A77(v\u00ED d\u1EE5: #botting fps 10)");
        }
    }

    private void setBottingMode(boolean enabled) {
        Baritone.settings().bottingMode.value = enabled;
        AutoMineScreen.optBottingMode = enabled;
        AutoMineConfig.save();
        if (enabled) {
            logDirect("\u00A7a[Botting] \u0110\u00C3 B\u1EACT CH\u1EBE \u0110\u1ED8 BOTTING (M\u00E0n h\u00ECnh \u0111en, ng\u1EAFt render 3D, 10 FPS, c\u1EF1c t\u1ED1i \u01B0u GPU)!");
            logDirect("\u00A77(\u1EA2nh ch\u1EE5p Discord Webhook v\u1EABn t\u1EF1 \u0111\u1ED9ng ch\u1EE5p h\u00ECnh \u1EA3nh 3D th\u1EF1c t\u1EBF)");
        } else {
            logDirect("\u00A7c[Botting] \u0110\u00C3 T\u1EAET CH\u1EBE \u0110\u1ED8 BOTTING (Kh\u00F4i ph\u1EE5c hi\u1EC3n th\u1ECB th\u1EBF gi\u1EDBi 3D b\u00ECnh th\u01B0\u1EDDng)!");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        try {
            if (args.hasExactlyOne()) {
                String prefix = args.getString().toLowerCase();
                return Stream.of("on", "off", "name", "fps", "10", "20")
                        .filter(s -> s.startsWith(prefix));
            }
            if (args.has(2)) {
                String first = args.getString();
                if (first.equalsIgnoreCase("fps")) {
                    String prefix = args.getString().toLowerCase();
                    return Stream.of("5", "10", "15", "20", "30")
                            .filter(s -> s.startsWith(prefix));
                }
            }
        } catch (Exception ignored) {}
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "B\u1EADt/t\u1EAFt ch\u1EBF \u0111\u1ED9 botting c\u1EF1c t\u1ED1i \u01B0u (\u0111en m\u00E0n h\u00ECnh, ng\u1EAFt 3D, 10 FPS)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Ch\u1EBF \u0111\u1ED9 Botting m\u00E0n h\u00ECnh \u0111en c\u1EF1c t\u1ED1i \u01B0u khi treo farm nhi\u1EC1u t\u00E0i kho\u1EA3n:",
                "  - Ng\u1EAFt 100% render th\u1EBF gi\u1EDBi 3D (GPU ~0%).",
                "  - Kh\u00F3a framerate v\u1EC1 10 FPS.",
                "  - V\u1EABn ch\u1EE5p \u1EA3nh m\u00E0n h\u00ECnh 3D th\u1EF1c t\u1EBF khi g\u1EEDi b\u00E1o c\u00E1o Webhook Discord.",
                "",
                "S\u1EED d\u1EE5ng:",
                "  #botting - B\u1EADt/t\u1EAFt nhanh ch\u1EBF \u0111\u1ED9",
                "  #botting on / off - B\u1EADt ho\u1EB7c t\u1EAFt",
                "  #botting fps <s\u1ED1> - \u0110\u1ED5i m\u1EE9c FPS limit (m\u1EB7c \u0111\u1ECBnh: 10)",
                "  #botting 10 - \u0110\u1EB7t 10 FPS v\u00E0 b\u1EADt ch\u1EBF \u0111\u1ED9"
        );
    }
}
