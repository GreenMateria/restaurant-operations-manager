package ca.foodinventory.model;

public class PosMenuItemImportSummary {

    private final String sheetName;
    private final int rowsRead;
    private final int insertedCount;
    private final int updatedCount;
    private final int skippedCount;

    public PosMenuItemImportSummary(
            String sheetName,
            int rowsRead,
            int insertedCount,
            int updatedCount,
            int skippedCount
    ) {
        this.sheetName = sheetName;
        this.rowsRead = rowsRead;
        this.insertedCount = insertedCount;
        this.updatedCount = updatedCount;
        this.skippedCount = skippedCount;
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getRowsRead() {
        return rowsRead;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
}
