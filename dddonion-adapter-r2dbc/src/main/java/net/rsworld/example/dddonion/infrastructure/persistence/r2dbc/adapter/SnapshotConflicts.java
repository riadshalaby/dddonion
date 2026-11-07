package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import io.r2dbc.spi.R2dbcException;

/**
 * Innerhalb einer Transaktion lehnt MariaDB mit Snapshot Isolation ({@code innodb_snapshot_isolation}, seit 11.6.2
 * standardmäßig an) ein UPDATE auf eine Zeile, die eine andere Transaktion seit dem Lesen geändert hat, mit Fehler 1020
 * ("Record has changed since last read") ab, statt keine Zeile zu treffen. Das ist derselbe
 * Optimistic-Locking-Konflikt.
 */
final class SnapshotConflicts {

    private static final int ER_CHECKREAD = 1020;

    private SnapshotConflicts() {}

    static boolean isSnapshotConflict(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getErrorCode() == ER_CHECKREAD) {
                return true;
            }
        }
        return false;
    }
}
