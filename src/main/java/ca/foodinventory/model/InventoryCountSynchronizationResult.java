package ca.foodinventory.model;

import java.util.List;

public record InventoryCountSynchronizationResult(List<Integer> addedProductIds) {
    public InventoryCountSynchronizationResult {
        addedProductIds = List.copyOf(addedProductIds);
    }

    public int addedCount() {
        return addedProductIds.size();
    }
}
