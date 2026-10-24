package ca.foodinventory.api;

class CountPreconditionException extends RuntimeException {
    CountPreconditionException() {
        super("Update the desktop application and reload this count before saving.");
    }
}
