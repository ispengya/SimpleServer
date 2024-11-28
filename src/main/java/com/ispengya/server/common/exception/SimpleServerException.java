package com.ispengya.server.common.exception;

public class SimpleServerException extends Exception {

    public SimpleServerException(String message) {
        super(message);
    }

    public SimpleServerException(String message, Throwable cause) {
        super(message, cause);
    }
}