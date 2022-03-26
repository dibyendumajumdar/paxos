package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EventLoopImpl implements EventLoop {

    /**
     * Timeout for select operations; default is 10 secs.
     */
    long selectTimeout = TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);

    Selector selector;

    volatile boolean opened;
    volatile boolean stop;
    volatile boolean errored;

    RequestHandler requestHandler;
    ServerSocketChannel serverSocketChannel;
    InetSocketAddress serverSocketAddress;

    Executor executor = Executors.newFixedThreadPool(5);
    Executor clientExecutor = Executors.newFixedThreadPool(5);

    AtomicInteger connId = new AtomicInteger(0);

    ConcurrentHashMap<CorrelationId, ResponseHandler> pendingRequests = new ConcurrentHashMap<>();

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
    public void start(String hostname, int port, RequestHandler requestHandler) {
        if (opened)
            throw new NetException("Already open");
        this.requestHandler = requestHandler;
        this.serverSocketAddress = new InetSocketAddress(hostname, port);
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(serverSocketAddress);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        }
        catch (Exception e) {
            throw new NetException("", e);
        }
        opened = true;
    }

    public void select() {
//        if (errored) {
//            throw new NetworkException(new MessageInstance(m_erroredException));
//        }
        if (!opened || stop) {
            return;
        }
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid()) {
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
                continue;
            }
            if (handler.isWritable()) {
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
            throw new NetException("");
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
            e.printStackTrace();
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
        } catch (IOException e) {
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

    @Override
    public void eventLoop() {

    }

    @Override
    public void requestStop() {

    }

    @Override
    public void shutdown() {

    }

    void queueRequest(ProtocolHandler protocolHandler,
                      MessageHeader requestHeader, ByteBuffer request) {
        // Is this a request to be handled by server
        // or is a response received a client connection?
        // We can tell by connection id

        CorrelationId correlationId = requestHeader.getCorrelationId();
        if (correlationId.connectionId == 0) {
            // Server side
            RequestDispatcher requestDispatcher = new RequestDispatcher(this,
                    protocolHandler, requestHandler, requestHeader, request);
            executor.execute(requestDispatcher);
        }
        else {
            // Do we have a response handler?
            ResponseHandler handler = pendingRequests.remove(correlationId);
            if (handler == null) {
                // No handler so nothing to do
                return;
            }
            ResponseDispatcher responseDispatcher = new ResponseDispatcher(this, handler, requestHeader, request);
            clientExecutor.execute(responseDispatcher);
        }
    }

    void queueResponseHandler(Message request, ResponseHandler responseHandler) {
        CorrelationId correlationId = request.getCorrelationId();
        pendingRequests.put(correlationId, responseHandler);
    }

    /**
     * RequestDispatcher task is responsible for handling a request. Actual
     * request handling is delegated to a RequestHandler instance.
     *
     * @author dibyendumajumdar
     */
    static final class RequestDispatcher implements Runnable {

        final EventLoopImpl server;
        final ProtocolHandler protocolHandler;
        final MessageHeader requestHeader;
        final ByteBuffer requestData;
        final RequestHandler requestHandler;

        static final ByteBuffer defaultData = ByteBuffer.allocate(0);

        RequestDispatcher(EventLoopImpl server,
                          ProtocolHandler protocolHandler, RequestHandler requestHandler,
                          MessageHeader requestHeader, ByteBuffer requestData) {
            this.server = server;
            this.protocolHandler = protocolHandler;
            this.requestHandler = requestHandler;
            this.requestHeader = requestHeader;
            this.requestData = requestData;
        }

        public void run() {
            ResponseHandler responseHandler = null;
            requestData.rewind();
            Message request = new MessageImpl(requestHeader, requestData);
            // setup default response
            MessageHeader messageHeader = new MessageHeader();
            messageHeader.setCorrelationId(requestHeader.getCorrelationId());
            messageHeader.setHasException(false);
            Message response = new MessageImpl(messageHeader, defaultData);
            try {
                requestHandler.handleRequest(request, response);
            } catch (Exception e) {
                response.setData(ByteBuffer.wrap(e.getMessage().getBytes()));
                messageHeader.setDataSize(response.getData().limit());
            }
            protocolHandler.queueWrite(new WriteRequest(messageHeader,
                    response.getData()));
        }
    }

    static final class ResponseDispatcher implements Runnable {

        final EventLoopImpl server;
        final MessageHeader responseHeader;
        final ByteBuffer responseData;
        final ResponseHandler responseHandler;

        ResponseDispatcher(EventLoopImpl server,
                           ResponseHandler handler,
                           MessageHeader responseHeader, ByteBuffer responseData) {
            this.server = server;
            this.responseHandler = handler;
            this.responseHeader = responseHeader;
            this.responseData = responseData;
        }

        public void run() {
            Message response = new MessageImpl(responseHeader, responseData);
            try {
                responseHandler.onResponse(response);
            } catch (Exception e) {
            }
        }
    }
}
