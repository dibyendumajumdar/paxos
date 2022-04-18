package org.redukti.paxos.basic;

import java.nio.ByteBuffer;

public interface PaxosMessage {
    ByteBuffer serialize();
    int getCode();
}
