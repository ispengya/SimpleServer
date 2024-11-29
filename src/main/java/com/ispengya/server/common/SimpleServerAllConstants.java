package com.ispengya.server.common;

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
    public static final int ClOSE = 1001;
    public static final int EXCEPTION = 1002;
    public static final int IDLE = 1003;
}
