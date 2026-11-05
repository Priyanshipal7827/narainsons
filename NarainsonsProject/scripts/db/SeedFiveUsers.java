import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 5 test users — email seed1..5@narainsons.local, random passwords printed when it runs
 * Mobile 9100000001 .. 9100000005
 */
public class SeedFiveUsers {

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


    record SeedUser(String email, String plainPassword, String mobile, String firstName) {
    }

    private static String randomPassword() {
        return "Seed-" + java.util.UUID.randomUUID().toString().substring(0, 12);
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        SeedUser[] seeds = {
                new SeedUser("seed1@narainsons.local", randomPassword(), "9100000001", "Seed One"),
                new SeedUser("seed2@narainsons.local", randomPassword(), "9100000002", "Seed Two"),
                new SeedUser("seed3@narainsons.local", randomPassword(), "9100000003", "Seed Three"),
                new SeedUser("seed4@narainsons.local", randomPassword(), "9100000004", "Seed Four"),
                new SeedUser("seed5@narainsons.local", randomPassword(), "9100000005", "Seed Five"),
        };

        try (Connection c = openDb()) {
            for (SeedUser s : seeds) {
                softDeleteByEmailOrMobile(c, s.email(), s.mobile());
            }
            for (int i = 0; i < seeds.length; i++) {
                SeedUser s = seeds[i];
                String hash = BCrypt.hashpw(s.plainPassword(), BCrypt.gensalt(12));
                String sql = """
                        INSERT INTO users (
                            customer_code, name, first_name, mobile_number, email,
                            nationality, current_country, permanent_country,
                            kyc_status, bank_verification_status, account_status,
                            login_attempts, notification_enable, is_app_installed,
                            privacy_consent, terms_accepted, kyc_consent, onboarding_step,
                            password_hash, mobile_verified_at, email_verified_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())
                        """;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    int n = 1;
                    ps.setString(n++, "SEED" + (i + 1) + System.currentTimeMillis());
                    ps.setString(n++, s.firstName());
                    ps.setString(n++, s.firstName());
                    ps.setString(n++, s.mobile());
                    ps.setString(n++, s.email());
                    ps.setString(n++, "Indian");
                    ps.setString(n++, "India");
                    ps.setString(n++, "India");
                    ps.setString(n++, "pending");
                    ps.setString(n++, "pending");
                    ps.setString(n++, "active");
                    ps.setInt(n++, 0);
                    ps.setBoolean(n++, true);
                    ps.setBoolean(n++, true);
                    ps.setBoolean(n++, false);
                    ps.setBoolean(n++, false);
                    ps.setBoolean(n++, false);
                    ps.setInt(n++, 0);
                    ps.setString(n++, hash);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            long id = keys.getLong(1);
                            System.out.printf("CREATED id=%d email=%s password=%s mobile=%s%n",
                                    id, s.email(), s.plainPassword(), s.mobile());
                        }
                    }
                }
            }
            System.out.println("--- active seed users ---");
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("""
                            SELECT id, email, mobile_number FROM users
                            WHERE deleted_at IS NULL AND email LIKE 'seed%@narainsons.local'
                            ORDER BY id
                            """)) {
                while (rs.next()) {
                    System.out.printf("id=%d %s %s%n",
                            rs.getLong("id"), rs.getString("email"), rs.getString("mobile_number"));
                }
            }
        }
    }

    private static void softDeleteByEmailOrMobile(Connection c, String email, String mobile) throws Exception {
        String sql = """
                UPDATE users SET deleted_at=NOW()
                WHERE deleted_at IS NULL AND (email=? OR mobile_number=?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, mobile);
            ps.executeUpdate();
        }
    }
}
