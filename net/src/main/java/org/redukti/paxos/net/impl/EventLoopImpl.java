/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
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
     * When a client connection sends a request it can ask for a callback to be invoked
     * on completion of the request by the server.
     */
    ConcurrentHashMap<CorrelationId, ResponseHandler> pendingRequests = new ConcurrentHashMap<>();

    public EventLoopImpl() {
        try {
            selector = Selector.open();
        }
        catch (Exception e) {
            errored = true;
            throw new NetException("Error opening selector", e);
        }
        opened = true;
    }

    /**
     * Start operation to connect to another server, if listener is supplied it will be invoked
     * when connection is successful or fails.
     *
     * @param address Remote server address
     * @param port remote server port
     * @param connectionListener Listener to invoke on completion
     */
    @Override
    public Connection clientConnection(String address, int port, ConnectionListener connectionListener) {
        int id = connId.incrementAndGet();
        ConnectionImpl connection = null;
        try {
            SocketChannel channel = NIOUtil.getSocketChannel(address, port);
            connection = new ConnectionImpl(id, this, channel, connectionListener);
            SelectionKey key = connection.socketChannel.register(this.selector, SelectionKey.OP_CONNECT);
            key.attach(connection);
        }
        catch (Exception e) {
            informConnectionListener(connectionListener, false);
            if (connection != null)
                connection.setErrored();
            throw new NetException("Failed to create channel for connection to " + address + ":" + port, e);
        }
        return connection;
    }

    private void informConnectionListener(ConnectionListener connectionListener, boolean success) {
        if (connectionListener == null)
            return;
        clientExecutor.execute(new ConnectionListenerRunnable(connectionListener, success));
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
            throw new NetException("The EventLoop is not open or shutting down");
        }
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid()) {
                // TODO do we need to call a listener?
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
                // TODO do we need to call a listener?
                key.attach(null);
                continue;
            }
            if (handler.socketChannel.isConnectionPending()) {
                key.interestOps(SelectionKey.OP_CONNECT);
            } else if (handler.isWritePending()) {
                key.interestOps(SelectionKey.OP_WRITE);
            } else {
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
            throw new NetException("Error when selecting events", e);
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
        ConnectionImpl connection = (ConnectionImpl) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            boolean isConnected = channel.finishConnect();
            if (isConnected) {
                key.interestOps(SelectionKey.OP_READ);
                informConnectionListener(connection.connectionListener, true);
            }
            else {
                informConnectionListener(connection.connectionListener, false);
            }
        } catch (Exception e) {
            connection.setErrored();
            log.error("Error occurred when completing connection " + connection, e.getMessage());
            informConnectionListener(connection.connectionListener, false);
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
            ConnectionImpl connection = new ConnectionImpl(connId.incrementAndGet(), this, socketChannel, null);
            channelKey.attach(connection);
            log.info("Accepted connection " + connection);
        } catch (Exception e) {
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

    static final class ConnectionListenerRunnable implements Runnable {
        final ConnectionListener listener;
        final boolean success;

        public ConnectionListenerRunnable(ConnectionListener listener, boolean success) {
            this.listener = listener;
            this.success = success;
        }


        @Override
        public void run() {
            try {
                if (success)
                    listener.onConnectionSuccess();
                else
                    listener.onConnectionFailed();
            }
            catch (Throwable t) {
                // ignored
            }
        }
    }

    static final class RequestResponseSenderImpl implements RequestResponseSender {
        final ProtocolHandler protocolHandler;
        final MessageHeader messageHeader;
        final MessageImpl response;
        static final ByteBuffer defaultData = ByteBuffer.allocate(0);

        RequestResponseSenderImpl(ProtocolHandler protocolHandler, MessageHeader requestHeader) {
            this.protocolHandler = protocolHandler;
            this.messageHeader = new MessageHeader(false);
            messageHeader.setCorrelationId(requestHeader.getCorrelationId());
            messageHeader.setHasException(false);
            response = new MessageImpl(messageHeader, defaultData);
        }

        @Override
        public void setData(ByteBuffer data) {
            response.setData(data);
        }

        @Override
        public void setErrored(String errorMessage) {
            messageHeader.setHasException(true);
            response.setData(ByteBuffer.wrap(errorMessage.getBytes()));
            messageHeader.setDataSize(response.getData().limit());
        }

        @Override
        public void submit() {
            protocolHandler.queueWrite(new WriteRequest(messageHeader,
                    response.getData()));
        }
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
            RequestResponseSenderImpl responseGenerator = new RequestResponseSenderImpl(protocolHandler, requestHeader);
            try {
                requestHandler.handleRequest(request, responseGenerator);
            } catch (Exception e) {
                log.error("Exception occurred when handling request " + requestHeader.getCorrelationId());
                responseGenerator.setErrored(e.getMessage());
            }
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
