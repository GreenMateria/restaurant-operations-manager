package ca.foodinventory.api;

class CountConflictException extends RuntimeException {
    CountConflictException(String message) {
        super(message);
    }
}
