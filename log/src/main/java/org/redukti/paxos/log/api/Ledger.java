/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

public interface Ledger {

    // decree num is equivalent to the log entry id
    // ballot num is the proposal number (made unique by proposer id),
    //            -1 is treated as special case

    /**
     * Records the decree written in p's ledger
     */
    void setOutcome(long decreeNum, byte[] data);

    /**
     * The decree written in p's ledger or null if there is nothing written there yet
     */
    byte[] getOutcome(long decreeNum);

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
     * Sets the number of the last ballot in which p voted
     */
    void setPrevBallot(BallotNum ballot);
    /**
     * The number of the last ballot in which p voted, or BallotNum.MINUS_INFINITY if p never voted
     */
    BallotNum getPrevBallot();

    /**
     * The decree for which p last voted
     */
    void setPrevDec(Decree decree);

    /**
     * The decree for which p last voted, or null if p never voted
     */
    Decree getPrevDec();

    /**
     * The number of the last ballot in which p agreed to participate
     */
    void setLastBallot(BallotNum ballot);

    /**
     * he number of the last ballot in which p agreed to participate, or
     * BallotNum.MINUS_INFINITY if he has never agreed to participate in a ballot.
     */
    BallotNum getLastBallot();
}
