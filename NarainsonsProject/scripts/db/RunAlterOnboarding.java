import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class RunAlterOnboarding {
    public static void main(String[] args) throws Exception {
        var props = new java.util.Properties();
        try (var in = java.nio.file.Files.newInputStream(Path.of("src/main/resources/db.properties"))) {
            props.load(in);
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        Path sqlFile = Path.of("database", "schema", "03_alter_onboarding.sql");
        String sql = Files.readString(sqlFile);
        try (Connection conn = DriverManager.getConnection(
                props.getProperty("db.url"), props.getProperty("db.user"), props.getProperty("db.password"));
                Statement st = conn.createStatement()) {
            for (String part : sql.split(";")) {
                String s = part.trim();
                if (!s.isEmpty() && !s.startsWith("USE")) {
                    try {
                        st.execute(s);
                        System.out.println("OK: " + s.substring(0, Math.min(60, s.length())));
                    } catch (Exception e) {
                        System.out.println("SKIP/ERR: " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("Migration finished");
    }
}
