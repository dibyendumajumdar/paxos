/**
 * Copyright (c) 2022 Dibyendu Majumdar
 * MIT License
 */
package org.redukti.paxos.log.api;

import java.nio.ByteBuffer;
import java.util.Objects;

public class Decree implements Comparable<Decree> {
    public final long decreeNum;
    public final long value;

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

    @Override
    public int compareTo(Decree o) {
        int result = Long.compare(decreeNum, o.decreeNum);
        if (result == 0)
            result = Long.compare(value, o.value);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Decree decree = (Decree) o;
        return compareTo(decree) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(decreeNum, value);
    }

    public boolean isNull() {
        return decreeNum == -1;
    }
}
