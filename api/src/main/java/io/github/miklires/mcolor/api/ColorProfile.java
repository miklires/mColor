package io.github.miklires.mcolor.api;

import java.util.List;
import java.util.Objects;

public record ColorProfile(Kind kind, List<String> colors) {
    public enum Kind { SOLID, GRADIENT, RAINBOW }

    public ColorProfile {
        Objects.requireNonNull(kind, "kind");
        colors = List.copyOf(colors);
    }
}
