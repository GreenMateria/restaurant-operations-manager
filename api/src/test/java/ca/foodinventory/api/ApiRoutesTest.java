package ca.foodinventory.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRoutesTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("foodinventory.api.key");
        System.clearProperty("foodinventory.api.db.url");
        System.clearProperty("foodinventory.api.db.user");
        System.clearProperty("foodinventory.api.db.password");
        System.clearProperty("foodinventory.api.locationAuthRequired");
    }

    @Test
    void healthRouteDoesNotRequireApiKeyOrDatabaseSettings() {
        System.setProperty("foodinventory.api.key", "secret");

        ApiRoutes.ApiResult result =
                new ApiRoutes().handle("GET", "/health", Map.of(), "");

        assertEquals(200, result.statusCode());
        assertEquals("application/json; charset=utf-8", result.contentType());
        assertTrue(result.body().contains("\"status\":\"ok\""));
        assertTrue(result.body().contains("\"version\":\"3.1.3\""));
    }

    @Test
    void protectedRoutesRejectMissingApiKeyBeforeDatabaseAccess() {
        System.setProperty("foodinventory.api.key", "secret");

        ApiRoutes.ApiResult result =
                new ApiRoutes().handle("GET", "/products", Map.of(), "");

        assertEquals(401, result.statusCode());
        assertTrue(result.body().contains("\"error\":\"unauthorized\""));
    }

    @Test
    void protectedRoutesAcceptApiKeyHeaderCaseInsensitively() {
        System.setProperty("foodinventory.api.key", "secret");

        ApiRoutes.ApiResult result = new ApiRoutes().handle(
                "GET",
                "/products",
                Map.of("X-API-Key", List.of("secret")),
                ""
        );

        assertEquals(503, result.statusCode());
        assertTrue(result.body().contains("\"error\":\"database_not_configured\""));
    }

    @Test
    void normalizesTrailingSlashOnHealthRoute() {
        ApiRoutes.ApiResult result =
                new ApiRoutes().handle("GET", "/health/", Map.of(), "");

        assertEquals(200, result.statusCode());
    }

    @Test
    void loginRequiresUsernameAndPassword() {
        ApiRoutes.ApiResult result =
                routesWithAuth(null).handle("POST", "/auth/login", Map.of(), "{\"username\":\"esm\"}");

        assertEquals(400, result.statusCode());
        assertTrue(result.body().contains("\"error\":\"bad_request\""));
        assertTrue(result.body().contains("password is required"));
    }

    @Test
    void loginRejectsInvalidCredentials() {
        ApiRoutes.ApiResult result = routesWithAuth(null).handle(
                "POST",
                "/auth/login",
                Map.of(),
                "{\"username\":\"esm\",\"password\":\"wrong\"}"
        );

        assertEquals(401, result.statusCode());
        assertTrue(result.body().contains("\"error\":\"invalid_credentials\""));
    }

    @Test
    void loginReturnsSessionAndLocation() {
        LocationSession session = new LocationSession(
                "token-123",
                7,
                "NORTH",
                "North Store",
                "north"
        );

        ApiRoutes.ApiResult result = routesWithAuth(session).handle(
                "POST",
                "/auth/login",
                Map.of(),
                "{\"username\":\"north\",\"password\":\"secret\"}"
        );

        assertEquals(200, result.statusCode());
        assertTrue(result.body().contains("\"token\":\"token-123\""));
        assertTrue(result.body().contains("\"id\":7"));
        assertTrue(result.body().contains("\"code\":\"NORTH\""));
        assertTrue(result.body().contains("\"name\":\"North Store\""));
        assertTrue(result.body().contains("\"username\":\"north\""));
    }

    @Test
    void labourRoutesRequireLocationTokenWhenLocationAuthIsEnabled() {
        System.setProperty("foodinventory.api.key", "secret");
        System.setProperty("foodinventory.api.locationAuthRequired", "true");

        ApiRoutes.ApiResult result = routesWithAuth(null).handle(
                "GET",
                "/labour/positions",
                Map.of("x-api-key", List.of("secret")),
                ""
        );

        assertEquals(401, result.statusCode());
        assertTrue(result.body().contains("\"error\":\"location_unauthorized\""));
    }

    @Test
    void protectedPasswordRoutesRejectMissingApiKeyBeforeDatabaseAccess() {
        System.setProperty("foodinventory.api.key", "secret");

        ApiRoutes.ApiResult result =
                routesWithAuth(null).handle("GET", "/protected-passwords/status", Map.of(), "");

        assertEquals(401, result.statusCode());
        assertTrue(result.body().contains("\"error\":\"unauthorized\""));
    }

    private ApiRoutes routesWithAuth(LocationSession session) {
        return new ApiRoutes(
                new ProductRepository(),
                new PosMenuItemRepository(),
                new AlcoholSalesMappingRepository(),
                new InventoryRepository(),
                new InvoiceRepository(),
                new AlcoholProductProfileRepository(),
                new ProductionRepository(),
                new ReportingRepository(),
                new AdminSyncRepository(),
                new LabourRepository(),
                new FakeLocationAuthRepository(session),
                new ProtectedPasswordRepository()
        );
    }

    private static class FakeLocationAuthRepository extends LocationAuthRepository {
        private final LocationSession session;

        FakeLocationAuthRepository(LocationSession session) {
            this.session = session;
        }

        @Override
        LocationSession login(String username, String password) {
            return session;
        }

        @Override
        LocationContext resolveSession(String token) {
            if (session == null || !session.token().equals(token)) {
                return null;
            }

            return new LocationContext(
                    session.locationId(),
                    session.locationCode(),
                    session.locationName(),
                    session.username()
            );
        }
    }
}
