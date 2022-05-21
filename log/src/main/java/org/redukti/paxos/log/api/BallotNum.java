/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A BallotNum uniquely identifies a ballot. For a description of what a ballot is
 * please refer to the PTP paper by Leslie Lamport.
 *
 * BallotNum is made up of two parts - a proposal number, and a process id.
 * BallotNum's are ordered by proposal number and process id, so that BallotNum(2,0) > BallotNum(1,0).
 */
public class BallotNum implements Comparable<BallotNum> {

    static final BallotNum MINUS_INFINITY = new BallotNum(-1L,0);

    public final long proposalNumber;
    /**
     * Identifies the process that owns this ballot number.
     */
    public final int processNum;

    public BallotNum(long proposalNumber, int processNum) {
        this.proposalNumber = proposalNumber;
        this.processNum = processNum;
    }

    @Override
    public int compareTo(BallotNum o) {
        if (proposalNumber < 0 && o.proposalNumber < 0)
            return Integer.compare(processNum, o.processNum);
        int result = Long.compare(proposalNumber, o.proposalNumber);
        if (result == 0)
            result = Integer.compare(processNum, o.processNum);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BallotNum ballotNum = (BallotNum) o;
        return compareTo(ballotNum) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalNumber, processNum);
    }

    @Override
    public String toString() {
        return "BallotNum={" +
                "proposalNum=" + proposalNumber +
                ", processNum=" + processNum +
                '}';
    }

    public static int size() {
        return Integer.BYTES + Long.BYTES;
    }

    public void store(ByteBuffer bb) {
        bb.putLong(proposalNumber);
        bb.putInt(processNum);
    }

    public BallotNum(ByteBuffer bb) {
        this.proposalNumber = bb.getLong();
        this.processNum = bb.getInt();
    }

    public BallotNum increment() {
        return new BallotNum(proposalNumber+1, processNum);
    }

    public boolean isNull() {
        return proposalNumber < 0;
    }

    public int owner() {
        return processNum;
    }
}
