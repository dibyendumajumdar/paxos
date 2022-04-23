/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

public interface Ledger extends AutoCloseable {

    // decree num is equivalent to the log entry id
    // ballot num is the proposal number (made unique by proposer id),
    //            -1 is treated as special case
    // Note that for Basic Paxos, only one decree can be agreed upon
    // You need Multi-Paxos to support multiple decreeNums

    /**
     * Records the decree written in p's ledger
     */
    void setOutcome(long decreeNum, long data);

    /**
     * The decree written in p's ledger or null if there is nothing written there yet
     */
    Long getOutcome(long decreeNum);

    /**
     * Sets the number of the last ballot that p tried to begin for the given decreeNum
     */
    void setLastTried(long decreeNum, BallotNum ballot);

    /**
     * The number of last ballot that p tried to begin or BallotNum.MINUS_INFINITY if there was none,
     * for the given decreeNum
     */
    BallotNum getLastTried(long decreeNum);

    /**
     * Sets the number of the last ballot in which p voted, for the given decreeNum
     */
    void setPrevBallot(long decreeNum, BallotNum ballot);
    /**
     * The number of the last ballot in which p voted, or BallotNum.MINUS_INFINITY if p never voted,
     * for given decreeNum
     */
    BallotNum getPrevBallot(long decreeNum);

    /**
     * The decree for which p last voted, for given decreeNum
     */
    void setPrevDec(long decreeNum, Decree decree);

    /**
     * The decree for which p last voted, or null if p never voted,
     * for given decreeNum
     */
    Decree getPrevDec(long decreeNum);

    /**
     * The number of the last ballot in which p agreed to participate, for the given decreeNum
     */
    void setNextBallot(long decreeNum, BallotNum ballot);

    /**
     * The number of the last ballot in which p agreed to participate, or
     * BallotNum.MINUS_INFINITY if he has never agreed to participate in a ballot,
     * for given decreeNum
     */
    BallotNum getNextBallot(long decreeNum);

    /**
     * Obtain the maximum decree that was committed to the ledger
     */
    long getMaxDecreeNum();
}
