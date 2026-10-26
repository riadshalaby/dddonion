package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import io.r2dbc.spi.R2dbcException;

/**
 * Inside a transaction MariaDB with snapshot isolation ({@code innodb_snapshot_isolation}, on by default since 11.6.2)
 * rejects an UPDATE on a row that another transaction changed since this one read it with error 1020 ("Record has
 * changed since last read") instead of matching zero rows. That is the same optimistic-lock conflict.
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
