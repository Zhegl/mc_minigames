package me.petr1furious.potatominigames.core.command;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Supplier;

import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class Suggest {
    private Suggest() {
    }

    public static SuggestionProvider<CommandSourceStack> strings(Collection<String> options) {
        return dynamic(() -> options);
    }

    public static SuggestionProvider<CommandSourceStack> dynamic(Supplier<Collection<String>> options) {
        return (context, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String option : options.get()) {
                if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(option);
                }
            }
            return builder.buildFuture();
        };
    }
}
