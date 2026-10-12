package com.mdevstudio.questboard.model;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum Goal {
    BREAK,
    KILL,
    FISH,
    CRAFT,
    TRAVEL;

    public static @Nullable Goal parse(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
