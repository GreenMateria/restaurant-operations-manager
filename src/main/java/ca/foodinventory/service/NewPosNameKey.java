package ca.foodinventory.service;

import java.util.Locale;

public final class NewPosNameKey {
    private NewPosNameKey() { }

    public static String of(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
