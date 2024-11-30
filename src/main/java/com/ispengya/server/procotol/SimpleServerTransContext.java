package com.ispengya.server.procotol;

import com.ispengya.server.CustomHeader;
import com.ispengya.server.common.constant.SimpleServerAllConstants;
import com.ispengya.server.common.exception.SimpleServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description: SimpleServer传输内容
 * @author: hanzhipeng
 * @create: 2024-11-28 20:16
 **/
public class SimpleServerTransContext {

    private static final Map<Class, String> CANONICAL_NAME_CACHE = new HashMap<Class, String>();
    private static final Map<Class<? extends CustomHeader>, Field[]> CLASS_HASH_MAP = new HashMap<Class<? extends CustomHeader>, Field[]>();
    private static final Map<Field, Boolean> NULLABLE_FIELD_CACHE = new HashMap<Field, Boolean>();
    private static final String STRING_CANONICAL_NAME = String.class.getCanonicalName();
    private static final String DOUBLE_CANONICAL_NAME_1 = Double.class.getCanonicalName();
    private static final String DOUBLE_CANONICAL_NAME_2 = double.class.getCanonicalName();
    private static final String INTEGER_CANONICAL_NAME_1 = Integer.class.getCanonicalName();
    private static final String INTEGER_CANONICAL_NAME_2 = int.class.getCanonicalName();
    private static final String LONG_CANONICAL_NAME_1 = Long.class.getCanonicalName();
    private static final String LONG_CANONICAL_NAME_2 = long.class.getCanonicalName();
    private static final String BOOLEAN_CANONICAL_NAME_1 = Boolean.class.getCanonicalName();
    private static final String BOOLEAN_CANONICAL_NAME_2 = boolean.class.getCanonicalName();
    private static final AtomicInteger requestIdWorker = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(SimpleServerTransContext.class);

    /**
     * 状态
     */
    private int statusCode = SimpleServerAllConstants.SUCCESS;
    /**
     * 处理器对应code
     */
    private int processCode;
    /**
     * 请求标识
     */
    private int requestId = 0;
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

    public static SimpleServerTransContext createRequestSST(int processCode, CustomHeader customHeader) {
        SimpleServerTransContext sst = new SimpleServerTransContext();
        sst.requestId = requestIdWorker.getAndIncrement();
        sst.setProcessCode(processCode);
        sst.customHeader = customHeader;
        return sst;
    }

    public static SimpleServerTransContext createResponseSST(int statusCode) {
        return createResponseSST(statusCode, null);
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

    public CustomHeader decodeSSTCustomHeader(
            Class<? extends CustomHeader> classHeader) throws SimpleServerException {
        CustomHeader objectHeader;
        try {
            objectHeader = classHeader.newInstance();
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }

        if (this.customFields != null) {

            Field[] fields = getClazzFields(classHeader);
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    String fieldName = field.getName();
                    if (!fieldName.startsWith("this")) {
                        try {
                            String value = this.customFields.get(fieldName);
                            if (null == value) {
                                continue;
                            }

                            field.setAccessible(true);
                            String type = getCanonicalName(field.getType());
                            Object valueParsed;

                            if (type.equals(STRING_CANONICAL_NAME)) {
                                valueParsed = value;
                            } else if (type.equals(INTEGER_CANONICAL_NAME_1) || type.equals(INTEGER_CANONICAL_NAME_2)) {
                                valueParsed = Integer.parseInt(value);
                            } else if (type.equals(LONG_CANONICAL_NAME_1) || type.equals(LONG_CANONICAL_NAME_2)) {
                                valueParsed = Long.parseLong(value);
                            } else if (type.equals(BOOLEAN_CANONICAL_NAME_1) || type.equals(BOOLEAN_CANONICAL_NAME_2)) {
                                valueParsed = Boolean.parseBoolean(value);
                            } else if (type.equals(DOUBLE_CANONICAL_NAME_1) || type.equals(DOUBLE_CANONICAL_NAME_2)) {
                                valueParsed = Double.parseDouble(value);
                            } else {
                                throw new SimpleServerException("the custom field <" + fieldName + "> type is not supported");
                            }

                            field.set(objectHeader, valueParsed);

                        } catch (Throwable e) {
                            log.error("Failed field [{}] decoding", fieldName, e);
                        }
                    }
                }
            }

        }
        return objectHeader;
    }

    public Field[] getClazzFields(Class<? extends CustomHeader> classHeader) {
        Field[] field = CLASS_HASH_MAP.get(classHeader);
        if (field == null) {
            field = classHeader.getDeclaredFields();
            synchronized (CLASS_HASH_MAP) {
                CLASS_HASH_MAP.put(classHeader, field);
            }
        }
        return field;
    }

    private String getCanonicalName(Class clazz) {
        String name = CANONICAL_NAME_CACHE.get(clazz);

        if (name == null) {
            name = clazz.getCanonicalName();
            synchronized (CANONICAL_NAME_CACHE) {
                CANONICAL_NAME_CACHE.put(clazz, name);
            }
        }
        return name;
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
