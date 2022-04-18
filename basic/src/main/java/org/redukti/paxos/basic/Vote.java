package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

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
}
