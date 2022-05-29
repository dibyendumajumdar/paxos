package org.redukti.paxos.log.api;

public class BallotedDecree {
    public final BallotNum b;
    public final Decree decree;

    public BallotedDecree(BallotNum b, Decree decree) {
        this.b = b;
        this.decree = decree;
    }
}
