package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EventLoopImpl implements EventLoop {

    static final Logger log = LoggerFactory.getLogger(EventLoopImpl.class);

    /**
     * Timeout for select operations; default is 1 sec.
     */
    long selectTimeout = TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

    Selector selector;

    volatile boolean opened;
    volatile boolean stop;
    volatile boolean errored;

    // TODO we should allow more than one server channel and corresponding request handler
    RequestHandler requestHandler;
    ServerSocketChannel serverSocketChannel;
    InetSocketAddress serverSocketAddress;

    // Should be inputs
    ExecutorService executor = Executors.newFixedThreadPool(5);
    ExecutorService clientExecutor = Executors.newFixedThreadPool(5);

    AtomicInteger connId = new AtomicInteger(0);

    // TODO we need to add ability to timeout requests
    /**
     * When a client connection send a request it can ask for a callback to be invoked
     * on completion of the request by the server.
     */
    ConcurrentHashMap<CorrelationId, ResponseHandler> pendingRequests = new ConcurrentHashMap<>();

    public EventLoopImpl() {
        try {
            selector = Selector.open();
        }
        catch (Exception e) {
            errored = true;
            throw new NetException("", e);
        }
        opened = true;
    }

    @Override
    public Connection clientConnection(String address, int port, Duration timeout) {
        SocketChannel channel = NIOUtil.getSocketChannel(address, port);
        ConnectionImpl connection = new ConnectionImpl(connId.incrementAndGet(), this, channel);
        try {
            SelectionKey key = connection.socketChannel.register(this.selector, SelectionKey.OP_CONNECT);
            key.attach(connection);
        }
        catch (Exception e) {
            throw new NetException("", e);
        }
        return connection;
    }

    @Override
    public void startServerChannel(String hostname, int port, RequestHandler requestHandler) {
        if (!opened || errored || stop)
            throw new NetException("Cannot start");
        if (serverSocketChannel != null || this.requestHandler != null)
            throw new IllegalArgumentException("ServerSocketChannel already created");
        this.requestHandler = requestHandler;
        this.serverSocketAddress = new InetSocketAddress(hostname, port);
        try {
            //selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(serverSocketAddress);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        }
        catch (Exception e) {
            log.error("Error starting server channel", e);
            errored = true;
            stop = true;
            throw new NetException("Failed to start server channel", e);
        }
        opened = true;
    }

    public void select() {
        if (errored) {
            throw new NetException("The EventLoop is in an error state");
        }
        if (!opened || stop) {
            return;
        }
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid()) {
                if (key.attachment() != null)
                    key.attach(null);
                continue;
            }
            ProtocolHandler handler = (ProtocolHandler) key.attachment();
            if (handler == null) {
                /*
                 * Must be the serverSocketChannel which doesn't have an
                 * attached handler.
                 */
                continue;
            }
            if (!handler.isOkay()) {
                /*
                 * Handler has errored or the client has closed connection.
                 */
                key.cancel();
                NIOUtil.close(key.channel());
                key.attach(null);
                continue;
            }
            if (handler.isWritable()) {
                key.interestOps(SelectionKey.OP_WRITE);
            } else if (handler.socketChannel.isConnectionPending()) {
                key.interestOps(SelectionKey.OP_CONNECT);
            }
            else {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
        try {
            int n = selector.select(selectTimeout);
            if (n == 0) {
                return;
            }
        } catch (IOException e) {
            errored = true;
            log.error("Error when selecting events", e);
            throw new NetException("", e);
        }
        Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            if (!key.isValid()) {
                continue;
            }
            if (key.isConnectable()) {
                handleConnect(key);
            } else if (key.isAcceptable()) {
                handleAccept(key);
            } else if (key.isReadable()) {
                handleRead(key);
            } else if (key.isWritable()) {
                handleWrite(key);
            }
        }
    }

    private void handleConnect(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            boolean isConnected = channel.finishConnect();
            if (isConnected) {
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            ConnectionImpl connection = (ConnectionImpl) key.attachment();
            connection.setErrored();
            log.error("Error occurred when completing connection " + connection, e);
        }
    }

    private void handleWrite(SelectionKey key) {
        ProtocolHandler protocolHandler = (ProtocolHandler) key.attachment();
        protocolHandler.doWrite(key);
    }

    private void handleRead(SelectionKey key) {
        ProtocolHandler protocolHandler = (ProtocolHandler) key.attachment();
        protocolHandler.doRead(key);
    }

    private void handleAccept(SelectionKey key) {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
                .channel();
        SocketChannel socketChannel = null;
        SelectionKey channelKey = null;
        try {
            socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            channelKey = socketChannel.register(this.selector,
                    SelectionKey.OP_READ);
            ConnectionImpl connection = new ConnectionImpl(connId.incrementAndGet(), this, socketChannel);
            channelKey.attach(connection);
            log.info("Accepted connection " + connection);
        } catch (IOException e) {
            log.error("Error when accepting connection", e);
            /*
             * If we failed to accept a new channel, we can still continue serving
             * existing channels, so do not treat this as a fatal error
             */
            if (channelKey != null) {
                channelKey.cancel();
            }
            NIOUtil.close(socketChannel);
        }
    }

    void queueRequest(ProtocolHandler protocolHandler,
                      MessageHeader requestHeader, ByteBuffer request) {
        // Is this a request to be handled by server
        // or is a response received a client connection?
        // We can tell by connection id

        CorrelationId correlationId = requestHeader.getCorrelationId();
        boolean isRequest = requestHeader.isRequest;
        if (isRequest) {
            // Server side
            RequestDispatcher requestDispatcher = new RequestDispatcher(this,
                    protocolHandler, requestHandler, requestHeader, request);
            log.info("Scheduling server write of " + requestHeader.getDataSize() + " for " + correlationId);
            executor.execute(requestDispatcher);
        }
        else {
            // Do we have a response handler?
            ResponseHandler handler = pendingRequests.remove(correlationId);
            if (handler == null) {
                // No handler so nothing to do
                log.warn("No handler found for " + correlationId);
                return;
            }
            log.info("Scheduling client response of " + requestHeader.getDataSize()  + " for " + correlationId);
            ResponseDispatcher responseDispatcher = new ResponseDispatcher(this, handler, requestHeader, request);
            clientExecutor.execute(responseDispatcher);
        }
    }

    void queueResponseHandler(Message request, ResponseHandler responseHandler) {
        CorrelationId correlationId = request.getCorrelationId();
        pendingRequests.put(correlationId, responseHandler);
    }

    @Override
    public void close() {
        NIOUtil.close(selector);
        opened = false;
        executor.shutdown();
        clientExecutor.shutdown();
    }

    /**
     * RequestDispatcher task is responsible for handling a server side request. Actual
     * request handling is delegated to a RequestHandler instance.
     *
     * @author dibyendumajumdar
     * @see EventLoop#startServerChannel(String, int, RequestHandler)
     */
    static final class RequestDispatcher implements Runnable {

        final EventLoopImpl eventLoop;
        final ProtocolHandler protocolHandler;
        final MessageHeader requestHeader;
        final ByteBuffer requestData;
        final RequestHandler requestHandler;

        static final ByteBuffer defaultData = ByteBuffer.allocate(0);

        RequestDispatcher(EventLoopImpl eventLoop,
                          ProtocolHandler protocolHandler, RequestHandler requestHandler,
                          MessageHeader requestHeader, ByteBuffer requestData) {
            this.eventLoop = eventLoop;
            this.protocolHandler = protocolHandler;
            this.requestHandler = requestHandler;
            this.requestHeader = requestHeader;
            this.requestData = requestData;
        }

        public void run() {
            requestData.rewind();
            Message request = new MessageImpl(requestHeader, requestData);
            // setup default response
            MessageHeader messageHeader = new MessageHeader(false);
            messageHeader.setCorrelationId(requestHeader.getCorrelationId());
            messageHeader.setHasException(false);
            MessageImpl response = new MessageImpl(messageHeader, defaultData);
            try {
                requestHandler.handleRequest(request, response);
            } catch (Exception e) {
                response.setData(ByteBuffer.wrap(e.getMessage().getBytes()));
                messageHeader.setDataSize(response.getData().limit());
                messageHeader.setHasException(true);
            }
            protocolHandler.queueWrite(new WriteRequest(messageHeader,
                    response.getData()));
        }
    }

    /**
     * Executes the response handle in the client thread pool; this
     * is the handler that the caller of Connection.submit() set.
     * @see Connection#submit(ByteBuffer, ResponseHandler, Duration) 
     */
    static final class ResponseDispatcher implements Runnable {

        final EventLoopImpl eventLoop;
        final MessageHeader responseHeader;
        final ByteBuffer responseData;
        final ResponseHandler responseHandler;

        ResponseDispatcher(EventLoopImpl eventLoop,
                           ResponseHandler handler,
                           MessageHeader responseHeader, ByteBuffer responseData) {
            this.eventLoop = eventLoop;
            this.responseHandler = handler;
            this.responseHeader = responseHeader;
            this.responseData = responseData;
        }

        public void run() {
            Message response = new MessageImpl(responseHeader, responseData);
            try {
                responseHandler.onResponse(response);
            } catch (Exception e) {
                EventLoopImpl.log.error("Error in ResponseHandler while processing " + response.getCorrelationId(), e);
            }
        }
    }
}
