package io.github.miklires.mcolor.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.PlayerColor;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ColorStorage implements AutoCloseable {
    private final MColorPlugin plugin;
    private final String serverId;
    private HikariDataSource dataSource;

    public ColorStorage(MColorPlugin plugin) {
        this.plugin = plugin;
        this.serverId = plugin.getConfig().getString("sync.server-id", UUID.randomUUID().toString());
    }

    public void start() {
        String type = plugin.getConfig().getString("storage.type", "h2").toLowerCase(Locale.ROOT);
        HikariConfig config = new HikariConfig();
        config.setPoolName("mColor-storage");
        config.setJdbcUrl(jdbcUrl(type));
        config.setDriverClassName(switch (type) {
            case "h2" -> "org.h2.Driver";
            case "sqlite" -> "org.sqlite.JDBC";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "mariadb" -> "org.mariadb.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            default -> throw new IllegalArgumentException("Unsupported storage type: " + type);
        });
        if (!type.equals("h2") && !type.equals("sqlite")) {
            config.setUsername(plugin.getConfig().getString("storage.username", ""));
            config.setPassword(plugin.getConfig().getString("storage.password", ""));
        }
        config.setMaximumPoolSize(type.equals("sqlite") ? 1 : Math.clamp(plugin.getConfig().getInt("storage.pool-size", 4), 1, 32));
        config.setConnectionTimeout(Math.clamp(plugin.getConfig().getLong("storage.connection-timeout-millis", 10_000), 250, 120_000));
        dataSource = new HikariDataSource(config);
        migrate();
    }

    public Optional<PlayerColor> load(UUID playerId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT kind, colors FROM player_colors WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(PlayerColor.decode(result.getString(1), result.getString(2)))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("load color", exception);
        }
    }

    public void save(UUID playerId, PlayerColor color) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            delete(connection, playerId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_colors (player_uuid, kind, colors, updated_at) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, color.kind().name());
                statement.setString(3, color.encode());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            recordChange(connection, playerId);
            connection.commit();
        } catch (SQLException exception) {
            throw failure("save color", exception);
        }
    }

    public void delete(UUID playerId) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            delete(connection, playerId);
            recordChange(connection, playerId);
            connection.commit();
        } catch (SQLException exception) {
            throw failure("reset color", exception);
        }
    }

    public Set<UUID> changesSince(long timestamp) {
        Set<UUID> changed = ConcurrentHashMap.newKeySet();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT player_uuid FROM color_changes WHERE changed_at >= ? AND server_id <> ?")) {
            statement.setLong(1, timestamp);
            statement.setString(2, serverId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) changed.add(UUID.fromString(result.getString(1)));
            }
            return changed;
        } catch (SQLException exception) {
            throw failure("poll changes", exception);
        }
    }

    private void migrate() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mcolor_schema (version INTEGER PRIMARY KEY, applied_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_colors (player_uuid VARCHAR(36) PRIMARY KEY, kind VARCHAR(16) NOT NULL, colors VARCHAR(1024) NOT NULL, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS color_changes (change_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, server_id VARCHAR(64) NOT NULL, changed_at BIGINT NOT NULL)");
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM mcolor_schema")) {
                if (result.next() && result.getInt(1) == 0) {
                    statement.executeUpdate("INSERT INTO mcolor_schema (version, applied_at) VALUES (1, " + System.currentTimeMillis() + ")");
                }
            }
        } catch (SQLException exception) {
            throw failure("migrate database", exception);
        }
    }

    private String jdbcUrl(String type) {
        String custom = plugin.getConfig().getString("storage.jdbc-url", "").trim();
        if (!custom.isEmpty()) return custom;
        String host = plugin.getConfig().getString("storage.host", "localhost");
        String database = plugin.getConfig().getString("storage.database", "mcolor");
        int port = plugin.getConfig().getInt("storage.port", type.equals("postgresql") ? 5432 : 3306);
        return switch (type) {
            case "h2" -> "jdbc:h2:" + new File(plugin.getDataFolder(), "mcolor").getAbsolutePath() + ";AUTO_SERVER=TRUE";
            case "sqlite" -> "jdbc:sqlite:" + new File(plugin.getDataFolder(), "mcolor.db").getAbsolutePath();
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
            case "mariadb" -> "jdbc:mariadb://" + host + ":" + port + "/" + database;
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            default -> throw new IllegalArgumentException("Unsupported storage type: " + type);
        };
    }

    private void delete(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_colors WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void recordChange(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO color_changes (change_id, player_uuid, server_id, changed_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, serverId);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        if (dataSource == null) throw new SQLException("storage is unavailable");
        return dataSource.getConnection();
    }

    private IllegalStateException failure(String action, Exception exception) {
        return new IllegalStateException("Could not " + action + ": " + exception.getMessage(), exception);
    }

    @Override
    public void close() {
        if (dataSource != null) dataSource.close();
    }
}
