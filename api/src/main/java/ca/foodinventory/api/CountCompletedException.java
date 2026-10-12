package ca.foodinventory.api;

final class CountCompletedException extends RuntimeException {
    CountCompletedException() {
        super("Completed counts cannot be synchronized with a template.");
    }
}
