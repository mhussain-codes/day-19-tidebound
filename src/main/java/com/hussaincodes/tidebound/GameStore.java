package com.hussaincodes.tidebound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Small repository for local game progress, backed by SQLite. */
final class GameStore implements AutoCloseable {
    private final Connection connection;

    GameStore() throws Exception {
        Path folder = Path.of(System.getProperty("user.home"), ".tidebound");
        Files.createDirectories(folder);
        connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("tidebound.db"));
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS level_progress (
                    level_no INTEGER PRIMARY KEY,
                    best_score INTEGER NOT NULL DEFAULT 0,
                    cleared_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS attempts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    level_no INTEGER NOT NULL,
                    score INTEGER NOT NULL,
                    hints_used INTEGER NOT NULL,
                    mirror_turns INTEGER NOT NULL,
                    won INTEGER NOT NULL CHECK (won IN (0, 1)),
                    attempted_at TEXT NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_attempts_level_time ON attempts(level_no, attempted_at)");
            statement.execute("PRAGMA user_version = 1");
        }
    }

    Map<Integer, Integer> loadBestScores() throws SQLException {
        Map<Integer, Integer> scores = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT level_no, best_score FROM level_progress")) {
            while (rows.next()) scores.put(rows.getInt("level_no"), rows.getInt("best_score"));
        }
        return scores;
    }

    void saveAttempt(int level, int score, int hints, int mirrorTurns, boolean won) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO attempts(level_no, score, hints_used, mirror_turns, won, attempted_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setInt(1, level);
                insert.setInt(2, score);
                insert.setInt(3, hints);
                insert.setInt(4, mirrorTurns);
                insert.setInt(5, won ? 1 : 0);
                insert.setString(6, Instant.now().toString());
                insert.executeUpdate();
            }
            if (won) {
                try (PreparedStatement progress = connection.prepareStatement("""
                        INSERT INTO level_progress(level_no, best_score, cleared_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(level_no) DO UPDATE SET
                            best_score = MAX(level_progress.best_score, excluded.best_score),
                            cleared_at = excluded.cleared_at
                        """)) {
                    progress.setInt(1, level);
                    progress.setInt(2, score);
                    progress.setString(3, Instant.now().toString());
                    progress.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    int totalAttempts() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT COUNT(*) FROM attempts")) {
            return row.next() ? row.getInt(1) : 0;
        }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
