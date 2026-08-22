package io.github.miklires.mcolor.color;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColorParserTest {
    @Test void acceptsOnlyStrictHex() {
        assertEquals("#AABBCC", ColorParser.hex("#aabbcc").orElseThrow());
        assertTrue(ColorParser.hex("aabbcc").isEmpty());
        assertTrue(ColorParser.hex("<#aabbcc>").isEmpty());
        assertTrue(ColorParser.hex("#12345G").isEmpty());
    }

    @Test void resolvesConfiguredNamesWithoutMarkup() {
        assertEquals("#FF5555", ColorParser.color("red", Map.of("red", "#FF5555")).orElseThrow());
        assertTrue(ColorParser.color("<red>", Map.of("red", "#FF5555")).isEmpty());
    }
}
