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

import org.redukti.paxos.log.api.BallotNum;

import java.nio.ByteBuffer;

/**
 * This message is sent by an acceptor if it can accept but
 * has pending outcomes.
 */
public class PendingVoteMessage implements PaxosMessage, ParticipantInfo {

    static final String MESSAGE_TYPE = "Pending ACCEPT (2b)";

    final BallotNum b;
    final int pid;
    final long cnum;

    public PendingVoteMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.pid = bb.get();
        this.cnum = bb.getLong();
    }

    public PendingVoteMessage(BallotNum b, int id, long cnum) {
        this.b = b;
        this.pid = id;
        this.cnum = cnum;
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+BallotNum.size()+Byte.BYTES+Long.BYTES);
        bb.putShort((short)getCode());
        b.store(bb);
        bb.put((byte) pid);
        bb.putLong(cnum);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.PENDING_VOTE_MESSAGE;
    }

    @Override
    public int getPid() {
        return pid;
    }

    @Override
    public long commitNum() {
        return cnum;
    }

    @Override
    public String toString() {
        return "PendingVoteMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", pid=" + pid +
                ", cnum=" + cnum +
                '}';
    }
}
