/**
 * MIT License
 *
 * Copyright (c) 2022 Dibyendu Majumdar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.redukti.paxos.multi;

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
