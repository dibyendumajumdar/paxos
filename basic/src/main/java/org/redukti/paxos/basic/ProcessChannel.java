package org.redukti.paxos.basic;

import org.redukti.paxos.net.api.Connection;
import org.redukti.paxos.net.api.ConnectionListener;
import org.redukti.paxos.net.api.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProcessChannel implements ConnectionListener {

    final static Logger log = LoggerFactory.getLogger(ProcessChannel.class);

    final int id;
    final ProcessDef def;
    volatile Connection connection;
    final EventLoop eventLoop;
    final ScheduledExecutorService executorService;

    public ProcessChannel(int id, ProcessDef def, EventLoop eventLoop, ScheduledExecutorService executorService) {
        this.id = id;
        this.def = def;
        this.eventLoop = eventLoop;
        this.executorService = executorService;
    }

    public void connect() {
        connection = eventLoop.clientConnection(def.address, def.port, this);
    }

    @Override
    public void onConnectionFailed() {
        log.error("Failed to connect to remote process " + def + "; will retry in 10 seconds");
        executorService.schedule(() -> {
            connect();
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onConnectionSuccess() {
        log.info("Connected to remote process " + def);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessChannel that = (ProcessChannel) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProcessChannel{" +
                "id=" + id +
                ", def=" + def +
                '}';
    }
}
