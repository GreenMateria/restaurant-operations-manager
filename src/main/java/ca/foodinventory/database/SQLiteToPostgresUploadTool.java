package ca.foodinventory.database;

public class SQLiteToPostgresUploadTool {

    private static final String UPLOAD_CONFIRM_ENV = "FOOD_INVENTORY_UPLOAD_CONFIRM";
    private static final String UPLOAD_CONFIRM_VALUE = "UPLOAD_TO_POSTGRES";

    public static void main(String[] args) {
        if (!UPLOAD_CONFIRM_VALUE.equals(System.getenv(UPLOAD_CONFIRM_ENV))) {
            throw new IllegalStateException(
                    "Upload is blocked. Set " + UPLOAD_CONFIRM_ENV
                            + "=" + UPLOAD_CONFIRM_VALUE
                            + " to confirm replacing PostgreSQL data."
            );
        }

        DatabaseSyncService.MigrationResult result =
                new DatabaseSyncService().uploadSqliteToCloud();

        System.out.println("SQLite upload to PostgreSQL complete.");
        System.out.println("Rows uploaded: " + result.totalRows());
        result.rowCounts().forEach((tableName, rows) ->
                System.out.println("Uploaded " + rows + " rows: " + tableName)
        );
    }
}
