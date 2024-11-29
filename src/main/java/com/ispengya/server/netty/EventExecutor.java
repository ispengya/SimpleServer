package com.ispengya.server.netty;

import com.ispengya.server.ChannelEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.ispengya.server.common.constant.SimpleServerAllConstants.*;


/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 18:01
 **/
public class EventExecutor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(EventExecutor.class);

    private final LinkedBlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>();
    private final ChannelEventListener channelEventListener;
    private final int maxSize = 10000;

    protected final Thread thread;
    private static final long JOIN_TIME = 90 * 1000;
    protected volatile boolean hasNotified = false;
    protected volatile boolean stopped = false;

    public EventExecutor(ChannelEventListener channelEventListener) {
        this.thread = new Thread(this, "EventExecutor");
        this.channelEventListener = channelEventListener;
    }

    public void putNettyEvent(final Event event) {
        if (this.eventQueue.size() <= maxSize) {
            this.eventQueue.add(event);
        } else {
            log.warn("event queue size[{}] enough, so drop this event {}", this.eventQueue.size(), event.toString());
        }
    }

    public void start() {
        this.thread.start();
    }

    public void shutdown() {
        this.shutdown(false);
    }

    public void shutdown(final boolean interrupt) {
        this.stopped = true;
        //Block multi-threaded calls
        synchronized (this) {
            if (!this.hasNotified) {
                this.hasNotified = true;
                this.notify();
            }
        }

        try {
            if (interrupt) {
                this.thread.interrupt();
            }
            this.thread.join(JOIN_TIME);
        } catch (InterruptedException e) {
        }
    }


    @Override
    public void run() {
        final ChannelEventListener listener = this.channelEventListener;

        while (!this.stopped) {
            try {
                Event event = this.eventQueue.poll(3000, TimeUnit.MILLISECONDS);
                if (event != null && listener != null) {
                    switch (event.getType()) {
                        case IDLE:
                            listener.onChannelIdle(event.getRemoteAddr(), event.getChannel());
                            break;
                        case CLOSE:
                            listener.onChannelClose(event.getRemoteAddr(), event.getChannel());
                            break;
                        case CONNECT:
                            listener.onChannelConnect(event.getRemoteAddr(), event.getChannel());
                            break;
                        case EXCEPTION:
                            listener.onChannelException(event.getRemoteAddr(), event.getChannel());
                            break;
                        default:
                            break;

                    }
                }
            } catch (Exception e) {
                log.error(" EventExecutor service has exception. ", e);
            }
        }
    }
}
