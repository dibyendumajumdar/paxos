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
 * Also known as Phase 2b - ACCEPTED message
 */
public class VotedMessage implements PaxosMessage {

    static final String MESSAGE_TYPE = "ACCEPTED (2b)";

    final BallotNum b;
    final int pid;

    public VotedMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.pid = bb.get();
    }

    public VotedMessage(BallotNum b, int id) {
        this.b = b;
        this.pid = id;
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+BallotNum.size()+Byte.BYTES);
        bb.putShort((short)getCode());
        b.store(bb);
        bb.put((byte) pid);
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.VOTED_MESSAGE;
    }

    @Override
    public String toString() {
        return "VotedMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", owner=" + pid +
                '}';
    }
}
