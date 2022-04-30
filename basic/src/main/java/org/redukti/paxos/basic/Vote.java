package org.redukti.paxos.basic;

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
    final int process;
    final BallotNum ballotNum;
    final Decree decree;

    public Vote(int process, BallotNum ballotNum, Decree decree) {
        this.process = process;
        this.ballotNum = ballotNum;
        this.decree = decree;
    }

    public Vote(ByteBuffer bb) {
        this.process = bb.get();
        this.ballotNum = new BallotNum(bb);
        this.decree = new Decree(bb);
    }

    public void store(ByteBuffer bb) {
        bb.put((byte) process);
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
        return compareTo(vote) == 0 && process == vote.process;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ballotNum);
    }

    /**
     * For any vote v and v', if v.ballotNum < v'.ballotNum then
     * v < v'. p6.
     */
    @Override
    public int compareTo(Vote o) {
        return ballotNum.compareTo(o.ballotNum);
    }

    @Override
    public String toString() {
        return "Vote{" +
                "p=" + process +
                ", b=" + ballotNum +
                ", decree=" + decree +
                '}';
    }
}
