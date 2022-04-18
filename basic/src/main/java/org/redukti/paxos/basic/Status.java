package org.redukti.paxos.basic;

public enum Status {
    IDLE, // Not conducting or trying to begin a ballot
    TRYING, // Trying to begin a ballot number ledger.lastTried
    POLLING // Now conducting ballot number ledger.lastTried
}
