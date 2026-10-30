package ca.foodinventory.model;

public record NewPosMenuItem(int id, String name, int productionProfileId,
                             String productionProfileName, boolean active) {
}
