import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class VerifyAdminLogin {

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
        if (args.length != 2) {
            System.out.println("Usage: VerifyAdminLogin <email> <password>");
            return;
        }
        String email = args[0];
        String password = args[1];
        try (Connection c = openDb();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, email, password_hash, account_status FROM admins WHERE email=? AND deleted_at IS NULL")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("FAIL: admin row nahi mila email=" + email);
                    return;
                }
                String hash = rs.getString("password_hash");
                boolean ok = BCrypt.checkpw(password, hash);
                System.out.println("id=" + rs.getLong("id") + " status=" + rs.getString("account_status"));
                System.out.println("BCrypt check: " + ok);
            }
        }
    }
}
