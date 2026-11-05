package com.narainsons.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Pooled connections. DAOs call {@link #getConnection()} in try-with-resources as before.
 * Inside {@link #inTransaction} every DAO call on the thread shares one connection, so several
 * DAO calls commit or roll back together without passing a Connection around.
 */
public final class DBConnection {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run() throws SQLException;
    }

    private static final HikariDataSource POOL;
    private static final ThreadLocal<Connection> CURRENT_TX = new ThreadLocal<>();
    private static final ThreadLocal<List<Runnable>> AFTER_COMMIT = new ThreadLocal<>();

    static {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("db.properties classpath par nahi mili");
            }
            props.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.user"));
        config.setPassword(props.getProperty("db.password"));
        config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.size", "10")));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        // App should still deploy when MySQL is down; /api/health reports it.
        config.setInitializationFailTimeout(-1);
        POOL = new HikariDataSource(config);
    }

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Connection tx = CURRENT_TX.get();
        return tx != null ? nonClosing(tx) : POOL.getConnection();
    }

    /** Runs work in one transaction; a nested call joins the outer one. */
    public static <T> T inTransaction(SqlWork<T> work) throws SQLException {
        if (CURRENT_TX.get() != null) {
            return work.run();
        }
        try (Connection conn = POOL.getConnection()) {
            conn.setAutoCommit(false);
            CURRENT_TX.set(conn);
            try {
                T result = work.run();
                conn.commit();
                List<Runnable> actions = AFTER_COMMIT.get();
                if (actions != null) {
                    actions.forEach(Runnable::run);
                }
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                CURRENT_TX.remove();
                AFTER_COMMIT.remove();
                conn.setAutoCommit(true);
            }
        }
    }

    /** Runs the action once the current transaction commits (never on rollback), or now if there is none. */
    public static void afterCommit(Runnable action) {
        if (CURRENT_TX.get() == null) {
            action.run();
            return;
        }
        if (AFTER_COMMIT.get() == null) {
            AFTER_COMMIT.set(new ArrayList<>());
        }
        AFTER_COMMIT.get().add(action);
    }

    public static void shutdown() {
        POOL.close();
    }

    private static Connection nonClosing(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                DBConnection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
