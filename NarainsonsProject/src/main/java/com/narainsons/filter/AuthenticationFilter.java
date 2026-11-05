package com.narainsons.filter;

import java.io.IOException;

import com.narainsons.config.AppConfig;
import com.narainsons.middleware.RequestContext;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.FilterResponseWriter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Auth middleware — API key / Bearer token (app.properties se).
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (!AppConfig.isAuthEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = normalizePath(req);
        if (AppConfig.getPublicPaths().contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(req);
        String expected = AppConfig.getAuthApiKey();
        if (token == null || expected.isBlank() || !expected.equals(token)) {
            FilterResponseWriter.writeJson(res, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.error("Unauthorized — valid API token chahiye"));
            return;
        }

        RequestContext.setAuthSubject(req, "api-client");
        chain.doFilter(request, response);
    }

    private static String normalizePath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String context = req.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.isEmpty() ? "/" : uri;
    }

    private static String extractToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        String apiKey = req.getHeader("X-API-Key");
        return apiKey == null ? null : apiKey.trim();
    }
}
