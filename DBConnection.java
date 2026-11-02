import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// DATABASE CONNECTIVITY: Manages JDBC connections to MySQL database (bankdb)
public class DBConnection {
    // Security: Requiring credentials from the environment prevents leaked repository credentials from exposing the database.

    static {
        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("CRITICAL ERROR: MySQL JDBC Driver not found on classpath!");
        }
    }

    public static String getUrl() {
        return requiredEnv("DB_URL");
    }

    public static String getUser() {
        return requiredEnv("DB_USER");
    }

    public static String getPassword() {
        return requiredEnv("DB_PASSWORD");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required environment variable is not configured: " + name);
        return value;
    }

    // Returns an open database connection instance
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getUrl(), getUser(), getPassword());
    }

    // Safely tests if the database connection can be established
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Throwable t) {
            return false;
        }
    }
}
