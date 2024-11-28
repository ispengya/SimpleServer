package com.ispengya.server.common.exception;

import java.rmi.ServerException;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 20:33
 **/
public class SimpleServerEncoderException extends SimpleServerException {

    public SimpleServerEncoderException(String message) {
        this(message, null);
    }

    public SimpleServerEncoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
