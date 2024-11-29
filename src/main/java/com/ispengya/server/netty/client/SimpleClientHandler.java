package com.ispengya.server.netty.client;

import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 14:59
 **/
public class SimpleClientHandler extends SimpleChannelInboundHandler<SimpleServerTransContext> {

    private final SimpleClient client;

    public SimpleClientHandler(SimpleClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, SimpleServerTransContext simpleServerTransContext) throws Exception {
        client.processMessage(channelHandlerContext, simpleServerTransContext);
    }
}
