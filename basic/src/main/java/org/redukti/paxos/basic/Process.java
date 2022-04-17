package org.redukti.paxos.basic;

import org.redukti.paxos.net.api.EventLoop;
import org.redukti.paxos.net.api.Message;
import org.redukti.paxos.net.api.RequestHandler;
import org.redukti.paxos.net.impl.EventLoopImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Process implements RequestHandler {

    final static Logger log = LoggerFactory.getLogger(Process.class);

    int myId = -1;
    ProcessDef myDef;
    String logPath;

    List<ProcessChannel> remoteProcesses = new ArrayList<>();
    List<ProcessDef> allDefs = new ArrayList<>();

    ScheduledExecutorService scheduledExecutorService;
    EventLoop eventLoop;

    void parseArguments(String[] args) {
        String idStr = null;
        for (int i = 0; i < args.length; i++) {
            String opt = args[i];
            switch (opt) {
                case "--connections": {
                    String connectStr = null;
                    if (i+1 < args.length) {
                        connectStr = args[++i];
                    }
                    if (connectStr != null) {
                        allDefs = ProcessDef.parseProcesses(connectStr);
                    }
                    break;
                }
                case "--myid": {
                    if (i+1 < args.length) {
                        idStr = args[++i];
                    }
                    if (idStr != null) {
                        try {
                            myId = Integer.parseInt(idStr);
                        }
                        catch (NumberFormatException e) {
                            myId = -1;
                        }
                    }
                    break;
                }
                case "--logpath": {
                    if (i+1 < args.length) {
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
        if (myId < 0 || myId > allDefs.size()) {
            errmsg.append(System.lineSeparator()).append("--myid must set a value between 0 and 3");
            result = false;
        }
        if (!result) {
            log.error(errmsg.toString());
        }
        else {
            myDef = new ProcessDef(allDefs.get(myId).address, allDefs.get(myId).port);
        }
        return result;
    }

    void startServer() {
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        eventLoop = new EventLoopImpl();
        eventLoop.startServerChannel(myDef.address, myDef.port, this);
        startConnections();
    }

    void startConnections() {
        for (int i = 0; i < allDefs.size(); i++) {
            if (i == myId)
                continue;
            ProcessDef def = allDefs.get(i);
            ProcessChannel pc = new ProcessChannel(i, def, eventLoop, scheduledExecutorService);
            remoteProcesses.add(pc);
        }
        for (ProcessChannel pc: remoteProcesses) {
            pc.connect();
        }
    }

    public static void main(String[] args) {
        try {
            Process me = new Process();
            me.parseArguments(args);
            if (!me.checkArgs()) {
                System.exit(1);
                return;
            }
            me.startServer();
            while (true) {
                me.eventLoop.select();
            }
        }
        catch (Exception e) {
            log.error("Error occurred", e);
            System.exit(1);
        }
    }

    @Override
    public void handleRequest(Message request, Message response) {
        
    }
}
