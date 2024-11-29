package com.ispengya.server;

import com.ispengya.server.common.exception.RemotingCommandException;
import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.netty.NettyClientConfig;
import com.ispengya.server.netty.NettyRemotingClient;
import com.ispengya.server.netty.client.ClientConfig;
import com.ispengya.server.netty.client.SimpleClient;
import com.ispengya.server.procotol.RemotingCommand;
import com.ispengya.server.procotol.SimpleServerTransContext;
import com.sun.istack.internal.Nullable;



/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 16:13
 **/
public class ClientTest {


    public static SimpleClient createSimpleClient() throws SimpleServerException {
        SimpleClient client = new SimpleClient(new ClientConfig());
        client.start();
        return client;
    }



    public static void main(String[] args) throws Exception {
        SimpleClient client = createSimpleClient();
        RequestHeader requestHeader = new RequestHeader();
        requestHeader.setCount(1);
        requestHeader.setMessageTitle("Welcome");
        SimpleServerTransContext request = SimpleServerTransContext.createRequestSST(0, requestHeader);
        SimpleServerTransContext response = client.invokeSync("localhost:9876", request, 1000 * 3);
    }

}
