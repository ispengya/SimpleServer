package com.ispengya.server;

import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.netty.Event;
import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.ExecutorService;

public interface SimpleServerService {
    void startServer() throws SimpleServerException;
    void processMessage(ChannelHandlerContext ctx, SimpleServerTransContext transContext) throws SimpleServerException;
    void registerProcessor(int requestCode, SimpleServerProcessor processor, ExecutorService executor);
    void putEvent(Event event);
}
