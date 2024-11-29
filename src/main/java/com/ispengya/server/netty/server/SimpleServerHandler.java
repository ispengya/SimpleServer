package com.ispengya.server.netty.server;

import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-27 16:52
 **/
public class SimpleServerHandler extends SimpleChannelInboundHandler<SimpleServerTransContext> {

    private final SimpleServer simpleServer;

    public SimpleServerHandler(SimpleServer simpleServer) {
        this.simpleServer = simpleServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SimpleServerTransContext msg) throws Exception {
        this.simpleServer.processMessage(ctx, msg);
    }
}