package com.ispengya.server.netty;

import com.ispengya.server.ChannelEventListener;
import com.ispengya.server.SimpleServerProcessor;
import com.ispengya.server.SimpleServerService;
import com.ispengya.server.common.constant.SimpleServerAllConstants;
import com.ispengya.server.common.exception.SimpleServerException;
import com.ispengya.server.common.util.Pair;
import com.ispengya.server.common.util.SimpleServerUtil;
import com.ispengya.server.procotol.SimpleServerTransContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static com.ispengya.server.common.constant.SimpleServerAllConstants.REQUEST_FLAG;
import static com.ispengya.server.common.constant.SimpleServerAllConstants.RESPONSE_FLAG;

/**
 * @description:
 * @author: hanzhipeng
 * @create: 2024-11-28 21:40
 **/
public abstract class SimpleServerAbstract implements SimpleServerService {
    /**
     * log
     */
    private static final Logger log = LoggerFactory.getLogger(SimpleServerAbstract.class);

    /**
     * Semaphore to limit maximum number of on-going one-way requests, which protects system memory footprint.
     */
    protected final Semaphore semaphoreOneway;

    /**
     * Semaphore to limit maximum number of on-going asynchronous requests, which protects system memory footprint.
     */
    protected final Semaphore semaphoreAsync;

    /**
     * This map caches all on-going requests.
     */
    protected final ConcurrentMap<Integer /* requestId */, ResponseFuture> responseTable =
            new ConcurrentHashMap<Integer, ResponseFuture>(256);

    /**
     * This container holds all processors per request code, aka, for each incoming request, we may look up the
     * responding processor in this map to handle the request.
     */
    protected final HashMap<Integer/* processCode */, Pair<SimpleServerProcessor, ExecutorService>> processorTable =
            new HashMap<Integer, Pair<SimpleServerProcessor, ExecutorService>>(64);



    public SimpleServerAbstract(Semaphore semaphoreOneway, Semaphore semaphoreAsync) {
        this.semaphoreOneway = semaphoreOneway;
        this.semaphoreAsync = semaphoreAsync;
    }

    public abstract ExecutorService getCallbackExecutor();

    @Override
    public void processMessage(ChannelHandlerContext ctx, SimpleServerTransContext transContext) throws SimpleServerException {
        final SimpleServerTransContext sst = transContext;
        if (sst != null) {
            switch (sst.getFlag()) {
                case REQUEST_FLAG:
                    processRequest(ctx, sst);
                    break;
                case RESPONSE_FLAG:
                    processResponse(ctx, sst);
                    break;
                default:
                    break;
            }
        }
    }

    private void processRequest(final ChannelHandlerContext ctx, final SimpleServerTransContext sst) {
        final Pair<SimpleServerProcessor, ExecutorService> pair = this.processorTable.get(sst.getProcessCode());
        final int requestId = sst.getRequestId();

        if (pair != null) {
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    try {
                        final SimpleServerTransContext response = pair.getProcessor().processRequest(ctx, sst);

                        if (!sst.isOneWay()) {
                            if (response != null) {
                                response.setRequestId(requestId);
                                response.setFlag(RESPONSE_FLAG);
                                try {
                                    ctx.writeAndFlush(response);
                                } catch (Throwable e) {
                                    log.error("process request over, but response failed", e);
                                    log.error(sst.toString());
                                    log.error(response.toString());
                                }
                            } else {

                            }
                        }
                    } catch (Throwable e) {
                        log.error("process request exception", e);
                        log.error(sst.toString());

                        if (!sst.isOneWay()) {
                            final SimpleServerTransContext response = SimpleServerTransContext.createResponseSST(SimpleServerAllConstants.SYSTEM_ERROR);
                            response.setRequestId(requestId);
                            ctx.writeAndFlush(response);
                        }
                    }
                }
            };

            if (pair.getProcessor().rejectRequest()) {
                final SimpleServerTransContext response = SimpleServerTransContext.createResponseSST(SimpleServerAllConstants.SYSTEM_BUSY);
                response.setRequestId(requestId);
                ctx.writeAndFlush(response);
                return;
            }

