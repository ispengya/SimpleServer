package com.ispengya.server.netty.server;

import com.ispengya.server.common.util.SimpleServerUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
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
            log.info("Simple server pipeline: channelRegistered {}", remoteAddress);
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("Simple server pipeline: channelUnregistered, the channel[{}]", remoteAddress);
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("Simple server pipeline: channelActive, the channel[{}]", remoteAddress);
            super.channelActive(ctx);
            if (server.getChannelEventListener() != null) {
                NettyRemotingServer.this.putNettyEvent(new NettyEvent(NettyEventType.CONNECT, remoteAddress, ctx.channel()));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("Simple server pipeline: channelInactive, the channel[{}]", remoteAddress);
            super.channelInactive(ctx);

//            if (NettyRemotingServer.this.channelEventListener != null) {
//                NettyRemotingServer.this.putNettyEvent(new NettyEvent(NettyEventType.CLOSE, remoteAddress, ctx.channel()));
//            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
//                if (event.state().equals(IdleState.ALL_IDLE)) {
//                    final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
//                    log.warn("NETTY SERVER PIPELINE: IDLE exception [{}]", remoteAddress);
//                    RemotingUtil.closeChannel(ctx.channel());
//                    if (NettyRemotingServer.this.channelEventListener != null) {
//                        NettyRemotingServer.this
//                                .putNettyEvent(new NettyEvent(NettyEventType.IDLE, remoteAddress, ctx.channel()));
//                    }
//                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final String remoteAddress = SimpleServerUtil.parseChannelRemoteAddr(ctx.channel());
            log.warn("Simple server pipeline: exceptionCaught {}", remoteAddress);
            log.warn("Simple server pipeline: exceptionCaught exception.", cause);

//            if (NettyRemotingServer.this.channelEventListener != null) {
//                NettyRemotingServer.this.putNettyEvent(new NettyEvent(NettyEventType.EXCEPTION, remoteAddress, ctx.channel()));
//            }
//
//            RemotingUtil.closeChannel(ctx.channel());
        }
    }