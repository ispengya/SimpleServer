package com.ispengya.server.common.exception;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 19:47
 **/
public class SimpServerInvokeException extends SimpleServerException {

    public SimpServerInvokeException(String addr) {
        this(addr, null);
    }

    public SimpServerInvokeException(String addr, Throwable cause) {
        super(String.format("%s invoke error", addr), cause);
    }
}
