package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.database.DatabaseSyncService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseBackupService {

    public File backupDatabase(File destinationFolder) throws IOException {
        if (DatabaseManager.isApiDatabase()) {
            new DatabaseSyncService().downloadCloudToSqlite();
        } else {
            requireLocalDatabase();
        }

        File sourceDb = DatabaseManager.getSqliteDatabaseFile();

        String timestamp =
                LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));

        File backupFile = new File(
                destinationFolder,
                "FoodInventory_Backup_" + timestamp + ".db"
        );

        Files.copy(
                sourceDb.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        return backupFile;
    }

    public void restoreDatabase(File backupFile) throws IOException {
        requireLocalDatabase();

        File destinationDb = DatabaseManager.getDatabaseFile();

        Files.copy(
                backupFile.toPath(),
                destinationDb.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private void requireLocalDatabase() {
        if (!DatabaseManager.isLocalFileDatabase()) {
            throw new IllegalStateException(
                    "Restore is only available for the local SQLite backup snapshot."
            );
        }
    }
}
