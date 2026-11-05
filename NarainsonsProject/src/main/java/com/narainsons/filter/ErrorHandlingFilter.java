package com.narainsons.filter;

import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonParseException;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.FilterResponseWriter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/** Last line of defence: API clients get JSON, never Tomcat's HTML error page with a stack trace. */
public class ErrorHandlingFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(ErrorHandlingFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (JsonParseException | DateTimeParseException | IllegalStateException
                | UnsupportedOperationException | ClassCastException e) {
            LOG.log(Level.INFO, "Bad request data", e);
            write(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request data");
        } catch (RuntimeException | LinkageError | ServletException e) {
            LOG.log(Level.SEVERE, "Unhandled API error", e);
            write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
        }
    }

    private static void write(ServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) {
            response.resetBuffer();
            FilterResponseWriter.writeJson((HttpServletResponse) response, status, ApiResponse.error(ApiResponse.codeForStatus(status), message));
        }
    }
}
