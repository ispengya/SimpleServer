package com.ispengya.server.netty;

import com.ispengya.server.InvokeCallback;
import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.Channel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResponseFuture {
    private final int requestId;
    private final Channel processChannel;
    private final long beginTimestamp = System.currentTimeMillis();
    private final long timeoutMillis;
    private final InvokeCallback invokeCallback;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private final AtomicBoolean released = new AtomicBoolean(false);
    private final Semaphore semaphore;

    private final AtomicBoolean executeCallbackOnlyOnce = new AtomicBoolean(false);
    private volatile SimpleServerTransContext simpleServerTransContext;
    private volatile boolean sendRequestOK = true;
    private volatile Throwable cause;

    public ResponseFuture(Channel channel, int requestId, long timeoutMillis, InvokeCallback invokeCallback,
        Semaphore semaphore) {
        this.requestId = requestId;
        this.processChannel = channel;
        this.timeoutMillis = timeoutMillis;
        this.invokeCallback = invokeCallback;
        this.semaphore = semaphore;
    }

    public void executeInvokeCallback() {
        if (invokeCallback != null) {
            if (this.executeCallbackOnlyOnce.compareAndSet(false, true)) {
                invokeCallback.applyCallBack(this);
            }
        }
    }

    public void release() {
        if (this.semaphore != null) {
            if (this.released.compareAndSet(false, true)) {
                this.semaphore.release();
            }
        }
    }

    public boolean isTimeout() {
        long diff = System.currentTimeMillis() - this.beginTimestamp;
        return diff > this.timeoutMillis;
    }

    public SimpleServerTransContext waitResponse(final long timeoutMillis) throws InterruptedException {
        this.countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        return this.simpleServerTransContext;
    }

    public void putResponse(final SimpleServerTransContext simpleServerTransContext) {
        this.simpleServerTransContext = simpleServerTransContext;
        this.countDownLatch.countDown();
    }

    public SimpleServerTransContext getSimpleServerTransContext() {
        return simpleServerTransContext;
    }

    public void setSimpleServerTransContext(SimpleServerTransContext simpleServerTransContext) {
        this.simpleServerTransContext = simpleServerTransContext;
    }

    public long getBeginTimestamp() {
        return beginTimestamp;
    }

    public boolean isSendRequestOK() {
        return sendRequestOK;
    }

    public void setSendRequestOK(boolean sendRequestOK) {
        this.sendRequestOK = sendRequestOK;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public InvokeCallback getInvokeCallback() {
        return invokeCallback;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public Channel getProcessChannel() {
        return processChannel;
    }

    public int getRequestId() {
        return requestId;
    }

}
