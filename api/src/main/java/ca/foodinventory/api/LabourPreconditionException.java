package ca.foodinventory.api;
final class LabourPreconditionException extends RuntimeException {
    LabourPreconditionException() { super("Update the desktop application and reload this week before saving Labour data."); }
}
