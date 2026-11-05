package com.narainsons.filter;

import java.io.IOException;

import com.narainsons.util.ApiResponse;
import com.narainsons.util.FilterResponseWriter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CachedBodyFilter implements Filter {

    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = httpRequest.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            FilterResponseWriter.writeJson((HttpServletResponse) response, 413, ApiResponse.error(ApiResponse.codeForStatus(413), "Request body too large"));
            return;
        }
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(httpRequest, body);
        chain.doFilter(wrapped, response);
    }
}
