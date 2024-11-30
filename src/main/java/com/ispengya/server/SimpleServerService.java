package com.ispengya.server;

import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.Channel;

import java.util.concurrent.ExecutorService;

public interface SimpleServerService {
    SimpleServerTransContext invokeSync(final Channel channel, final SimpleServerTransContext request,
                                        final long timeoutMillis) throws Exception;

    void invokeAsync(final Channel channel, final SimpleServerTransContext request, final long timeoutMillis,
                     final InvokeCallback invokeCallback) throws Exception;

    void invokeOneway(final Channel channel, final SimpleServerTransContext request, final long timeoutMillis)
            throws Exception;

    void registerDefaultProcessor(SimpleServerProcessor processor, ExecutorService executor);
}
