package com.ispengya.server.netty.server;

import com.ispengya.server.ChannelEventListener;
import com.ispengya.server.SimpleServerProcessor;
import com.ispengya.server.SimpleServerService;
import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.common.util.Pair;
import com.ispengya.server.netty.Event;
import com.ispengya.server.netty.EventExecutor;
import com.ispengya.server.netty.SimpleServerAbstract;
import com.ispengya.server.procotol.SimpleServerDecoder;
import com.ispengya.server.procotol.SimpleServerEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 21:26
 **/
public class SimpleServer extends SimpleServerAbstract {

    private static final Logger log = LoggerFactory.getLogger(SimpleServer.class);

    //netty server thread config
    private final ServerBootstrap serverBootstrap;
    private final EventLoopGroup eventLoopGroupBoss;
    private final EventLoopGroup eventLoopGroupSelector;
    private int port;

    private final ServerConfig serverConfig;
    private final ChannelEventListener channelEventListener;

    private DefaultEventExecutorGroup defaultEventExecutorGroup;
    private final ExecutorService publicExecutor;
    protected final EventExecutor eventExecutor;

    public SimpleServer(ServerConfig serverConfig) {
        this(serverConfig, null);
    }

    public SimpleServer(ServerConfig serverConfig, ChannelEventListener channelEventListener) {
        super(new Semaphore(serverConfig.getServerOnewaySemaphoreValue()), new Semaphore(serverConfig.getServerAsyncSemaphoreValue()));
        this.serverBootstrap = new ServerBootstrap();
        this.serverConfig = serverConfig;
        this.channelEventListener = channelEventListener;
        this.eventExecutor = new EventExecutor(channelEventListener);

        int publicThreadNums = serverConfig.getServerPublicExecutorThreads();
        if (publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyServerPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyNIOBoss_%d", this.threadIndex.incrementAndGet()));
            }
        });

        this.eventLoopGroupSelector = new NioEventLoopGroup(serverConfig.getServerSelectorThreads(), new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyServerNIOSelector_%d", this.threadIndex.incrementAndGet()));
            }
        });

        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(serverConfig.getServerWorkerThreads(), new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyServerWorkerThread_%d" + this.threadIndex.incrementAndGet()));
            }
        });
    }


    @Override
    public void startServer() throws SimpleServerException {
        SimpleServerEncoder encoder = new SimpleServerEncoder();
        SimpleServerDecoder decoder = new SimpleServerDecoder();
        IdleStateHandler idleStateHandler = new IdleStateHandler(0, 0, serverConfig.getServerChannelMaxIdleTimeSeconds());
        SimpleServerConnectManageHandler connectManageHandler = new SimpleServerConnectManageHandler(this);
        SimpleServerHandler simpleServerHandler = new SimpleServerHandler(this);

        ServerBootstrap server =
                this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.SO_KEEPALIVE, false)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_SNDBUF, serverConfig.getServerSocketSndBufSize())
                        .childOption(ChannelOption.SO_RCVBUF, serverConfig.getServerSocketRcvBufSize())
                        .localAddress(new InetSocketAddress(this.serverConfig.getListenPort()))
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline()
                                        .addLast(defaultEventExecutorGroup,
                                                encoder,
                                                decoder,
                                                idleStateHandler,
                                                connectManageHandler,
                                                simpleServerHandler
                                        );
                            }
                        });

        if (serverConfig.isServerPooledByteBufAllocatorEnable()) {
            serverBootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        }

        try {
            ChannelFuture sync = this.serverBootstrap.bind().sync();
            InetSocketAddress addr = (InetSocketAddress) sync.channel().localAddress();
            this.port = addr.getPort();
        } catch (InterruptedException e) {
            throw new SimpleServerException("this.serverBootstrap.bind().sync() InterruptedException", e);
        }
        // start event thread
        if (this.channelEventListener != null) {
            this.eventExecutor.start();
        }

        log.info("!!!!!!!!!!!!!!!!SimpleServer started success on port {}!!!!!!!!!!!!!!!!", this.port);
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
    public void putEvent(Event event) {
        this.eventExecutor.putNettyEvent(event);
    }

    public ChannelEventListener getChannelEventListener() {
        return channelEventListener;
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return publicExecutor;
    }
}
