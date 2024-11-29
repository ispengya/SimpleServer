package com.ispengya.server.common.exception;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 19:38
 **/
public class SimpleServerTimeOutException extends SimpleServerException {

    public SimpleServerTimeOutException(String message) {
        super(message);
    }

    public SimpleServerTimeOutException(String addr, long timeoutMillis) {
        this(addr, timeoutMillis, null);
    }

    public SimpleServerTimeOutException(String addr, long timeoutMillis, Throwable cause) {
        super(String.format("%s handler timeout %s (ms)", addr, timeoutMillis), cause);
    }

}
