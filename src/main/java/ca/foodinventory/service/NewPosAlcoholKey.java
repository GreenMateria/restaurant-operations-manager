package ca.foodinventory.service;

/** Keeps name-based new POS alcohol mappings distinct from numbered legacy PLUs. */
public final class NewPosAlcoholKey {
    private static final String PREFIX = "NEWPOS:";

    private NewPosAlcoholKey() { }

    public static String of(String name) {
        return PREFIX + NewPosNameKey.of(name);
    }

    public static boolean isNewPos(String key) {
        return key != null && key.startsWith(PREFIX);
    }
}
