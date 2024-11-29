package com.ispengya.server.netty;

import io.netty.channel.Channel;

public class Event {
    private final Integer type;
    private final String remoteAddr;
    private final Channel channel;

    public Event(Integer type, String remoteAddr, Channel channel) {
        this.type = type;
        this.remoteAddr = remoteAddr;
        this.channel = channel;
    }

    public Integer getType() {
        return type;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return "NettyEvent [type=" + type + ", remoteAddr=" + remoteAddr + ", channel=" + channel + "]";
    }
}
