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
 * Also known as PREPARE message or message type "1a"
 */
public class NextBallotMessage implements PaxosMessage, ParticipantInfo {

    static final String MESSAGE_TYPE = "PREPARE (1a)";

    /**
     * BallotNumber of new ballot being started
     */
    final BallotNum b;

    /**
     * Max consecutive committed decree number - commit number
     * Set from ledger.commmitNum().
     */
    final long cnum;

    /**
     * Sender id;
     */
    final int pid;

    public NextBallotMessage(BallotNum b, int pid, long cnum) {
        this.b = b;
        this.pid = pid;
        this.cnum = cnum;
    }

    public NextBallotMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.pid = bb.getInt();
        this.cnum = bb.getLong();
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES + BallotNum.size() + Integer.BYTES + Long.BYTES);
        bb.putShort((short) getCode());
        b.store(bb);
        bb.putInt(pid);
        bb.putLong(cnum);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.NEXT_BALLOT_MESSAGE;
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
        return "NextBallotMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", id=" + pid +
                ", cnum=" + cnum +
                '}';
    }
}
