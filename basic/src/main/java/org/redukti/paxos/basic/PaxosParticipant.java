package org.redukti.paxos.basic;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;

import java.util.Objects;

public abstract class PaxosParticipant {

    /**
     * Each PaxosParticipant has an id that marks its position in the
     * process array
     */
    public abstract int getId();

    // equivalent to phase 1 (a) prepare request
    public abstract void sendNextBallot(BallotNum b);

    // equivalent to phase 1 (b) promise message
    public abstract void sendLastVoteMessage(BallotNum b, Vote v);

    // equivalent to phase 2 (a) accept request
    public abstract void sendBeginBallot(BallotNum b, Decree decree);

    // equivalent to phase 2 (b) accepted message
    public abstract void sendVoted(BallotNum prevBal, int id);

    public abstract void sendSuccess(Decree decree);

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaxosParticipant p = (PaxosParticipant) o;
        return getId() == p.getId();
    }
}
