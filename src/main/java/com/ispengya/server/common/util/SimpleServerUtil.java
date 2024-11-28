package com.ispengya.server.common.util;

import io.netty.channel.Channel;

import java.net.SocketAddress;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 21:24
 **/
public class SimpleServerUtil {

    public static String parseChannelRemoteAddr(final Channel channel) {
        if (null == channel) {
            return "";
        }
        SocketAddress remote = channel.remoteAddress();
        final String addr = remote != null ? remote.toString() : "";

        if (addr.length() > 0) {
            int index = addr.lastIndexOf("/");
            if (index >= 0) {
                return addr.substring(index + 1);
            }

            return addr;
        }

        return "";
    }
}
