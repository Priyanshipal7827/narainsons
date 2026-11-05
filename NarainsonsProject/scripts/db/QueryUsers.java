import java.sql.*;

public class QueryUsers {

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
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = openDb();
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "SELECT id, mobile_number, email, password_hash IS NOT NULL AS has_pwd, "
                            + "otp_hash IS NOT NULL AS has_otp, mobile_verified_at, created_at "
                            + "FROM users WHERE deleted_at IS NULL ORDER BY id DESC LIMIT 10")) {
                while (rs.next()) {
                    System.out.printf("id=%d mobile=%s email=%s has_pwd=%s has_otp=%s verified=%s created=%s%n",
                            rs.getLong("id"),
                            rs.getString("mobile_number"),
                            rs.getString("email"),
                            rs.getBoolean("has_pwd"),
                            rs.getBoolean("has_otp"),
                            rs.getString("mobile_verified_at"),
                            rs.getString("created_at"));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) AS n FROM users WHERE deleted_at IS NULL")) {
                rs.next();
                System.out.println("total_users=" + rs.getInt("n"));
            }
        }
    }
}
