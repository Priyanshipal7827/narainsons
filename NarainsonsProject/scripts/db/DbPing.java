import java.sql.Connection;
import java.sql.DriverManager;

public class DbPing {

    private static java.util.Properties db() throws Exception {
        var props = new java.util.Properties();
        try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/main/resources/db.properties"))) {
            props.load(in);
        }
        return props;
    }

    private static Connection openDb() throws Exception {
        var p = db();
        return DriverManager.getConnection(p.getProperty("db.url"), p.getProperty("db.user"), p.getProperty("db.password"));
    }
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (var conn = openDb()) {
                System.out.println("OK: connected, valid=" + conn.isValid(2));
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            Throwable cause = e.getCause();
            if (cause != null) {
                System.out.println("CAUSE: " + cause.getMessage());
            }
        }
    }
}
