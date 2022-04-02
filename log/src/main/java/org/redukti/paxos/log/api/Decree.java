/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

import java.nio.ByteBuffer;

public class Decree {
    long decreeNum;
    long value;

    public static int size() {
        return Long.BYTES * 2;
    }

    public void store(ByteBuffer bb) {
        bb.putLong(decreeNum);
        bb.putLong(value);
    }

    public Decree(ByteBuffer bb) {
        this.decreeNum = bb.getLong();
        this.value = bb.getLong();
    }

    public Decree(long decreeNum, long value) {
        this.decreeNum = decreeNum;
        this.value = value;
    }
}