            try {
                //start handler
                final RequestTask requestTask = new RequestTask(run, ctx.channel(), sst);
                pair.getExecutorService().submit(requestTask);
            } catch (RejectedExecutionException e) {
                if ((System.currentTimeMillis() % 10000) == 0) {
                    log.warn(SimpleServerUtil.parseChannelRemoteAddr(ctx.channel())
                            + ", too many requests and system thread pool busy, RejectedExecutionException "
                            + pair.getExecutorService().toString()
                            + " request code: " + sst.getProcessCode());
                }

                if (!sst.isOneWay()) {
                    final SimpleServerTransContext response = SimpleServerTransContext.createResponseSST(SimpleServerAllConstants.SYSTEM_BUSY);
                    response.setRequestId(requestId);
                    ctx.writeAndFlush(response);
                }
            }
        } else {
            String error = " request type " + sst.getProcessCode() + " not supported";
            final SimpleServerTransContext response =
                    SimpleServerTransContext.createResponseSST(SimpleServerAllConstants.REQUEST_CODE_NOT_SUPPORTED);
            response.setRequestId(requestId);
            ctx.writeAndFlush(response);
            log.error(SimpleServerUtil.parseChannelRemoteAddr(ctx.channel()) + error);
        }
    }

    private void processResponse(ChannelHandlerContext ctx, SimpleServerTransContext sst) {
        final int requestId = sst.getRequestId();
        final ResponseFuture responseFuture = responseTable.get(requestId);
        if (responseFuture != null) {
            responseFuture.setSimpleServerTransContext(sst);

            responseTable.remove(requestId);

            if (responseFuture.getInvokeCallback() != null) {
                executeInvokeCallback(responseFuture);
            } else {
                responseFuture.putResponse(sst);
                responseFuture.release();
            }
        } else {
            log.warn("receive response, but not matched any request, " + SimpleServerUtil.parseChannelRemoteAddr(ctx.channel()));
            log.warn(sst.toString());
        }
    }

    private void executeInvokeCallback(final ResponseFuture responseFuture) {
        boolean runInThisThread = false;
        ExecutorService executor = this.getCallbackExecutor();
        if (executor != null) {
            try {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            responseFuture.executeInvokeCallback();
                        } catch (Throwable e) {
                            log.warn("execute callback in executor exception, and callback throw", e);
                        } finally {
                            responseFuture.release();
                        }
                    }
                });
            } catch (Exception e) {
                runInThisThread = true;
                log.warn("execute callback in executor exception, maybe executor busy", e);
            }
        } else {
            runInThisThread = true;
        }
        //executor is null
        if (runInThisThread) {
            try {
                responseFuture.executeInvokeCallback();
            } catch (Throwable e) {
                log.warn("executeInvokeCallback Exception", e);
            } finally {
                responseFuture.release();
            }
        }
    }

    private void requestFail(final int requestId) {
        ResponseFuture responseFuture = responseTable.remove(requestId);
        if (responseFuture != null) {
            responseFuture.setSendRequestOK(false);
            responseFuture.putResponse(null);
            try {
                executeInvokeCallback(responseFuture);
            } catch (Throwable e) {
                log.warn("execute callback in requestFail, and callback throw", e);
            } finally {
                responseFuture.release();
            }
        }
    }

    /**
     * mark the request of the specified channel as fail and to invoke fail callback immediately
     * @param channel the channel which is close already
     */
    public void failFast(final Channel channel) {
        Iterator<Map.Entry<Integer, ResponseFuture>> it = responseTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ResponseFuture> entry = it.next();
            if (entry.getValue().getProcessChannel() == channel) {
                Integer requestId = entry.getKey();
                if (requestId != null) {
                    requestFail(requestId);
                }
            }
        }
    }

    /**
     * <p>
     * This method is periodically invoked to scan and expire deprecated request.
     * </p>
     */
    public void scanResponseTable() {
        final List<ResponseFuture> rfList = new LinkedList<ResponseFuture>();
        Iterator<Map.Entry<Integer, ResponseFuture>> it = this.responseTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ResponseFuture> next = it.next();
            ResponseFuture rep = next.getValue();

            if ((rep.getBeginTimestamp() + rep.getTimeoutMillis() + 1000) <= System.currentTimeMillis()) {
                rep.release();
                it.remove();
                rfList.add(rep);
                log.warn("remove timeout request, " + rep);
            }
        }

        for (ResponseFuture rf : rfList) {
            try {
                executeInvokeCallback(rf);
            } catch (Throwable e) {
                log.warn("scanResponseTable, operationComplete Exception", e);
            }
        }
    }
}
