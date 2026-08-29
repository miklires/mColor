package io.github.miklires.mcolor.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.PlayerColor;

import java.io.File;
import java.sql.*;
import java.time.Duration;
import java.util.*;
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

    public Optional<StoredColor> load(UUID playerId) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT kind, colors, expires_at FROM player_colors WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new StoredColor(
                        PlayerColor.decode(result.getString(1), result.getString(2)), result.getLong(3))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("load color", exception);
        }
    }

    public void save(UUID playerId, PlayerColor color, long expiresAt) {
        transaction(connection -> {
            snapshot(connection, playerId);
            deleteColor(connection, playerId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_colors (player_uuid, kind, colors, expires_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, color.kind().name());
                statement.setString(3, color.encode());
                statement.setLong(4, Math.max(0, expiresAt));
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
            recordChange(connection, playerId);
        }, "save color");
    }

    public void delete(UUID playerId) {
        transaction(connection -> {
            snapshot(connection, playerId);
            deleteColor(connection, playerId);
            recordChange(connection, playerId);
        }, "reset color");
    }

    public List<HistoryEntry> history(UUID playerId, int limit) {
        List<HistoryEntry> entries = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT history_id, kind, colors, expires_at, changed_at FROM color_history WHERE player_uuid=? ORDER BY changed_at DESC")) {
            statement.setString(1, playerId.toString());
            statement.setMaxRows(Math.clamp(limit, 1, 50));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String kind = result.getString("kind");
                    entries.add(new HistoryEntry(UUID.fromString(result.getString("history_id")),
                            kind == null ? null : PlayerColor.decode(kind, result.getString("colors")),
                            result.getLong("expires_at"), result.getLong("changed_at")));
                }
            }
            return List.copyOf(entries);
        } catch (SQLException exception) {
            throw failure("load color history", exception);
        }
    }

    public UndoResult undo(UUID playerId) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID historyId;
                String kind;
                String colors;
                long expiresAt;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT history_id,kind,colors,expires_at FROM color_history WHERE player_uuid=? ORDER BY changed_at DESC")) {
                    select.setString(1, playerId.toString());
                    select.setMaxRows(1);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) { connection.rollback(); return new UndoResult(false, null); }
                        historyId = UUID.fromString(result.getString(1));
                        kind = result.getString(2);
                        colors = result.getString(3);
                        expiresAt = result.getLong(4);
                    }
                }
                deleteColor(connection, playerId);
                StoredColor restored = null;
                if (kind != null) {
                    PlayerColor color = PlayerColor.decode(kind, colors);
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO player_colors(player_uuid,kind,colors,expires_at,updated_at) VALUES(?,?,?,?,?)")) {
                        insert.setString(1, playerId.toString());
                        insert.setString(2, color.kind().name());
                        insert.setString(3, color.encode());
                        insert.setLong(4, expiresAt);
                        insert.setLong(5, System.currentTimeMillis());
                        insert.executeUpdate();
                    }
                    restored = new StoredColor(color, expiresAt);
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM color_history WHERE history_id=?")) {
                    delete.setString(1, historyId.toString());
                    delete.executeUpdate();
                }
                recordChange(connection, playerId);
                connection.commit();
                return new UndoResult(true, restored);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw failure("restore color history", exception);
        }
    }

    public boolean copyAllowed(UUID playerId, boolean defaultValue) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT copy_allowed FROM player_color_settings WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getBoolean(1) : defaultValue;
            }
        } catch (SQLException exception) {
            throw failure("load privacy setting", exception);
        }
    }

    public void setCopyAllowed(UUID playerId, boolean allowed) {
        transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM player_color_settings WHERE player_uuid=?")) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_color_settings(player_uuid,copy_allowed) VALUES(?,?)")) {
                insert.setString(1, playerId.toString());
                insert.setBoolean(2, allowed);
                insert.executeUpdate();
            }
        }, "save privacy setting");
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_colors (player_uuid VARCHAR(36) PRIMARY KEY, kind VARCHAR(16) NOT NULL, colors VARCHAR(1024) NOT NULL, expires_at BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL)");
            if (!hasColumn(connection, "player_colors", "expires_at")) {
                statement.executeUpdate("ALTER TABLE player_colors ADD COLUMN expires_at BIGINT NOT NULL DEFAULT 0");
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS color_changes (change_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, server_id VARCHAR(64) NOT NULL, changed_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS color_history (history_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, kind VARCHAR(16), colors VARCHAR(1024), expires_at BIGINT NOT NULL DEFAULT 0, changed_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS color_history_player_time_idx ON color_history(player_uuid,changed_at)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_color_settings (player_uuid VARCHAR(36) PRIMARY KEY, copy_allowed BOOLEAN NOT NULL)");
            try (ResultSet result = statement.executeQuery("SELECT MAX(version) FROM mcolor_schema")) {
                int version = result.next() ? result.getInt(1) : 0;
                if (version > 2) throw new SQLException("Database schema " + version + " is newer than supported schema 2");
                if (version == 0) statement.executeUpdate(
                        "INSERT INTO mcolor_schema (version, applied_at) VALUES (2, " + System.currentTimeMillis() + ")");
                else if (version < 2) statement.executeUpdate(
                        "UPDATE mcolor_schema SET version=2, applied_at=" + System.currentTimeMillis() + " WHERE version=" + version);
            }
            long changeCutoff = System.currentTimeMillis() - Duration.ofDays(7).toMillis();
            long historyCutoff = System.currentTimeMillis() - Duration.ofDays(Math.clamp(
                    plugin.getConfig().getLong("history.retention-days", 90), 1, 3650)).toMillis();
            statement.executeUpdate("DELETE FROM color_changes WHERE changed_at < " + changeCutoff);
            statement.executeUpdate("DELETE FROM color_history WHERE changed_at < " + historyCutoff);
        } catch (SQLException exception) {
            throw failure("migrate database", exception);
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        for (String name : List.of(table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT))) {
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, name, null)) {
                while (columns.next()) if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    private static void snapshot(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT kind,colors,expires_at FROM player_colors WHERE player_uuid=?")) {
            select.setString(1, playerId.toString());
            try (ResultSet result = select.executeQuery()) {
                boolean present = result.next();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO color_history(history_id,player_uuid,kind,colors,expires_at,changed_at) VALUES(?,?,?,?,?,?)")) {
                    insert.setString(1, UUID.randomUUID().toString());
                    insert.setString(2, playerId.toString());
                    insert.setString(3, present ? result.getString(1) : null);
                    insert.setString(4, present ? result.getString(2) : null);
                    insert.setLong(5, present ? result.getLong(3) : 0);
                    insert.setLong(6, nextHistoryTime(connection, playerId));
                    insert.executeUpdate();
                }
            }
        }
    }

    private static long nextHistoryTime(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT MAX(changed_at) FROM color_history WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                long previous = result.next() ? result.getLong(1) : 0;
                return Math.max(System.currentTimeMillis(), previous + 1);
            }
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

    private static void deleteColor(Connection connection, UUID playerId) throws SQLException {
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

    private void transaction(SqlWork work, String action) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw failure(action, exception);
        }
    }

    private Connection connection() throws SQLException {
        if (dataSource == null) throw new SQLException("storage is unavailable");
        return dataSource.getConnection();
    }

    private static IllegalStateException failure(String action, Exception exception) {
        return new IllegalStateException("Could not " + action + ": " + exception.getMessage(), exception);
    }

    @Override public void close() { if (dataSource != null) dataSource.close(); }

    @FunctionalInterface private interface SqlWork { void run(Connection connection) throws Exception; }
    public record StoredColor(PlayerColor color, long expiresAt) { public boolean expired(long now) { return expiresAt > 0 && expiresAt <= now; } }
    public record HistoryEntry(UUID id, PlayerColor color, long expiresAt, long changedAt) { }
    public record UndoResult(boolean found, StoredColor restored) { }
}
