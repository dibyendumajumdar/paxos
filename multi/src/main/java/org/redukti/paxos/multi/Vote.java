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
import java.util.Objects;

/**
 * A Vote is defined as the quantity consisting of 3 components: a
 * process (priest), a ballot number and a decree. It represents a vote cast
 * by a process for said decree in said ballot number. p6.
 */
public class Vote implements Comparable<Vote> {
    /**
     * Process id of the voter
     */
    final int pid;
    final BallotNum ballotNum;
    final Decree decree;

    public Vote(int pid, BallotNum ballotNum, Decree decree) {
        this.pid = pid;
        this.ballotNum = ballotNum;
        this.decree = decree;
    }

    public Vote(ByteBuffer bb) {
        this.pid = bb.get();
        this.ballotNum = new BallotNum(bb);
        this.decree = new Decree(bb);
    }

    public void store(ByteBuffer bb) {
        bb.put((byte) pid);
        ballotNum.store(bb);
        decree.store(bb);
    }

    public static int size() {
        return Byte.BYTES + BallotNum.size() + Decree.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote = (Vote) o;
        return compareTo(vote) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ballotNum, pid);
    }

    /**
     * For any vote v and v', if v.ballotNum < v'.ballotNum then
     * v < v'. p6.
     */
    @Override
    public int compareTo(Vote o) {
        int result = ballotNum.compareTo(o.ballotNum);
        if (result == 0)
            // tie breaker, does it matter which process? Think not
            result = Integer.compare(pid, o.pid);
        return result;
    }

    @Override
    public String toString() {
        return "Vote{" +
                "pid=" + pid +
                ", b=" + ballotNum +
                ", decree=" + decree +
                '}';
    }
}
