package com.ispengya.server.procotol;

import com.ispengya.server.common.util.SimpleServerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 21:17
 **/
public class SimpleServerDecoder extends LengthFieldBasedFrameDecoder {

    private static final Logger log = LoggerFactory.getLogger(SimpleServerDecoder.class);

    public SimpleServerDecoder() {
        super(16777216, 0, 4, 0, 4);
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            frame = (ByteBuf) super.decode(ctx, in);
            if (null == frame) {
                return null;
            }

            ByteBuffer byteBuffer = frame.nioBuffer();

            return decode(byteBuffer);
        } catch (Exception e) {
            log.error("decode exception, " + SimpleServerUtil.parseChannelRemoteAddr(ctx.channel()), e);
        } finally {
            if (null != frame) {
                frame.release();
            }
        }

        return null;
    }

    public static SimpleServerTransContext decode(final ByteBuffer byteBuffer) {
        int length = byteBuffer.limit();
        int headerLength = byteBuffer.getInt();
        byte[] headerData = new byte[headerLength];
        byteBuffer.get(headerData);

        SimpleServerTransContext sst = SimpleServerSerializable.decode(headerData, SimpleServerTransContext.class);

        int bodyLength = length - 4 - headerLength;
        byte[] bodyData = null;
        if (bodyLength > 0) {
            bodyData = new byte[bodyLength];
            byteBuffer.get(bodyData);
        }
        sst.setBody(bodyData);

        return sst;
    }

}
