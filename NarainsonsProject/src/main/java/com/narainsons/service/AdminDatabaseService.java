package com.narainsons.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.narainsons.dao.AdminDatabaseBrowseDAO;

public class AdminDatabaseService {

    private final AdminDatabaseBrowseDAO browseDAO = new AdminDatabaseBrowseDAO();

    public Map<String, Object> browseAll(int limitPerTable, boolean includeAdmins) throws SQLException {
        int limit = Math.min(Math.max(limitPerTable, 1), 1000);
        return browseDAO.allTables(limit, includeAdmins);
    }

    public Map<String, Object> browseTable(String table, int limit) throws SQLException {
        if (!browseDAO.isAllowedTable(table)) {
            throw new IllegalArgumentException("Table not allowed: " + table);
        }
        int capped = Math.min(Math.max(limit, 1), 1000);
        return browseDAO.tableBundle(table, capped);
    }

    public List<String> listTables() {
        return AdminDatabaseBrowseDAO.orderedTableLabels();
    }
}
