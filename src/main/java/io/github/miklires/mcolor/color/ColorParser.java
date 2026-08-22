package io.github.miklires.mcolor.color;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ColorParser {
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private ColorParser() { }

    public static Optional<String> hex(String input) {
        if (input == null) return Optional.empty();
        String value = input.trim();
        return HEX.matcher(value).matches()
                ? Optional.of(value.toUpperCase(Locale.ROOT))
                : Optional.empty();
    }

    public static Optional<String> color(String input, Map<String, String> namedColors) {
        Optional<String> direct = hex(input);
        if (direct.isPresent()) return direct;
        if (input == null) return Optional.empty();
        return Optional.ofNullable(namedColors.get(input.toLowerCase(Locale.ROOT))).flatMap(ColorParser::hex);
    }
}
