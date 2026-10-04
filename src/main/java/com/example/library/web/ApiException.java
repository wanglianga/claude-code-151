package com.example.library.web;

/** 业务规则冲突（422）：如续借遇排队、保护比例不允许调拨等 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(422, message);
    }

    public int getStatus() {
        return status;
    }
}
