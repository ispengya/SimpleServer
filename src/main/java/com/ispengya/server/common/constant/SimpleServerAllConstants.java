package com.ispengya.server.common.constant;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 20:21
 **/
public class SimpleServerAllConstants {
    /**
     * request type
     */
    public static final int REQUEST_FLAG = 0;
    public static final int RESPONSE_FLAG = 1;

    /**
     * event type
     */
    //处理事件类型
    public static final int CONNECT = 1000;
    public static final int CLOSE = 1001;
    public static final int EXCEPTION = 1002;
    public static final int IDLE = 1003;

    /**
     * statusCode
     */
    public static final int SUCCESS = 0;
    public static final int SYSTEM_ERROR = 1;
    public static final int SYSTEM_BUSY = 2;
    public static final int REQUEST_CODE_NOT_SUPPORTED = 3;
}
