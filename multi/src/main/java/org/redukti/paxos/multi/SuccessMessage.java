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

import org.redukti.paxos.log.api.Decree;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class SuccessMessage implements PaxosMessage {

    final Decree[] decree;

    public SuccessMessage(Decree[] decree) {
        this.decree = decree;
    }

    public SuccessMessage(ByteBuffer bb) {
        int n = bb.getInt();
        this.decree = new Decree[n];
        for (int i = 0; i < n; i++) {
            this.decree[i] = new Decree(bb);
        }
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+Integer.BYTES+decree.length*Decree.size());
        bb.putShort((short)getCode());
        bb.putInt(decree.length);
        for (int i = 0; i < decree.length; i++) {
            Decree d = decree[i];
            d.store(bb);
        }
        bb.flip();
        return bb;
    }

    @Override
    public int getCode() {
        return PaxosMessages.SUCCESS_MESSAGE;
    }

    @Override
    public String toString() {
        return "SuccessMessage{" +
                "decree[]=" + Arrays.asList(decree) +
                '}';
    }
}
