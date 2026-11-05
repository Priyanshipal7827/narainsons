package com.narainsons.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.narainsons.util.ApiResponse;
import com.narainsons.util.DBConnection;
import com.narainsons.util.JwtService;
import com.narainsons.util.GsonProvider;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HealthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(HealthServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");

        boolean dbOk = false;
        String problem = "Database connection failed";
        try (Connection conn = DBConnection.getConnection()) {
            dbOk = conn.isValid(2);
            problem = "Database schema is out of date. Run scripts/db/InitDatabase.java";
            // Newest migrations: fail when database/schema/11 or 12 has not been run.
            try (var ps = conn.prepareStatement("SELECT token_version FROM admins LIMIT 0");
                    var us = conn.prepareStatement("SELECT token_version, otp_attempts FROM users LIMIT 0");
                    var ls = conn.prepareStatement("SELECT closed_at FROM loan_applications LIMIT 0");
                    var fs = conn.prepareStatement("SELECT id FROM uploaded_files LIMIT 0");
                    var as = conn.prepareStatement("SELECT alerts_read_id FROM admins LIMIT 0");
                    var an = conn.prepareStatement("SELECT id FROM admin_notifications LIMIT 0")) {
                ps.executeQuery().close();
                us.executeQuery().close();
                ls.executeQuery().close();
                fs.executeQuery().close();
                as.executeQuery().close();
                an.executeQuery().close();
            }
            if (!JwtService.isConfigured()) {
                dbOk = false;
                problem = "jwt.secret is missing in secrets.properties";
            }
        } catch (Exception e) {
            dbOk = false;
            LOG.log(Level.SEVERE, problem, e);
        }

        ApiResponse body = dbOk ? ApiResponse.ok("Application running", null) : ApiResponse.error(problem);

        response.setStatus(dbOk ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.getWriter().write(GsonProvider.toJson(body));
    }
}
