/**
 * MIT License
 * <p>
 * Copyright (c) 2022 Dibyendu Majumdar
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.redukti.paxos.multi;

import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.log.impl.LedgerImpl;
import org.redukti.paxos.net.api.EventLoop;
import org.redukti.paxos.net.impl.EventLoopImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A simple driver program for a MultiPaxos process.
 */
public class MultiPaxosProcess {

    final static Logger log = LoggerFactory.getLogger(MultiPaxosProcess.class);

    int pid = -1;
    ProcessDef myDef;
    String logPath;

    List<ProcessChannel> remoteProcesses = new ArrayList<>();
    List<ProcessDef> allDefs = new ArrayList<>();

    ScheduledExecutorService scheduledExecutorService;
    EventLoop eventLoop;

    Ledger ledger;
    String ledgerName;

    ThisPaxosParticipant me;

    void parseArguments(String[] args) {
        String idStr = null;
        for (int i = 0; i < args.length; i++) {
            String opt = args[i];
            switch (opt) {
                case "--connections": {
                    String connectStr = null;
                    if (i + 1 < args.length) {
                        connectStr = args[++i];
                    }
                    if (connectStr != null) {
                        allDefs = ProcessDef.parseProcesses(connectStr);
                    }
                    break;
                }
                case "--myid": {
                    if (i + 1 < args.length) {
                        idStr = args[++i];
                    }
                    if (idStr != null) {
                        try {
                            pid = Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            pid = -1;
                        }
                    }
                    break;
                }
                case "--logpath": {
                    if (i + 1 < args.length) {
                        logPath = args[++i];
                    }
                    break;
                }
            }
        }
    }

    boolean checkArgs() {
        StringBuilder errmsg = new StringBuilder();
        boolean result = true;
        if (logPath == null) {
            errmsg.append(System.lineSeparator()).append("Use --logPath to set location of logs");
            result = false;
        }
        if (allDefs.size() != 3) {
            errmsg.append(System.lineSeparator()).append("Must set three process definitions using --connections");
            result = false;
        }
        if (pid < 0 || pid > allDefs.size()) {
            errmsg.append(System.lineSeparator()).append("--myid must set a value between 0 and " + allDefs.size());
            result = false;
        }
        if (!result) {
            log.error(errmsg.toString());
        } else {
            myDef = new ProcessDef(allDefs.get(pid).address, allDefs.get(pid).port);
            ledgerName = "ledger_" + pid + ".log";
        }
        return result;
    }

    void startServer() {
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        eventLoop = new EventLoopImpl();
        if (LedgerImpl.exists(logPath, ledgerName)) {
            ledger = LedgerImpl.open(logPath, ledgerName, pid);
        } else {
            ledger = LedgerImpl.createIfNotExisting(logPath, ledgerName, pid);
        }
        me = new ThisPaxosParticipant(pid, ledger);
        eventLoop.startServerChannel(myDef.address, myDef.port, me);
        startConnections();
        me.addRemotes(getRemotes());
    }

    List<PaxosParticipant> getRemotes() {
        List<PaxosParticipant> remoteParticipants = new ArrayList<>();
        for (ProcessChannel p : remoteProcesses) {
            remoteParticipants.add(new RemotePaxosParticipant(p.id, p));
        }
        return remoteParticipants;
    }

    void startConnections() {
        for (int i = 0; i < allDefs.size(); i++) {
            if (i == pid)
                continue;
            ProcessDef def = allDefs.get(i);
            ProcessChannel pc = new ProcessChannel(i, def, eventLoop, scheduledExecutorService);
            remoteProcesses.add(pc);
        }
        for (ProcessChannel pc : remoteProcesses) {
            pc.connect();
        }
    }

    void startClientRequest() {
        if (me.isPendingClientRequests() && !me.isHandlingClientRequest())
            me.doOneClientRequest();
    }

    public static void main(String[] args) {
        try {
            MultiPaxosProcess p = new MultiPaxosProcess();
            p.parseArguments(args);
            if (!p.checkArgs()) {
                System.exit(1);
                return;
            }
            p.startServer();
            while (true) {
                p.eventLoop.select();
                p.startClientRequest();
            }
        } catch (Exception e) {
            log.error("Error occurred", e);
            System.exit(1);
        }
    }

}
