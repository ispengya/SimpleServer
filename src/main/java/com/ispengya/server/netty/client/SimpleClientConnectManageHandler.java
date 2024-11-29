package com.ispengya.server.netty.client;

import com.ispengya.server.common.constant.SimpleServerAllConstants;
import com.ispengya.server.common.util.ChannelWrapper;
import com.ispengya.server.common.util.SimpleServerUtil;
import com.ispengya.server.netty.Event;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 14:59
 **/
public class SimpleClientConnectManageHandler extends ChannelDuplexHandler {

    private static final Logger log = LoggerFactory.getLogger(SimpleClientConnectManageHandler.class);

    private final SimpleClient client;

    public SimpleClientConnectManageHandler(SimpleClient client) {
        this.client = client;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        final String local = localAddress == null ? "UNKNOWN" : SimpleServerUtil.parseSocketAddressAddr(localAddress);
        final String remote = remoteAddress == null ? "UNKNOWN" : SimpleServerUtil.parseSocketAddressAddr(remoteAddress);
        log.info("simple client pipeline: CONNECT  {} => {}", local, remote);

        super.connect(ctx, remoteAddress, localAddress, promise);

        if (client.getChannelEventListener() != null) {
           client.putEvent(new Event(SimpleServerAllConstants.CONNECT, remote, ctx.channel()));
        }
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
        log.info("simple client pipeline: DISCONNECT {}", remoteAddress);
        client.closeChannel(ctx.channel());
        super.disconnect(ctx, promise);

        if (client.getChannelEventListener() != null) {
            client.putEvent(new Event(SimpleServerAllConstants.CLOSE, remoteAddress, ctx.channel()));
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
        log.info("simple client pipeline: CLOSE {}", remoteAddress);
        client.closeChannel(ctx.channel());
        super.close(ctx, promise);
        client.failFast(ctx.channel());
        if (client.getChannelEventListener() != null) {
            client.putEvent(new Event(SimpleServerAllConstants.CLOSE, remoteAddress, ctx.channel()));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.ALL_IDLE)) {
                final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
                log.warn("simple client pipeline: IDLE exception [{}]", remoteAddress);
                client.closeChannel(ctx.channel());
                if (client.getChannelEventListener() != null) {
                    client
                            .putEvent(new Event(SimpleServerAllConstants.IDLE, remoteAddress, ctx.channel()));
                }
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
        log.warn("simple client pipeline: exceptionCaught {}", remoteAddress);
        log.warn("simple client pipeline: exceptionCaught exception.", cause);
        client.closeChannel(ctx.channel());
        if (client.getChannelEventListener() != null) {
            client.putEvent(new Event(SimpleServerAllConstants.EXCEPTION, remoteAddress, ctx.channel()));
        }
    }
}
