/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

import java.util.List;

public interface Ledger extends AutoCloseable {

    // decree num is equivalent to the log entry id
    // ballot num is the proposal number (made unique by proposer id),
    //            -1 is treated as special case

    /**
     * Records the decree written in p's ledger
     */
    void setOutcome(long decreeNum, long data);

    /**
     * The decree written in p's ledger or null if there is nothing written there yet
     */
    Long getOutcome(long decreeNum);

    /**
     * Sets the number of the last ballot that p tried to begin
     */
    void setLastTried(BallotNum ballot);

    /**
     * The number of last ballot that p tried to begin or BallotNum.MINUS_INFINITY if there was none
     * @return
     */
    BallotNum getLastTried();

    /**
     * Sets the number of the last ballot in which p voted, as well as the value voted for
     * Also known as MaxVBal and MaxVal
     */
    void setPrevBallot(BallotNum ballot, long dnum, long value);
    default void setPrevBallot(BallotNum ballot, long maxVal) { setPrevBallot(ballot, 0, maxVal); }
    default void setMaxVBal(BallotNum ballot, long dnum, long maxVal) { setPrevBallot(ballot, dnum, maxVal); }
    default void setMaxVBal(BallotNum ballot, long maxVal) { setMaxVBal(ballot, 0, maxVal); }
    /**
     * The number of the last ballot in which p voted, or BallotNum.MINUS_INFINITY if p never voted
     * Also known as MaxVBal
     */
    BallotNum getPrevBallot(long dnum);
    default BallotNum getPrevBallot() { return getPrevBallot(0); }
    default BallotNum getMaxVBal(long dnum) { return getPrevBallot(dnum); }
    default BallotNum getMaxVBal() { return getMaxVBal(0); }

    /**
     * The decree for which p last voted, or null if p never voted
     * Also known as MaxVal
     */
    Decree getPrevDec(long dnum);
    default Decree getPrevDec() { return getPrevDec(0); }
    default Decree getMaxVal(long dnum) { return getPrevDec(dnum); }
    default Decree getMaxVal() { return getMaxVal(0); }

    /**
     * The number of the last ballot in which p agreed to participate
     * Also known as MaxBal
     */
    void setNextBallot(BallotNum ballot);
    default void setMaxBal(BallotNum ballot) { setNextBallot(ballot); }

    /**
     * he number of the last ballot in which p agreed to participate, or
     * BallotNum.MINUS_INFINITY if he has never agreed to participate in a ballot.
     * Also known as MaxBal
     */
    BallotNum getNextBallot();
    default BallotNum getMaxBal() { return getNextBallot(); }

    /**
     * The most recent decree number that was committed
     */
    long getCommitNum();

    /**
     * Get a list of the ballots we know about
     */
    List<BallotedDecree> getUndecidedBallots();
}
