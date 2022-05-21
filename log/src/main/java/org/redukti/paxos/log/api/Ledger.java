/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

import java.util.List;

/**
 * In Leslie Lamport's PTP paper, the ledger is where the priest notes down stuff that he/she must not
 * forget. In general terms, the ledger is equivalent to a log kept by each process for stuff that must be remembered
 * even if a process dies.
 */
public interface Ledger extends AutoCloseable {

    // decree num is equivalent to the log entry id
    // ballot num is the proposal number (made unique by proposer id),
    //            -1 is treated as special case

    /**
     * Records the decree written in p's ledger with status COMMITTED.
     * This also updates commitNum.
     */
    void setOutcome(long decreeNum, long value);

    /**
     * The decree written in p's ledger or null if there is nothing written there yet, i.e. value was not
     * COMMITTED
     */
    Long getOutcome(long decreeNum);

    /**
     * Sets the number of the last ballot that p tried to start; p = owner(ballot) here.
     */
    void setLastTried(BallotNum ballot);

    /**
     * The number of last ballot that p tried to begin or BallotNum.MINUS_INFINITY if there was none
     * @return
     */
    BallotNum getLastTried();

    /**
     * Sets the number of the last ballot in which p voted for given decree dnum, as well as the value voted for
     * Also known as setPrevBal
     *
     * This implies that the value for decree number dnum is not yet COMMITTED,
     * and it is an error if that is not true.
     *
     * @param ballot Ballot number being stored
     * @param dnum decree number
     * @param value the value to be stored against the decree dnum
     */
    void setMaxVBal(BallotNum ballot, long dnum, long value);

    /**
     * This variant is used in Basic Paxos which only ever works with decree number 0
     */
    default void setMaxVBal(BallotNum ballot, long maxVal) { setMaxVBal(ballot, 0, maxVal); }
    /**
     * The number of the last ballot in which p voted, or BallotNum.MINUS_INFINITY if p never voted
     * Also known as prevBal
     */
    BallotNum getMaxVBal(long dnum);
    /**
     * This variant is used in Basic Paxos which only ever works with decree number 0
     */
    default BallotNum getMaxVBal() { return getMaxVBal(0); }

    /**
     * The decree for which p last voted, or null if p never voted
     * Also known as prevDec
     */
    Decree getMaxVal(long dnum);
    /**
     * This variant is used in Basic Paxos which only ever works with decree number 0
     */
    default Decree getMaxVal() { return getMaxVal(0); }

    /**
     * The number of the last ballot in which p agreed to participate
     * Also known as nextBallot
     */
    void setMaxBal(BallotNum ballot);

    /**
     * The number of the last ballot in which p agreed to participate, or
     * BallotNum.MINUS_INFINITY if he has never agreed to participate in a ballot.
     * Also known as nextBallot
     */
    BallotNum getMaxBal();

    /**
     * The highest decree number that was committed sequentially without a gap
     */
    long getCommitNum();

    /**
     * Get a list of the ballots that were saved via setMaxVBal() but are not yet committed,
     * i.e. setOutcome() was not called
     */
    List<BallotedDecree> getUndecidedBallots();
}
