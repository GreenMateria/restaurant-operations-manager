package ca.foodinventory.database;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

class PostgresConnectionPool {

    private static final int MAX_IDLE_CONNECTIONS = 4;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    private static final long VALIDATION_INTERVAL_MILLIS = 30_000;

    private final String url;
    private final String user;
    private final String password;
    private final Deque<IdleConnection> idleConnections = new ArrayDeque<>();

    PostgresConnectionPool(String url, String user, String password) {
        this.url = Objects.requireNonNull(url);
        this.user = Objects.requireNonNull(user);
        this.password = Objects.requireNonNull(password);
    }

    synchronized Connection borrowConnection() throws SQLException {
        long now = System.currentTimeMillis();

        while (!idleConnections.isEmpty()) {
            IdleConnection idleConnection = idleConnections.removeFirst();
            Connection connection = idleConnection.connection();

            if (isRecentlyValidated(connection, idleConnection.lastValidatedAtMillis(), now)) {
                return pooledConnection(connection);
            }

            if (isUsable(connection)) {
                return pooledConnection(connection, now);
            }
            closePhysicalConnection(connection);
        }

        return pooledConnection(DriverManager.getConnection(url, user, password), now);
    }

    private Connection pooledConnection(Connection connection) {
        return pooledConnection(connection, System.currentTimeMillis());
    }

    private Connection pooledConnection(Connection connection, long lastValidatedAtMillis) {
        InvocationHandler handler = new PooledConnectionHandler(connection, lastValidatedAtMillis);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private synchronized void releaseConnection(Connection connection, long lastValidatedAtMillis) throws SQLException {
        if (connection == null || connection.isClosed()) {
            closePhysicalConnection(connection);
            return;
        }

        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
        }

        connection.clearWarnings();

        if (idleConnections.size() >= MAX_IDLE_CONNECTIONS) {
            closePhysicalConnection(connection);
        } else {
            idleConnections.addLast(new IdleConnection(connection, lastValidatedAtMillis));
        }
    }

    private boolean isRecentlyValidated(
            Connection connection,
            long lastValidatedAtMillis,
            long now
    ) {
        try {
            return connection != null
                    && !connection.isClosed()
                    && now - lastValidatedAtMillis < VALIDATION_INTERVAL_MILLIS;
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean isUsable(Connection connection) {
        try {
            return connection != null
                    && !connection.isClosed()
                    && connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }

    private void closePhysicalConnection(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    private record IdleConnection(Connection connection, long lastValidatedAtMillis) {
    }

    private class PooledConnectionHandler implements InvocationHandler {

        private final Connection physicalConnection;
        private final long lastValidatedAtMillis;
        private boolean closed;

        private PooledConnectionHandler(Connection physicalConnection, long lastValidatedAtMillis) {
            this.physicalConnection = physicalConnection;
            this.lastValidatedAtMillis = lastValidatedAtMillis;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            if ("close".equals(methodName)) {
                if (!closed) {
                    closed = true;
                    releaseConnection(physicalConnection, lastValidatedAtMillis);
                }
                return null;
            }

            if ("isClosed".equals(methodName)) {
                return closed || physicalConnection.isClosed();
            }

            if ("unwrap".equals(methodName) && args != null && args.length == 1) {
                Class<?> requestedType = (Class<?>) args[0];
                if (requestedType.isInstance(physicalConnection)) {
                    return requestedType.cast(physicalConnection);
                }
            }

            if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
                Class<?> requestedType = (Class<?>) args[0];
                return requestedType.isInstance(physicalConnection);
            }

            if (closed) {
                throw new SQLException("Connection is closed.");
            }

            try {
                return method.invoke(physicalConnection, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
