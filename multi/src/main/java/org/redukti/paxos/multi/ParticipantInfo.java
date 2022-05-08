package org.redukti.paxos.multi;

public interface ParticipantInfo {
    int getPid();
    long commitNum();
}
