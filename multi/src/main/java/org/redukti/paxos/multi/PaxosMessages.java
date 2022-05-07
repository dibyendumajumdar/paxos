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
package org.redukti.paxos.multi;

import org.redukti.paxos.net.impl.CorrelationId;

import java.nio.ByteBuffer;

public class PaxosMessages {
    static final int NEXT_BALLOT_MESSAGE = 1;
    static final int LAST_VOTE_MESSAGE = 2;
    static final int BEGIN_BALLOT_MESSAGE = 3;
    static final int VOTED_MESSAGE = 4;
    static final int SUCCESS_MESSAGE = 5;
    static final int CLIENT_REQUEST_MESSAGE = 6;
    static final int CLIENT_RESPONSE_MESSAGE = 7;

    public static PaxosMessage parseMessage(CorrelationId correlationId, ByteBuffer bb) {
        int messageId = bb.getShort();
        switch (messageId) {
            case NEXT_BALLOT_MESSAGE: {
                return new NextBallotMessage(bb);
            }
//            case LAST_VOTE_MESSAGE: {
//                return new LastVoteMessage(bb);
//            }
//            case BEGIN_BALLOT_MESSAGE: {
//                return new BeginBallotMessage(bb);
//            }
//            case VOTED_MESSAGE: {
//                return new VotedMessage(bb);
//            }
//            case SUCCESS_MESSAGE: {
//                return new SuccessMessage(bb);
//            }
            case CLIENT_REQUEST_MESSAGE: {
                return new ClientRequestMessage(correlationId, bb);
            }
//            case CLIENT_RESPONSE_MESSAGE: {
//                return new ClientResponseMessage(bb);
//            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

}
