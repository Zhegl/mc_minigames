package me.petr1furious.potatominigames.core.game;

import java.util.List;
import java.util.function.Supplier;

/**
 * A game's extra last argument to {@code /<game> start [mode] [players] [value]}, e.g. the pillars
 * arena. Passed to the session as {@link SessionServices#option option(key)}.
 */
public record StartOption(String key, Supplier<List<String>> values) {
}
