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

import java.nio.ByteBuffer;

public class ClientResponseMessage implements PaxosMessage {

    final long agreedValue;
    final long dnum;

    public ClientResponseMessage(long dnum, long agreedValue) {
        this.dnum = dnum;
        this.agreedValue = agreedValue;
    }

    public ClientResponseMessage(ByteBuffer bb) {
        this.dnum = bb.getLong();
        this.agreedValue = bb.getLong();
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES+2*Long.BYTES);
        bb.putShort((short)getCode());
        bb.putLong(dnum);
        bb.putLong(agreedValue);
        return bb.flip();
    }

    @Override
    public int getCode() {
        return PaxosMessages.CLIENT_RESPONSE_MESSAGE;
    }

    @Override
    public String toString() {
        return "ClientResponseMessage{" +
                "dnum=" + dnum +
                ", agreedValue=" + agreedValue +
                '}';
    }
}
