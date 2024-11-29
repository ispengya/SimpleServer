package com.ispengya.server.netty.server;

import com.ispengya.server.common.constant.SimpleServerAllConstants;
import com.ispengya.server.common.util.SimpleServerUtil;
import com.ispengya.server.netty.Event;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleServerConnectManageHandler extends ChannelDuplexHandler {

        private static final Logger log = LoggerFactory.getLogger(SimpleServerConnectManageHandler.class);

        private final SimpleServer server;

        public SimpleServerConnectManageHandler(SimpleServer server) {
            this.server = server;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("SimpleServer server pipeline: channelRegistered {}", remoteAddress);
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("SimpleServer server pipeline: channelUnregistered, the channel[{}]", remoteAddress);
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("SimpleServer server pipeline: channelActive, the channel[{}]", remoteAddress);
            super.channelActive(ctx);
            if (server.getChannelEventListener() != null) {
                server.putEvent(new Event(SimpleServerAllConstants.CONNECT, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("SimpleServer server pipeline: channelInactive, the channel[{}]", remoteAddress);
            super.channelInactive(ctx);

            if (server.getChannelEventListener() != null) {
                server.putEvent(new Event(SimpleServerAllConstants.CLOSE, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
                    log.warn("SimpleServer server pipeline: idle exception [{}]", remoteAddress);
                    SimpleServerUtil.closeChannel(ctx.channel());
                    if (server.getChannelEventListener() != null) {
                        server.putEvent(new Event(SimpleServerAllConstants.IDLE, remoteAddress, ctx.channel()));
                    }
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.warn("SimpleServer server pipeline: exceptionCaught {}", remoteAddress);
            log.warn("SimpleServer server pipeline: exceptionCaught exception.", cause);

            if (server.getChannelEventListener() != null) {
                server.putEvent(new Event(SimpleServerAllConstants.EXCEPTION, remoteAddress, ctx.channel()));
            }

            SimpleServerUtil.closeChannel(ctx.channel());
        }
    }