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

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

/**
 * Also known as message type "1b" or PROMISE message.
 */
public class LastVoteMessage implements PaxosMessage {

    static final String MESSAGE_TYPE = "PROMISE (1b)";

    /**
     * BallotNumber for which a promise is being made
     */
    BallotNum b;
    /**
     * The pair MaxVBal and MaxVal - represent the highest numbered
     * ballot / and its value that was previously voted on
     */
    Vote v;

    public LastVoteMessage(BallotNum b, Vote v) {
        this.b = b;
        this.v = v;
    }

    public LastVoteMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.v = new Vote(bb);
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+BallotNum.size()+Vote.size());
        bb.putShort((short)getCode());
        b.store(bb);
        v.store(bb);
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.LAST_VOTE_MESSAGE;
    }

    @Override
    public String toString() {
        return "LastVoteMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", v=" + v +
                '}';
    }
}
