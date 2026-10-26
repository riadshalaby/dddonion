package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.junit.jupiter.api.Test;

class SnapshotConflictsTest {

    @Test
    void detectsErrorCode1020AnywhereInTheCauseChain() {
        var conflict = new R2dbcNonTransientResourceException("Record has changed since last read", "HY000", 1020);

        assertThat(SnapshotConflicts.isSnapshotConflict(conflict)).isTrue();
        assertThat(SnapshotConflicts.isSnapshotConflict(new RuntimeException("wrapped", conflict)))
                .isTrue();
    }

    @Test
    void ignoresOtherErrorsAndChainsWithoutR2dbcException() {
        var other = new R2dbcNonTransientResourceException("Duplicate entry", "23000", 1062);

        assertThat(SnapshotConflicts.isSnapshotConflict(other)).isFalse();
        assertThat(SnapshotConflicts.isSnapshotConflict(new RuntimeException("wrapped", other)))
                .isFalse();
        assertThat(SnapshotConflicts.isSnapshotConflict(new IllegalStateException("boom")))
                .isFalse();
    }
}
