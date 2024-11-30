package com.ispengya.server;

import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.Channel;

/**
 * 不同场景，构建不同业务
 */
public interface SimpleClientService {
    SimpleServerTransContext invokeSync(final String addr, final SimpleServerTransContext request,
                                        final long timeoutMillis) throws Exception;
    void invokeAsync(final String addr, final SimpleServerTransContext request, final long timeoutMillis,
                     final InvokeCallback invokeCallback) throws Exception;
    void invokeOneway(final String addr, final SimpleServerTransContext request, final long timeoutMillis) throws Exception;
}
