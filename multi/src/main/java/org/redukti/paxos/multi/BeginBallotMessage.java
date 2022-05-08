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
import org.redukti.paxos.log.api.Decree;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Also Phase2a ACCEPT message
 */
public class BeginBallotMessage implements PaxosMessage {

    static final String MESSAGE_TYPE = "ACCEPT (2a)";

    final BallotNum b;
    final int pid;
    final long cnum;
    final Decree[] chosenDecrees;
    final Decree[] committedDecrees; // optional

    public BeginBallotMessage(BallotNum b, int pid, long cnum, Decree[] chosenDecrees) {
        this(b, pid, cnum, chosenDecrees, new Decree[0]);
    }

    public BeginBallotMessage(BallotNum b, int pid, long cnum, Decree[] chosenDecrees, Decree[] committedDecrees) {
        this.b = b;
        this.pid = pid;
        this.cnum = cnum;
        this.chosenDecrees = chosenDecrees;
        this.committedDecrees = new Decree[0];
    }

    public BeginBallotMessage(ByteBuffer bb) {
        b = new BallotNum(bb);
        pid = bb.getInt();
        cnum = bb.getLong();
        int n = bb.getInt();
        chosenDecrees = new Decree[n];
        for (int i = 0; i < n; i++) {
            chosenDecrees[i] = new Decree(bb);
        }
        n = bb.getInt();
        committedDecrees = new Decree[n];
        for (int i = 0; i < n; i++) {
            committedDecrees[i] = new Decree(bb);
        }
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+BallotNum.size()+
                3*Integer.BYTES+Long.BYTES+
                chosenDecrees.length*Decree.size()+
                committedDecrees.length*Decree.size());
        bb.putShort((short)getCode());
        b.store(bb);
        bb.putInt(pid);
        bb.putLong(cnum);
        bb.putInt(chosenDecrees.length);
        for (int i = 0; i < chosenDecrees.length; i++) {
            chosenDecrees[i].store(bb);
        }
        bb.putInt(committedDecrees.length);
        for (int i = 0; i < committedDecrees.length; i++) {
            committedDecrees[i].store(bb);
        }
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.BEGIN_BALLOT_MESSAGE;
    }

    @Override
    public String toString() {
        return "BeginBallotMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", pid=" + pid +
                ", cnum=" + cnum +
                ", chosen[]=" + Arrays.asList(chosenDecrees) +
                ", committed[]=" + Arrays.asList(committedDecrees) +
                '}';
    }
}
