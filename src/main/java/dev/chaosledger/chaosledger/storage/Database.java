package dev.chaosledger.chaosledger.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * SQLite-backed database for ChaosLedger.
 *
 * <p>All persistent state lives in a single {@code .db} file managed by the embedded
 * SQLite JDBC driver. The server thread is the only writer; the HTTP API is read-only
 * against snapshots. To guarantee that, the connection is opened with a serialized
 * mutex so that any thread calling {@link #open()} receives the same connection
 * guarded by an internal write lock.</p>
 *
 * <p>NOTE: This class loads the SQLite driver through the system class loader. When
 * the mod is bundled inside a Fabric jar, the embedded {@code sqlite-jdbc} dependency
 * must be present on the classpath (it is shaded via {@code include} in build.gradle).</p>
 */
public final class Database {

    private static final String DRIVER = "org.sqlite.JDBC";

    private final Path file;
    private volatile Connection connection;

    public Database(Path file) {
        this.file = file;
    }

    public synchronized Connection open() {
        if (connection != null) {
            return connection;
        }
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver is not on the classpath.", e);
        }
        try {
            file.getParent().toFile().mkdirs();
            Properties props = new Properties();
            props.setProperty("foreign_keys", "true");
            // 5s busy timeout to absorb the brief lock contention when the HTTP API
            // issues read queries while the server thread is mid-write.
            props.setProperty("busy_timeout", "5000");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toString(), props);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database: " + file, e);
        }
        new Schema().apply(connection);
        return connection;
    }

    public synchronized Connection connection() {
        if (connection == null) {
            return open();
        }
        return connection;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
                connection.close();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to close SQLite connection.", e);
            } finally {
                connection = null;
            }
        }
    }
}
