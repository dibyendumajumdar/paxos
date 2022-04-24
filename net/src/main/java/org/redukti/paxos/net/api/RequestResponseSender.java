package org.redukti.paxos.net.api;

import java.nio.ByteBuffer;

public interface RequestResponseSender {
    void setData(ByteBuffer data);
    void setErrored(String errorMessage);
    void submit();
}
