package com.ispengya.server.procotol;

import com.ispengya.server.CustomHeader;
import com.ispengya.server.common.constant.SimpleServerAllConstants;

import java.util.Arrays;
import java.util.HashMap;

/**
 * @description: SimpleServer传输内容
 * @author: hanzhipeng
 * @create: 2024-11-28 20:16
 **/
public class SimpleServerTransContext {
    /**
     * 状态
     */
    private int statusCode;
    /**
     * 处理器对应code
     */
    private int processCode;
    /**
     * 请求标识
     */
    private int requestId;
    /**
     * 请求：0  响应：1
     */
    private int flag = SimpleServerAllConstants.REQUEST_FLAG;
    /**
     * 请求是否单向
     */
    private boolean isOneWay = false;
    /**
     * 自定义字段
     */
    private HashMap<String, String> customFields;
    /**
     * 数据
     */
    private transient byte[] body;
    /**
     * customFields对应实体
     */
    private transient CustomHeader customHeader;


    public static SimpleServerTransContext createResponseSST(int statusCode) {
        return createResponseSST(statusCode);
    }

    public static SimpleServerTransContext createResponseSST(int code, Class<? extends CustomHeader> classHeader) {
        SimpleServerTransContext sst = new SimpleServerTransContext();
        sst.setFlag(SimpleServerAllConstants.RESPONSE_FLAG);
        sst.setStatusCode(code);

        if (classHeader != null) {
            try {
                CustomHeader objectHeader = classHeader.newInstance();
                sst.customHeader = objectHeader;
            } catch (InstantiationException | IllegalAccessException e) {
                return null;
            }
        }
        return sst;
    }

    public int getProcessCode() {
        return processCode;
    }

    public void setProcessCode(int processCode) {
        this.processCode = processCode;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public boolean isOneWay() {
        return isOneWay;
    }

    public void setOneWay(boolean oneWay) {
        isOneWay = oneWay;
    }

    public HashMap<String, String> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(HashMap<String, String> customFields) {
        this.customFields = customFields;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public CustomHeader getCustomHeader() {
        return customHeader;
    }

    public void setCustomHeader(CustomHeader customHeader) {
        this.customHeader = customHeader;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        return "SimpleServerTransContext{" +
                "statusCode=" + statusCode +
                ", processCode=" + processCode +
                ", requestId=" + requestId +
                ", flag=" + flag +
                ", isOneWay=" + isOneWay +
                ", customFields=" + customFields +
                ", body=" + Arrays.toString(body) +
                ", customHeader=" + customHeader +
                '}';
    }
}
