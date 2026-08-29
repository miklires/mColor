package io.github.miklires.mcolor.color;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern VALUE = Pattern.compile("^([1-9][0-9]{0,5})([smhdw])$");
    private DurationParser() { }

    public static Optional<Duration> parse(String input, Duration maximum) {
        if (input == null || maximum == null || maximum.isNegative() || maximum.isZero()) return Optional.empty();
        Matcher matcher = VALUE.matcher(input.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return Optional.empty();
        long amount = Long.parseLong(matcher.group(1));
        Duration duration = switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7));
            default -> throw new IllegalStateException("Unexpected duration unit");
        };
        return duration.compareTo(maximum) <= 0 ? Optional.of(duration) : Optional.empty();
    }
}
