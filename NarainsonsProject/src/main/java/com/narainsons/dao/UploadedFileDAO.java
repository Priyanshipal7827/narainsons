package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.narainsons.util.DBConnection;

public class UploadedFileDAO {

    public record FileRow(String id, long userId, String purpose, String storedName, String contentType, long sizeBytes) {
    }

    public void insert(FileRow file) throws SQLException {
        String sql = """
                INSERT INTO uploaded_files (id, user_id, purpose, stored_name, content_type, size_bytes)
                VALUES (?,?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, file.id());
            ps.setLong(2, file.userId());
            ps.setString(3, file.purpose());
            ps.setString(4, file.storedName());
            ps.setString(5, file.contentType());
            ps.setLong(6, file.sizeBytes());
            ps.executeUpdate();
        }
    }

    public Optional<FileRow> findById(String id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM uploaded_files WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FileRow(rs.getString("id"), rs.getLong("user_id"), rs.getString("purpose"),
                        rs.getString("stored_name"), rs.getString("content_type"), rs.getLong("size_bytes")));
            }
        }
    }
}
