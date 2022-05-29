/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.net.impl;

import org.redukti.paxos.net.api.NetException;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

final class WriteRequest {
    final MessageHeader messageHeader;
    final ByteBuffer data;

    WriteRequest(MessageHeader messageHeader, ByteBuffer data) {
        this.messageHeader = messageHeader;
        this.data = data;
        this.messageHeader.setDataSize(this.data.limit());
    }

    MessageHeader getResponseHeader() {
        return messageHeader;
    }

    ByteBuffer getData() {
        return data;
    }
}

/**
 * A simple protocol handler. The network protocol is extremely simple. Each
 * request must have a response. The request and response packets have a
 * header and a body. The header is of fixed length. The body is variable
 * length but the length is recorded in the header so that the handler can
 * determine when a full request/response packet has been received.
 * <p>
 *
 * @see MessageHeader
 * @author dibyendumajumdar
 *
 */
public abstract class ProtocolHandler {
    final EventLoopImpl eventLoop;
    protected SocketChannel socketChannel;

    static final int STATE_INIT = 0;
    static final int STATE_HEADER = 1;
    static final int STATE_HEADER_COMPLETED = 2;
    static final int STATE_PAYLOAD = 3;
    static final int STATE_PAYLOAD_COMPLETED = 3;

    ByteBuffer readHeader = MessageHeader.allocate();
    MessageHeader requestHeader = new MessageHeader();
    ByteBuffer readPayload = null;
    int readState = STATE_INIT;

    ByteBuffer writeHeader = MessageHeader.allocate();
    ArrayList<WriteRequest> writeQueue = new ArrayList<>();
    WriteRequest current = null;
    int writeState = STATE_INIT;

    boolean okay = true;

    ProtocolHandler(EventLoopImpl networkServer) {
        this.eventLoop = networkServer;
    }

    /**
     * Perform an incremental read, keeping track of progress. When a full
     * request is detected, schedule a request handler event.
     *
     * @param key Identifies the channel which is ready for reading
     */
    synchronized void doRead(SelectionKey key) {

        if (!okay) {
            eventLoop.log.error(getClass(), "doRead", "Channel in error state");
            throw new NetException("Channel has errored");
        }
        try {
            while (true) {
                /* We read as much as we can */
                if (readState == STATE_INIT) {
                    /* Initial state */
                    readHeader.clear();
                    requestHeader = new MessageHeader();
                    int n = socketChannel.read(readHeader);
                    if (n < 0) {
                        eof();
                        break;
                    }
                    if (readHeader.remaining() == 0) {
                        /* We got everything we need */
                        readState = STATE_HEADER_COMPLETED;
                    } else {
                        /* Need to resume reading the header some other time */
                        readState = STATE_HEADER;
                        break;
                    }
                }

                if (readState == STATE_HEADER) {
                    /* Resume reading header */
                    int n = socketChannel.read(readHeader);
                    if (n < 0) {
                        eof();
                        break;
                    }
                    if (readHeader.remaining() == 0) {
                        /* We got everything we need */
                        readState = STATE_HEADER_COMPLETED;
                    } else {
                        /* Need to resume reading the header some other time */
                        break;
                    }
                }

                if (readState == STATE_HEADER_COMPLETED) {
                    /* parse the header */
                    requestHeader.retrieve(readHeader.flip());
                    eventLoop.log.debug(getClass(), "doRead", "Reading payload of " + requestHeader.getDataSize());
                    /* allocate buffer for reading the payload */
                    readPayload = ByteBuffer.allocate(requestHeader
                            .getDataSize());
                    readState = STATE_PAYLOAD;
                }

                if (readState == STATE_PAYLOAD) {
                    /* get the payload */
                    int n = socketChannel.read(readPayload);
                    if (n < 0) {
                        eof();
                        break;
                    }
                    if (readPayload.remaining() == 0) {
                        /* we got the payload */
                        readState = STATE_PAYLOAD_COMPLETED;
                        if (readPayload.limit() != requestHeader.getDataSize()) {
                            eventLoop.log.error(getClass(), "doRead", "Read " + readPayload.limit() + " but expected " + requestHeader.getDataSize());
                            throw new IOException();
                        }
                    } else {
                        /* still more to read, must resume later */
                        break;
                    }
                }

                if (readState == STATE_PAYLOAD_COMPLETED) {
                    /* read completed, queue the request */
                    eventLoop.queueRequest(this, requestHeader,
                            readPayload.flip());
                    /* let's see if we can read another message */
                    readState = STATE_INIT;
                    readPayload = null;
                }
            }
        } catch (SocketException e) {
            eventLoop.log.error(getClass(), "doRead", "Error in read operation", e);
            if (e.getMessage().equals("Connection reset")) {
                connectionReset();
            }
            else {
                failed();
            }
        } catch (IOException e) {
            eventLoop.log.error(getClass(), "doRead", "Error in read operation", e);
            failed();
        }
    }

    void eof() {
        okay = false;
    }

    void failed() {
        okay = false;
    }

    boolean isOkay() {
        return okay;
    }

    void connectionReset() {
        okay = false;
    }

    /**
     * Perform an incremental write. Keep writing as long as the channel is
     * writable and there are more packets to be written.
     *
     * @param key Identifies the channel that is ready for writing
     */
    synchronized void doWrite(SelectionKey key) {
        if (!okay) {
            eventLoop.log.error(getClass(), "doWrite", "Channel in error state");
            throw new NetException("");
        }
        try {
            while (true) {
                /* Keep writing as long as we can */
                if (current == null) {
                    /* Get the next message */
                    if (writeQueue.size() > 0) {
                        current = writeQueue.remove(0);
                    } else {
                        /* No more messages to write */
                        break;
                    }
                }
                if (writeState == STATE_INIT) {
                    writeHeader.clear();
                    current.getResponseHeader().store(writeHeader);
                    writeHeader.flip();
                    socketChannel.write(writeHeader);
                    if (writeHeader.remaining() == 0) {
                        /* done writing the header */
                        writeState = STATE_PAYLOAD;
                    } else {
                        /* need to resume write at a later time */
                        writeState = STATE_HEADER;
                        break;
                    }
                }

                if (writeState == STATE_HEADER) {
                    /* resume writing the header */
                    socketChannel.write(writeHeader);
                    if (writeHeader.remaining() == 0) {
                        /* done writing the header */
                        writeState = STATE_PAYLOAD;
                    } else {
                        /* need to resume write at a leter time */
                        break;
                    }
                }

                if (writeState == STATE_PAYLOAD) {
                    /* write the payload */
                    socketChannel.write(current.getData());
                    if (current.getData().remaining() == 0) {
                        /* done */
                        writeState = STATE_PAYLOAD_COMPLETED;
                    } else {
                        /* need to resume at a later time */
                        break;
                    }
                }

                if (writeState == STATE_PAYLOAD_COMPLETED) {
                    /* all done so let's write another message */
                    writeState = STATE_INIT;
                    current = null;
                }
            }
        } catch (IOException e) {
            eventLoop.log.error(getClass(), "doWrite", "Error in write operation", e);
            failed();
        }
    }

    /**
     * Add a write request to the queue - it will be picked by in the next
     * select loop.
     *
     * @param wr A write request
     */
    synchronized void queueWrite(WriteRequest wr) {
        writeQueue.add(wr);
        eventLoop.selector.wakeup();
    }

    /**
     * Checks whether there are queued requests to be written
     */
    synchronized boolean isWritePending() {
        return writeQueue.size() > 0;
    }


}