package com.ispengya.server;

import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.ChannelHandlerContext;

public interface SimpleServerProcessor {
    SimpleServerTransContext processRequest(ChannelHandlerContext chc, SimpleServerTransContext request)
            throws Exception;
    boolean rejectRequest();
}
