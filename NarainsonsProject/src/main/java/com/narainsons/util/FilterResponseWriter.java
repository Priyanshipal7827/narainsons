package com.narainsons.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

public final class FilterResponseWriter {

    private FilterResponseWriter() {
    }

    public static void writeJson(HttpServletResponse response, int status, ApiResponse body) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        response.getWriter().write(GsonProvider.toJson(body));
    }
}
