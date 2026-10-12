package ca.foodinventory.api;
final class LabourConflictException extends RuntimeException {
    LabourConflictException() { super("Another manager changed this data. Your entries are still on screen. Use Load Week to reload and review the latest data before saving again. Earlier dates in a range may already have saved."); }
}
