package com.ispengya.server.procotol;

import com.ispengya.server.CustomHeader;
import com.ispengya.server.common.util.SimpleServerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 20:30
 **/
public class SimpleServerEncoder extends MessageToByteEncoder<SimpleServerTransContext> {

    private static final Logger log = LoggerFactory.getLogger(SimpleServerEncoder.class);

    private static final Map<Class<? extends CustomHeader>, Field[]> CLASS_HASH_MAP = new HashMap<Class<? extends CustomHeader>, Field[]>();

    /**
     * 传输格式：4字节总长度（4 + header length + body length）、4字节header长度、header数据、body
     */
    @Override
    protected void encode(ChannelHandlerContext chc, SimpleServerTransContext sst, ByteBuf byteBuf) throws Exception {
        try {
            encode(sst, byteBuf);
        } catch (Exception e) {
            log.error("encode exception, " + SimpleServerUtil.parseChannelRemoteAddr(chc.channel()), e);
        }
    }

    private void encode(SimpleServerTransContext sst, ByteBuf out) {
        //header length size
        int length = 4;
        //body data length
        int bodyLength = sst.getBody() == null ? 0 : sst.getBody().length;
        length += bodyLength;
        //header data
        byte[] headerData = this.headerEncode(sst);
        //header length
        length += headerData.length;

        //write header
        ByteBuffer header = ByteBuffer.allocate(4 + length - bodyLength);
        // length
        header.putInt(length);
        // header length
        header.putInt(headerData.length);
        // header data
        header.put(headerData);
        header.flip();
        //write header
        out.writeBytes(header);
        //write body data
        if (sst.getBody() != null) {
            out.writeBytes(sst.getBody());
        }
    }

    private byte[] headerEncode(SimpleServerTransContext sst) {
        CustomHeader customHeader = sst.getCustomHeader();
        if (customHeader != null) {
            Field[] fields = getClazzFields(customHeader.getClass());
            HashMap<String, String> customFields = sst.getCustomFields();
            if (null == customFields) {
                customFields = new HashMap<>();
            }
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    String name = field.getName();
                    if (!name.startsWith("this")) {
                        Object value = null;
                        try {
                            field.setAccessible(true);
                            value = field.get(customHeader);
                        } catch (Exception e) {
                            log.error("Failed to access field [{}]", name, e);
                        }

                        if (value != null) {
                            customFields.put(name, value.toString());
                        }
                    }
                }
            }
            sst.setCustomFields(customFields);
        }
        return SimpleServerSerializable.encode(sst);
    }

    private Field[] getClazzFields(Class<? extends CustomHeader> classHeader) {
        Field[] field = CLASS_HASH_MAP.get(classHeader);
        if (field == null) {
            field = classHeader.getDeclaredFields();
            synchronized (CLASS_HASH_MAP) {
                CLASS_HASH_MAP.put(classHeader, field);
            }
        }
        return field;
    }


}
