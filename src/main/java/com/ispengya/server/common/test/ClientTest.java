package com.ispengya.server.common.test;


import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.netty.client.ClientConfig;
import com.ispengya.server.netty.client.SimpleClient;
import com.ispengya.server.procotol.SimpleServerTransContext;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        ConcurrentHashMap<Integer, SimpleServerTransContext> hashMap = new ConcurrentHashMap<Integer, SimpleServerTransContext>();
        for (int i = 0; i < 200; i++) {
            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        SimpleServerTransContext request = SimpleServerTransContext.createRequestSST(0, requestHeader);
                        SimpleServerTransContext simpleServerTransContext = client.invokeSync("localhost:6666", request, 1000 * 30);
                        System.out.println("---------------"+simpleServerTransContext+"----------------");
                        hashMap.put(simpleServerTransContext.getRequestId(), simpleServerTransContext);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        Thread.sleep(5000);
        System.out.println("=============");
        System.out.println(hashMap.size());
        hashMap.entrySet().stream().sorted(new Comparator<Map.Entry<Integer, SimpleServerTransContext>>() {
            @Override
            public int compare(Map.Entry<Integer, SimpleServerTransContext> o1, Map.Entry<Integer, SimpleServerTransContext> o2) {
                return o1.getKey()-o2.getKey();
            }
        }).forEach((m)-> System.out.print(m.getKey()+" "));
//        client.stop();
    }

}
