package ca.foodinventory.api;

record LocationSession(
        String token,
        int locationId,
        String locationCode,
        String locationName,
        String username
) {
}
