package com.narainsons.middleware;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request attributes — baad ke servlet/DAO mein use kar sakte ho.
 */
public final class RequestContext {

    public static final String REQUEST_ID = "requestId";
    public static final String AUTH_SUBJECT = "authSubject";

    private RequestContext() {
    }

    public static String getRequestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID);
        return value == null ? null : value.toString();
    }

    public static void setAuthSubject(HttpServletRequest request, String subject) {
        request.setAttribute(AUTH_SUBJECT, subject);
    }

    public static String getAuthSubject(HttpServletRequest request) {
        Object value = request.getAttribute(AUTH_SUBJECT);
        return value == null ? null : value.toString();
    }
}
