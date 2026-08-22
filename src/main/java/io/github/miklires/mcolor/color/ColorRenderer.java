package io.github.miklires.mcolor.color;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

public final class ColorRenderer {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().build();

    private ColorRenderer() { }

    public static Component component(PlayerColor profile, String text) {
        if (profile == null || text == null || text.isEmpty()) return Component.text(text == null ? "" : text);
        int[] codePoints = text.codePoints().toArray();
        List<String> stops = profile.renderColors();
        TextComponent.Builder result = Component.text();
        for (int index = 0; index < codePoints.length; index++) {
            double position = codePoints.length == 1 ? 0 : (double) index / (codePoints.length - 1);
            result.append(Component.text(new String(Character.toChars(codePoints[index])))
                    .color(interpolate(stops, position))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return result.build();
    }

    public static String miniMessage(PlayerColor profile, String text) {
        if (profile == null) return MINI_MESSAGE.escapeTags(text == null ? "" : text);
        String safeText = MINI_MESSAGE.escapeTags(text == null ? "" : text);
        if (profile.kind() == io.github.miklires.mcolor.api.ColorProfile.Kind.SOLID) {
            return "<color:" + profile.colors().getFirst() + ">" + safeText + "</color>";
        }
        return "<gradient:" + String.join(":", profile.renderColors()) + ">" + safeText + "</gradient>";
    }

    public static String legacy(PlayerColor profile, String text) {
        return LEGACY.serialize(component(profile, text));
    }

    private static TextColor interpolate(List<String> stops, double position) {
        if (stops.size() == 1) return TextColor.fromHexString(stops.getFirst());
        double scaled = position * (stops.size() - 1);
        int leftIndex = Math.min((int) Math.floor(scaled), stops.size() - 2);
        double local = scaled - leftIndex;
        TextColor left = TextColor.fromHexString(stops.get(leftIndex));
        TextColor right = TextColor.fromHexString(stops.get(leftIndex + 1));
        int red = lerp(left.red(), right.red(), local);
        int green = lerp(left.green(), right.green(), local);
        int blue = lerp(left.blue(), right.blue(), local);
        return TextColor.color(red, green, blue);
    }

    private static int lerp(int left, int right, double amount) {
        return (int) Math.round(left + (right - left) * amount);
    }
}
