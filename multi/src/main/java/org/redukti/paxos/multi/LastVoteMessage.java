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
import java.util.Arrays;

/**
 * Also known as message type "1b" or PROMISE message.
 */
public class LastVoteMessage implements PaxosMessage, ParticipantInfo {

    static final String MESSAGE_TYPE = "PROMISE (1b)";

    /**
     * BallotNumber for which a promise is being made
     */
    BallotNum b;

    /**
     * Sender id
     */
    int pid;

    /**
     * Commit num of sender
     */
    long cnum;

    /**
     * Votes cast in previous ballots
     */
    Vote[] votes;

    public LastVoteMessage(BallotNum b, int pid, long cnum, Vote[] votes) {
        this.b = b;
        this.pid = pid;
        this.cnum = cnum;
        this.votes = votes;
    }

    public LastVoteMessage(ByteBuffer bb) {
        this.b = new BallotNum(bb);
        this.pid = bb.getInt();
        this.cnum = bb.getLong();
        int n = bb.getInt();
        votes = new Vote[n];
        for (int i = 0; i < votes.length; i++) {
            votes[i] = new Vote(bb);
        }
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES
                +BallotNum.size()+Integer.BYTES*2
                +Long.BYTES+votes.length*Vote.size());
        bb.putShort((short)getCode());
        b.store(bb);
        bb.putInt(pid);
        bb.putLong(cnum);
        bb.putInt(votes.length);
        for (int i = 0; i < votes.length; i++){
            votes[i].store(bb);
        }
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.LAST_VOTE_MESSAGE;
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
        return "LastVoteMessage{" +
                "type=" + MESSAGE_TYPE +
                ", b=" + b +
                ", pid=" + pid +
                ", cnum=" + cnum +
                ", votes[]=" + Arrays.asList(votes) +
                '}';
    }
}
