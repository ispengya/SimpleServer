package com.ispengya.server.netty.client;

import com.ispengya.server.ChannelEventListener;
import com.ispengya.server.SimpleServerProcessor;
import com.ispengya.server.SimpleServerService;
import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.common.util.ChannelWrapper;
import com.ispengya.server.common.util.Pair;
import com.ispengya.server.common.util.SimpleServerUtil;
import com.ispengya.server.netty.Event;
import com.ispengya.server.netty.EventExecutor;
import com.ispengya.server.netty.SimpleServerAbstract;
import com.ispengya.server.netty.server.SimpleServerConnectManageHandler;
import com.ispengya.server.netty.server.SimpleServerHandler;
import com.ispengya.server.procotol.SimpleServerDecoder;
import com.ispengya.server.procotol.SimpleServerEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-29 14:50
 **/
public class SimpleClient extends SimpleServerAbstract implements SimpleServerService {

    private static final Logger log = LoggerFactory.getLogger(SimpleClient.class);

    private final ClientConfig clientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private final ConcurrentMap<String /* addr */, ChannelWrapper> channelTables = new ConcurrentHashMap<String, ChannelWrapper>();

    private static final long LOCK_TIMEOUT_MILLIS = 3000;
    private final Lock lockChannelTables = new ReentrantLock();
    private final AtomicReference<List<String>> namesrvAddrList = new AtomicReference<List<String>>();
    private final AtomicReference<String> namesrvAddrChoosed = new AtomicReference<String>();
    private final AtomicInteger namesrvIndex = new AtomicInteger(0);
    private final Lock lockNamesrvChannel = new ReentrantLock();

    private final ExecutorService publicExecutor;

    /**
     * Invoke the callback methods in this executor when process response.
     */
    private final Timer timer = new Timer("ClientHouseKeepingService", true);
    private final ChannelEventListener channelEventListener;
    private DefaultEventExecutorGroup defaultEventExecutorGroup;
    protected final EventExecutor eventExecutor;


    public SimpleClient(final ClientConfig clientConfig) {
        this(clientConfig, null);
    }

    public SimpleClient(final ClientConfig clientConfig,
                        final ChannelEventListener channelEventListener) {
        super(new Semaphore(clientConfig.getClientOnewaySemaphoreValue()), new Semaphore(clientConfig.getClientAsyncSemaphoreValue()));
        this.clientConfig = clientConfig;
        this.channelEventListener = channelEventListener;
        this.eventExecutor = new EventExecutor(channelEventListener);

        int publicThreadNums = clientConfig.getClientCallbackExecutorThreads();
        if (publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyClientSelector_%d", this.threadIndex.incrementAndGet()));
            }
        });

        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(clientConfig.getClientWorkerThreads(), new ThreadFactory() {

            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientWorkerThread_" + this.threadIndex.incrementAndGet());
            }
        });
    }

    @Override
    public void startServer() throws SimpleServerException {
        SimpleServerEncoder encoder = new SimpleServerEncoder();
        SimpleServerDecoder decoder = new SimpleServerDecoder();
        IdleStateHandler idleStateHandler = new IdleStateHandler(0, 0, clientConfig.getClientChannelMaxIdleTimeSeconds());
        SimpleClientConnectManageHandler connectManageHandler = new SimpleClientConnectManageHandler(this);
        SimpleClientHandler clientHandler = new SimpleClientHandler(this);

        Bootstrap handler = this.bootstrap.group(this.eventLoopGroupWorker).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.SO_SNDBUF, clientConfig.getClientSocketSndBufSize())
                .option(ChannelOption.SO_RCVBUF, clientConfig.getClientSocketRcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(
                                defaultEventExecutorGroup,
                                encoder,
                                decoder,
                                idleStateHandler,
                                connectManageHandler,
                                clientHandler);
                    }
                });

        if (this.channelEventListener != null) {
            this.eventExecutor.start();
        }
    }

    @Override
    public void registerProcessor(int requestCode, SimpleServerProcessor processor, ExecutorService executor) {
        ExecutorService executorThis = executor;
        if (null == executor) {
            executorThis = this.publicExecutor;
        }

        Pair<SimpleServerProcessor, ExecutorService> pair = new Pair<SimpleServerProcessor, ExecutorService>(processor, executorThis);
        this.processorTable.put(requestCode, pair);
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return this.publicExecutor;
    }

    @Override
    public void putEvent(Event event) {
        this.eventExecutor.putNettyEvent(event);
    }

    @Override
    public ChannelEventListener getChannelEventListener() {
        return this.channelEventListener;
    }

    public void closeChannel(final Channel channel) {
        if (null == channel)
            return;

        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    ChannelWrapper prevCW = null;
                    String addrRemote = null;
                    for (Map.Entry<String, ChannelWrapper> entry : channelTables.entrySet()) {
                        String key = entry.getKey();
                        ChannelWrapper prev = entry.getValue();
                        if (prev.getChannel() != null) {
                            if (prev.getChannel() == channel) {
                                prevCW = prev;
                                addrRemote = key;
                                break;
                            }
                        }
                    }

                    if (null == prevCW) {
                        log.info("eventCloseChannel: the channel[{}] has been removed from the channel table before", addrRemote);
                        removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelTables.remove(addrRemote);
                        log.info("closeChannel: the channel[{}] was removed from channel table", addrRemote);
                        SimpleServerUtil.closeChannel(channel);
                    }
                } catch (Exception e) {
                    log.error("closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
                log.warn("closeChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            log.error("closeChannel exception", e);
        }
    }


}
