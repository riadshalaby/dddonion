package net.rsworld.example.dddonion.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabaseBootstrapInitializerTest {

    @Test
    @DisplayName("Parst Datenbankname und Server-URL aus einer einfachen JDBC-URL")
    void parseSimpleMariaDbUrl() {
        var target = DatabaseBootstrapInitializer.parse("jdbc:mariadb://localhost:3306/shop");

        assertThat(target.databaseName()).isEqualTo("shop");
        assertThat(target.serverJdbcUrl()).isEqualTo("jdbc:mariadb://localhost:3306/");
    }

    @Test
    @DisplayName("Behält Query-Parameter für die Server-URL bei")
    void parseMariaDbUrlWithQuery() {
        var target = DatabaseBootstrapInitializer.parse(
                "jdbc:mariadb://db.internal:3307/shop?useSsl=false&serverTimezone=UTC");

        assertThat(target.databaseName()).isEqualTo("shop");
        assertThat(target.serverJdbcUrl())
                .isEqualTo("jdbc:mariadb://db.internal:3307/?useSsl=false&serverTimezone=UTC");
    }

    @Test
    @DisplayName("Wirft Fehler bei fehlendem Datenbanknamen")
    void parseFailsWithoutDatabase() {
        assertThatThrownBy(() -> DatabaseBootstrapInitializer.parse("jdbc:mariadb://localhost:3306/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database name");
    }
}
