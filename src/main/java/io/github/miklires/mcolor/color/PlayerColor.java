package io.github.miklires.mcolor.color;

import io.github.miklires.mcolor.api.ColorProfile;

import java.util.Arrays;
import java.util.List;

public record PlayerColor(ColorProfile.Kind kind, List<String> colors) {
    private static final List<String> RAINBOW = List.of(
            "#FF5555", "#FFAA00", "#FFFF55", "#55FF55", "#55FFFF", "#5555FF", "#FF55FF");

    public PlayerColor {
        colors = List.copyOf(colors);
        if (colors.size() > 16) throw new IllegalArgumentException("Too many colors");
        int required = switch (kind) {
            case SOLID -> 1;
            case GRADIENT -> 2;
            case RAINBOW -> 0;
        };
        if (colors.size() < required) throw new IllegalArgumentException("Not enough colors for " + kind);
        colors.forEach(value -> ColorParser.hex(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid color: " + value)));
        colors = colors.stream().map(value -> ColorParser.hex(value).orElseThrow()).toList();
    }

    public static PlayerColor solid(String color) {
        return new PlayerColor(ColorProfile.Kind.SOLID, List.of(color));
    }

    public static PlayerColor gradient(List<String> colors) {
        return new PlayerColor(ColorProfile.Kind.GRADIENT, colors);
    }

    public static PlayerColor rainbow() {
        return new PlayerColor(ColorProfile.Kind.RAINBOW, List.of());
    }

    public List<String> renderColors() {
        return kind == ColorProfile.Kind.RAINBOW ? RAINBOW : colors;
    }

    public String encode() {
        return String.join(",", colors);
    }

    public static PlayerColor decode(String kind, String colors) {
        ColorProfile.Kind parsedKind = ColorProfile.Kind.valueOf(kind);
        List<String> parsedColors = colors == null || colors.isBlank()
                ? List.of()
                : Arrays.asList(colors.split(","));
        return new PlayerColor(parsedKind, parsedColors);
    }

    public ColorProfile apiProfile() {
        return new ColorProfile(kind, colors);
    }
}
