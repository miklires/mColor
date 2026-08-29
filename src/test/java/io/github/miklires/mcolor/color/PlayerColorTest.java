package io.github.miklires.mcolor.color;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerColorTest {
    @Test void roundTripsMultiStopGradient() {
        PlayerColor original = PlayerColor.gradient(List.of("#112233", "#445566", "#778899"));
        assertEquals(original, PlayerColor.decode(original.kind().name(), original.encode()));
    }

    @Test void rejectsInvalidAndIncompleteProfiles() {
        assertThrows(IllegalArgumentException.class, () -> PlayerColor.solid("red"));
        assertThrows(IllegalArgumentException.class, () -> PlayerColor.gradient(List.of("#112233")));
        assertThrows(IllegalArgumentException.class, () -> PlayerColor.gradient(
                java.util.stream.IntStream.range(0, 17).mapToObj(ignored -> "#112233").toList()));
    }

    @Test void rendersUnicodeByCodePoint() {
        String legacy = ColorRenderer.legacy(PlayerColor.gradient(List.of("#000000", "#FFFFFF")), "A😀B");
        assertTrue(legacy.contains("😀"));
    }

    @Test void escapesMiniMessageInput() {
        String rendered = ColorRenderer.miniMessage(PlayerColor.solid("#FFFFFF"), "<red>name");
        assertTrue(rendered.contains("\\<red>"));
    }
}
