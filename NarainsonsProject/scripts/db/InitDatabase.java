import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class InitDatabase {
    private static String dbUrl;
    private static String user;
    private static String password;

    public static void main(String[] args) throws Exception {
        // Run from NarainsonsProject/. Connection details come from db.properties.
        var props = new java.util.Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/db.properties"))) {
            props.load(in);
        }
        dbUrl = props.getProperty("db.url");
        user = props.getProperty("db.user");
        password = props.getProperty("db.password");
        String baseUrl = dbUrl.replaceFirst("/narainson_db\\?", "/?");
        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection conn = DriverManager.getConnection(baseUrl, user, password);
                Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS narainson_db "
                    + "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            st.executeUpdate("USE narainson_db");
        }

        // Run from NarainsonsProject/. Files run in name order: 02_users_schema.sql, 03_..., 09_...
        Path schemaDir = Path.of("database", "schema");
        if (!Files.isDirectory(schemaDir)) {
            throw new IllegalStateException("database/schema nahi mila - NarainsonsProject folder se chalao");
        }
        try (var files = Files.list(schemaDir)) {
            for (Path file : files.sorted().toList()) {
                // 01_create_database.sql is covered by the CREATE DATABASE above.
                if (file.getFileName().toString().endsWith(".sql")
                        && !file.getFileName().toString().startsWith("01_")) {
                    runAlterStatements(Files.readString(file));
                }
            }
        }

        System.out.println("OK: narainson_db database and users table ready");
    }

    private static void runAlterStatements(String sqlFile) throws Exception {
        // Drop comment lines first: a comment above a statement must not hide that statement.
        String sql = sqlFile.replaceAll("(?m)^\\s*--.*$", "").replace("USE narainson_db;", "");
        String[] statements = sql.split(";");
        try (Connection conn = DriverManager.getConnection(dbUrl, user, password);
                Statement st = conn.createStatement()) {
            for (String part : statements) {
                String s = part.trim();
                if (s.isEmpty() || s.startsWith("--")) {
                    continue;
                }
                try {
                    st.execute(s);
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (msg.contains("Duplicate column") || msg.contains("Duplicate key name")) {
                        continue;
                    }
                    throw e;
                }
            }
        }
    }
}
