package ca.foodinventory.service;

public class InventoryCountConflictException extends RuntimeException {
    public InventoryCountConflictException() {
        super("Another manager changed this count. Your entries are still on screen. Review them before reloading.");
    }
}
