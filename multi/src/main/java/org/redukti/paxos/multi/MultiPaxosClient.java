/**
 * MIT License
 * <p>
 * Copyright (c) 2022 Dibyendu Majumdar
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.redukti.paxos.multi;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiPaxosClient implements ResponseHandler {
    static AtomicInteger received = new AtomicInteger(0);

    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Error: please supply port and value");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        long value = Long.parseLong(args[1]);

        MultiPaxosClient m = new MultiPaxosClient();
        try (EventLoopImpl eventLoop = new EventLoopImpl()) {

            Connection connection = eventLoop.clientConnection("localhost", port, null);

            eventLoop.select();
            eventLoop.select();

            if (connection.isConnected()) {
                System.out.println("Sending request");
                connection.submit(makeRequest(value), m, Duration.ofSeconds(1));
            }

            while (received.get() < 1 && !connection.isErrored())
                eventLoop.select();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static ByteBuffer makeRequest(long value) {
        return new ClientRequestMessage(value).serialize();
    }

    @Override
    public void onTimeout() {

    }

    @Override
    public void onException(Exception e) {
    }

    @Override
    public void onResponse(Message response) {
        ClientResponseMessage clientResponseMessage = (ClientResponseMessage) PaxosMessages.parseMessage(response.getCorrelationId(), response.getData());
        System.out.println("Received back " + clientResponseMessage);
        received.incrementAndGet();
    }
}
