package org.redukti.paxos.log.api;

public class BallotedDecree {
    final BallotNum b;
    final Decree decree;

    public BallotedDecree(BallotNum b, Decree decree) {
        this.b = b;
        this.decree = decree;
    }
}
