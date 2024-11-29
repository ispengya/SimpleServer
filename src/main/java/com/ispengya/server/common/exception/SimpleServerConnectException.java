package com.ispengya.server.common.exception;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 19:32
 **/
public class SimpleServerConnectException extends SimpleServerException{

    public SimpleServerConnectException(String addr) {
        this(addr, null);
    }
    public SimpleServerConnectException(String addr, Throwable cause) {
        super(String.format("%s connect failed", addr), cause);
    }
}
