package com.ispengya.server;

import com.ispengya.server.netty.ResponseFuture;

public interface InvokeCallback {
    void applyCallBack(final ResponseFuture responseFuture);
}