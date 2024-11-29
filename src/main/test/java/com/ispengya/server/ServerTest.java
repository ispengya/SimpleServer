package com.ispengya.server;

import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.netty.server.ServerConfig;
import com.ispengya.server.netty.server.SimpleServer;
import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.Executors;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 16:36
 **/
public class ServerTest {

    public static SimpleServer createSimpleServer() throws InterruptedException, SimpleServerException {
        ServerConfig config = new ServerConfig();
        SimpleServer server = new SimpleServer(config);
        server.registerProcessor(0, new SimpleServerProcessor() {
            @Override
            public SimpleServerTransContext processRequest(ChannelHandlerContext ctx, SimpleServerTransContext request) {
                request.setRequestId(666);
                return request;
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        }, Executors.newCachedThreadPool());

        server.start();

        return server;
    }

    public static void main(String[] args) throws InterruptedException, SimpleServerException {
        createSimpleServer();
    }
}
