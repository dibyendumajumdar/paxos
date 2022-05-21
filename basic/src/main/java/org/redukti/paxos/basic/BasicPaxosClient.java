/**
 * MIT License
 *
 * Copyright (c) 2022 Dibyendu Majumdar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.redukti.paxos.basic;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.ResponseHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.redukti.paxos.basic.PaxosMessages.CLIENT_REQUEST_MESSAGE;

public class BasicPaxosClient implements ResponseHandler {
    static AtomicInteger received = new AtomicInteger(0);

    public static void main(String[] args) {

        BasicPaxosClient m = new BasicPaxosClient();
        try (EventLoopImpl eventLoop = new EventLoopImpl()) {

            //eventLoop.start("localhost", 9001, m);
            Connection connection = eventLoop.clientConnection("localhost", 9000, null);

            eventLoop.select();
            eventLoop.select();

            if (connection.isConnected()) {
                System.out.println("Sending request");
                connection.submit(makeRequest(), m, Duration.ofSeconds(1));
            }

            while (received.get() < 1)
                eventLoop.select();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static ByteBuffer makeRequest() {
        ByteBuffer bb = ByteBuffer.allocate(Long.BYTES + Short.BYTES);
        bb.putShort((short) CLIENT_REQUEST_MESSAGE);
        bb.putLong(521);
        bb.flip();
        return bb;
    }

    @Override
    public void onResponse(Message response) {
        ClientResponseMessage clientResponseMessage = (ClientResponseMessage) PaxosMessages.parseMessage(response.getCorrelationId(), response.getData());
        long value = clientResponseMessage.agreedValue;
        System.out.println("Received back " + value);
        received.incrementAndGet();
    }
}
