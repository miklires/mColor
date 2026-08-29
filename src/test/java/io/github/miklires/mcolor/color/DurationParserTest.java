package io.github.miklires.mcolor.color;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class DurationParserTest {
    @Test void parsesBoundedDurations() {
        assertEquals(Duration.ofHours(12), DurationParser.parse("12h", Duration.ofDays(30)).orElseThrow());
        assertEquals(Duration.ofDays(14), DurationParser.parse("2W", Duration.ofDays(30)).orElseThrow());
    }

    @Test void rejectsAmbiguousZeroNegativeAndOversizedValues() {
        assertTrue(DurationParser.parse("0h", Duration.ofDays(30)).isEmpty());
        assertTrue(DurationParser.parse("-1h", Duration.ofDays(30)).isEmpty());
        assertTrue(DurationParser.parse("forever", Duration.ofDays(30)).isEmpty());
        assertTrue(DurationParser.parse("31d", Duration.ofDays(30)).isEmpty());
    }
}
