import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Creates or updates a super admin. */
public class SeedAdmin {

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
        if (args.length < 3) {
            System.out.println("Usage: SeedAdmin <email> <password> \"Full Name\" [mobile]");
            return;
        }
        String email = args[0];
        String fullName = args[2];
        String mobile = args.length > 3 ? args[3] : null;
        String password = args[1];
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        try (Connection c = openDb()) {
            Long existingId = null;
            try (PreparedStatement chk = c.prepareStatement(
                    "SELECT id FROM admins WHERE email=? AND deleted_at IS NULL")) {
                chk.setString(1, email);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getLong("id");
                    }
                }
            }
            if (existingId != null) {
                try (PreparedStatement upd = c.prepareStatement("""
                        UPDATE admins SET password_hash=?, full_name=?, role='super_admin',
                        account_status='active', updated_at=NOW() WHERE id=?
                        """)) {
                    upd.setString(1, hash);
                    upd.setString(2, fullName);
                    upd.setLong(3, existingId);
                    upd.executeUpdate();
                }
                System.out.println("UPDATED admin id=" + existingId + " email=" + email);
            } else {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO admins (email, password_hash, full_name, mobile_number, role, account_status)
                        VALUES (?,?,?,?,?,?)
                        """)) {
                    ps.setString(1, email);
                    ps.setString(2, hash);
                    ps.setString(3, fullName);
                    ps.setString(4, mobile);
                    ps.setString(5, "super_admin");
                    ps.setString(6, "active");
                    ps.executeUpdate();
                }
                System.out.println("CREATED admin email=" + email);
            }
            System.out.println("Done. Login with: " + email);
        }
    }
}
