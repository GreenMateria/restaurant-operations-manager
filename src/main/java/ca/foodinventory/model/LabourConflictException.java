package ca.foodinventory.model;

public class LabourConflictException extends RuntimeException {
    public LabourConflictException() {
        super("This data changed since it was loaded. Your entries are still on screen. "
                + "Review or copy your entries before using Load Week to reload, then re-enter your changes. "
                + "Earlier dates in a Net Sales or Tip Pool range may already have saved.");
    }
}
