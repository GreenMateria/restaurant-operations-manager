package ca.foodinventory.model;

public record LocationLoginSession(
        String token,
        int locationId,
        String locationCode,
        String locationName,
        String username
) {
}
