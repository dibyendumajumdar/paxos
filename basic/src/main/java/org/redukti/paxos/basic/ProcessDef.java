package org.redukti.paxos.basic;

import java.util.ArrayList;
import java.util.List;

final class ProcessDef {
    String address;
    int port;

    public ProcessDef(String address, int port) {
        this.address = address;
        this.port = port;
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static List<ProcessDef> parseProcesses(String arg) {
        List<ProcessDef> defs = new ArrayList<>();
        String[] args = arg.split(";");
        for (int i = 0; i < args.length; i++) {
            String[] parts = args[i].split(":");
            if (parts.length != 2)
                continue;
            String addr = parts[0];
            int port = parseInt(parts[1]);
            if (port < 0)
                continue;
            defs.add(new ProcessDef(addr, port));
        }
        return defs;
    }

    @Override
    public String toString() {
        return address + ':' + port;
    }
}
