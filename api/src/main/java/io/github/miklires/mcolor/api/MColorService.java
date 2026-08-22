package io.github.miklires.mcolor.api;

import java.util.Optional;
import java.util.UUID;

public interface MColorService {
    Optional<ColorProfile> color(UUID playerId);
    String renderMiniMessage(UUID playerId, String text);
    String renderLegacy(UUID playerId, String text);
}
