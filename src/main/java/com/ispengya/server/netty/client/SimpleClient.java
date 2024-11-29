package com.ispengya.server.netty.client;

import com.ispengya.server.*;
import com.ispengya.server.common.exception.SimpServerInvokeException;
import com.ispengya.server.common.exception.SimpleServerConnectException;
import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.common.exception.SimpleServerTimeOutException;
import com.ispengya.server.common.util.ChannelWrapper;
import com.ispengya.server.common.util.Pair;
import com.ispengya.server.common.util.SimpleServerUtil;
import com.ispengya.server.netty.Event;
import com.ispengya.server.netty.EventExecutor;
import com.ispengya.server.netty.ResponseFuture;
import com.ispengya.server.netty.SimpleAbstract;
import com.ispengya.server.procotol.SimpleServerDecoder;
import com.ispengya.server.procotol.SimpleServerEncoder;
import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
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
public class SimpleClient extends SimpleAbstract implements SimpleClientService {

    private static final Logger log = LoggerFactory.getLogger(SimpleClient.class);

    private final ClientConfig clientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private final ConcurrentMap<String /* addr */, ChannelWrapper> channelTables = new ConcurrentHashMap<String, ChannelWrapper>();

    private static final long LOCK_TIMEOUT_MILLIS = 3000;
    private final Lock lockChannelTables = new ReentrantLock();

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


    }

    @Override
    public void start() throws SimpleServerException {
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

        this.timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                try {
                    scanResponseTable();
                } catch (Throwable e) {
                    log.error("scanResponseTable exception", e);
                }
            }
        }, 1000 * 3, 1000);
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

    @Override
    public void stop() throws SimpleServerException {
        try {
            this.timer.cancel();

            for (ChannelWrapper cw : this.channelTables.values()) {
                this.closeChannel(cw.getChannel());
            }

            this.channelTables.clear();

            this.eventLoopGroupWorker.shutdownGracefully();

            if (this.eventExecutor != null) {
                this.eventExecutor.shutdown();
            }

            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            log.error("SimpleClient shutdown exception, ", e);
        }

        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {
                log.error("SimpleClient shutdown exception, ", e);
            }
        }
    }

    @Override
    public SimpleServerTransContext invokeSync(String addr, SimpleServerTransContext request, long timeoutMillis) throws Exception {
        long beginStartTime = System.currentTimeMillis();
        //connect server
        final Channel channel = this.getAndCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                long costTime = System.currentTimeMillis() - beginStartTime;
                if (timeoutMillis < costTime) {
                    throw new SimpleServerTimeOutException("invokeSync call timeout", timeoutMillis);
                }
                SimpleServerTransContext response = this.invokeSyncImpl(channel, request, timeoutMillis - costTime);
                return response;
            } catch (SimpServerInvokeException e) {
                log.warn("invokeSync: invoke exception, so close the channel[{}]", addr);
                this.closeChannel(addr, channel);
                throw e;
            } catch (SimpleServerTimeOutException e){
                if (clientConfig.isClientCloseSocketIfTimeout()) {
                    this.closeChannel(addr, channel);
                    log.warn("invokeSync: close socket because of timeout, {}ms, {}", timeoutMillis, addr);
                }
                log.warn("invokeSync: wait response timeout exception, the channel[{}]", addr);
                throw e;
            }
        } else {
            this.closeChannel(addr, channel);
            throw new SimpleServerConnectException(addr);
        }
    }

    @Override
    public void invokeAsync(String addr, SimpleServerTransContext request, long timeoutMillis, InvokeCallback invokeCallback) throws Exception {
        long beginStartTime = System.currentTimeMillis();
        final Channel channel = this.getAndCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                long costTime = System.currentTimeMillis() - beginStartTime;
                if (timeoutMillis < costTime) {
                    throw new SimpleServerTimeOutException("invokeAsync call timeout");
                }
                this.invokeAsyncImpl(channel, request, timeoutMillis - costTime, invokeCallback);
            } catch (SimpServerInvokeException e) {
                log.warn("invokeAsync: send request exception, so close the channel[{}]", addr);
                this.closeChannel(addr, channel);
                throw e;
            }catch (SimpleServerTimeOutException e){
                if (clientConfig.isClientCloseSocketIfTimeout()) {
                    this.closeChannel(addr, channel);
                    log.warn("invokeSync: close socket because of timeout, {}ms, {}", timeoutMillis, addr);
                }
                log.warn("invokeSync: wait response timeout exception, the channel[{}]", addr);
                throw e;
            }
        } else {
            this.closeChannel(addr, channel);
            throw new SimpleServerConnectException(addr);
        }
    }

    @Override
    public void invokeOneway(String addr, SimpleServerTransContext request, long timeoutMillis) throws Exception {
        final Channel channel = this.getAndCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                this.invokeOnewayImpl(channel, request, timeoutMillis);
            } catch (SimpleServerException e) {
                log.warn("invokeOneway: send request exception, so close the channel[{}]", addr);
                this.closeChannel(addr, channel);
                throw e;
            }
        } else {
            this.closeChannel(addr, channel);
            throw new SimpleServerException(addr);
        }
    }

    @Override
    public Channel customGetAndCreateChannel() throws Exception {
        return this.createChannel("127.0.0.1:8888");
    }


    private Channel getAndCreateChannel(final String addr) throws Exception {
        if (null == addr) {
            return customGetAndCreateChannel();
        }

        ChannelWrapper cw = this.channelTables.get(addr);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }

        return this.createChannel(addr);
    }

    private Channel createChannel(final String addr) throws InterruptedException {
        ChannelWrapper cw = this.channelTables.get(addr);
        if (cw != null && cw.isOK()) {
            cw.getChannel().close();
            channelTables.remove(addr);
        }

        if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            try {
                boolean createNewConnection;
                cw = this.channelTables.get(addr);
                if (cw != null) {

                    if (cw.isOK()) {
                        cw.getChannel().close();
                        this.channelTables.remove(addr);
                        createNewConnection = true;
                    } else if (!cw.getChannelFuture().isDone()) {
                        createNewConnection = false;
                    } else {
                        this.channelTables.remove(addr);
                        createNewConnection = true;
                    }
                } else {
                    createNewConnection = true;
                }

                if (createNewConnection) {
                    ChannelFuture channelFuture = this.bootstrap.connect(SimpleServerUtil.string2SocketAddress(addr));
                    log.info("createChannel: begin to connect remote host[{}] asynchronously", addr);
                    cw = new ChannelWrapper(channelFuture);
                    this.channelTables.put(addr, cw);
                }
            } catch (Exception e) {
                log.error("createChannel: create channel exception", e);
            } finally {
                this.lockChannelTables.unlock();
            }
        } else {
            log.warn("createChannel: try to lock channel table, but timeout, {}ms", LOCK_TIMEOUT_MILLIS);
        }

        if (cw != null) {
            ChannelFuture channelFuture = cw.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(this.clientConfig.getConnectTimeoutMillis())) {
                if (cw.isOK()) {
                    log.info("createChannel: connect remote host[{}] success, {}", addr, channelFuture.toString());
                    return cw.getChannel();
                } else {
                    log.warn("createChannel: connect remote host[" + addr + "] failed, " + channelFuture.toString(), channelFuture.cause());
                }
            } else {
                log.warn("createChannel: connect remote host[{}] timeout {}ms, {}", addr, this.clientConfig.getConnectTimeoutMillis(),
                        channelFuture.toString());
            }
        }

        return null;
    }

    public void closeChannel(final String addr, final Channel channel) {
        if (null == channel)
            return;

        final String addrRemote = null == addr ? SimpleServerUtil.parseChannelRemoteAddr(channel) : addr;

        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    final ChannelWrapper prevCW = this.channelTables.get(addrRemote);

                    log.info("closeChannel: begin close the channel[{}] Found: {}", addrRemote, prevCW != null);

                    if (null == prevCW) {
                        log.info("closeChannel: the channel[{}] has been removed from the channel table before", addrRemote);
                        removeItemFromTable = false;
                    } else if (prevCW.getChannel() != channel) {
                        log.info("closeChannel: the channel[{}] has been closed before, and has been created again, nothing to do.",
                                addrRemote);
                        removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelTables.remove(addrRemote);
                        log.info("closeChannel: the channel[{}] was removed from channel table", addrRemote);
                    }

                    SimpleServerUtil.closeChannel(channel);
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
