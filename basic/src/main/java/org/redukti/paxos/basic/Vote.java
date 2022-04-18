package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

import java.nio.ByteBuffer;
import java.util.Objects;

public class Vote implements Comparable<Vote> {
    final int p;
    final BallotNum b;
    final Decree decree;

    public Vote(int p, BallotNum b, Decree decree) {
        this.p = p;
        this.b = b;
        this.decree = decree;
    }

    public Vote(ByteBuffer bb) {
        this.p = bb.get();
        this.b = new BallotNum(bb);
        this.decree = new Decree(bb);
    }

    public void store(ByteBuffer bb) {
        bb.put((byte) p);
        b.store(bb);
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
        return Objects.hash(b);
    }

    @Override
    public int compareTo(Vote o) {
        return b.compareTo(o.b);
    }

    @Override
    public String toString() {
        return "Vote{" +
                "p=" + p +
                ", b=" + b +
                ", decree=" + decree +
                '}';
    }
}
