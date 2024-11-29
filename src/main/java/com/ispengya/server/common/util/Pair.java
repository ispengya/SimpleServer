package com.ispengya.server.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 11:13
 **/
public class Pair <SimpleServerProcessor, ExecutorService>{

    private SimpleServerProcessor processor;
    private ExecutorService executorService;

    public Pair(SimpleServerProcessor processor, ExecutorService executorService) {
        this.processor = processor;
        this.executorService = executorService;
    }

    public SimpleServerProcessor getProcessor() {
        return processor;
    }

    public void setProcessor(SimpleServerProcessor processor) {
        this.processor = processor;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

}
