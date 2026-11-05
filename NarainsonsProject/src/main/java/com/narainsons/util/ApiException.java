package com.narainsons.util;

/**
 * Service layer errors with an HTTP status and a stable error_code for app developers.
 * Extends IllegalArgumentException so existing catch blocks keep working;
 * {@link ServletUtil#writeError} turns it into the response.
 */
public class ApiException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public ApiException(int status, String message) {
        this(status, null, message);
    }

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    /** May be null; the status then decides the code. */
    public String code() {
        return code;
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, message);
    }

    public static ApiException unavailable(String message) {
        return new ApiException(503, message);
    }
}
