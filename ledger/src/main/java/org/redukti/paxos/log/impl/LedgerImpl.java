package org.redukti.paxos.log.impl;

import org.redukti.logging.Logger;
import org.redukti.logging.LoggerFactory;
import org.redukti.paxos.log.api.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.List;

// TODO implementation is simple, but we should cache values in memory


public class LedgerImpl implements Ledger {

    final static Logger log = LoggerFactory.DEFAULT.getLogger(LedgerImpl.class.getName());
    public static final int PAGE_SIZE = 8 * 1024;

    /**
     * The underlying file object.
     */
    private final RandomAccessFile file;
    private FileLock lock;

    private final int id;
    private final String name;

    /**
     * Mode for creating new container objects. This should be configurable.
     */
    private final String createMode;
    private static final String CREATE_MODE = "storage.createMode";
    private static final String defaultCreateMode = "rws";

    /**
     * Mode for opening existing container objects. This should be configurable.
     */
    private final String openMode;
    private static final String OPEN_MODE = "storage.openMode";
    private static final String defaultOpenMode = "rws";

    /**
     * Values can be noforce, force.true or force.false.
     */
    private final String flushMode;
    /**
     * Default flush mode
     */
    private static final String FLUSH_MODE = "storage.flushMode";
    private static final String DEFAULT_FLUSH_MODE = "force.true";

    static final class Header {
        final int id;
        BallotNum lastTried;
        BallotNum lastBallot;
        long commitNum;

        public static int size() {
            return Integer.BYTES +
                    BallotNum.size() * 2 +
                    Long.BYTES;
        }

        public void store(ByteBuffer bb) {
            bb.putInt(id);
            lastTried.store(bb);
            lastBallot.store(bb);
            bb.putLong(commitNum);
        }

        public Header(ByteBuffer bb) {
            id = bb.getInt();
            lastTried = new BallotNum(bb);
            lastBallot = new BallotNum(bb);
            commitNum = bb.getLong();
        }

        public Header(int id, BallotNum lastTried, BallotNum lastBallot, long commitNum) {
            this.id = id;
            this.lastTried = lastTried;
            this.lastBallot = lastBallot;
            this.commitNum = commitNum;
        }
    }

    Header header;

    static final byte VALUE_UNINITIALISED = 0;
    static final byte VALUE_COMMITTED = 42;
    static final byte VALUE_IN_BALLOT = 24;

    static final class Value {
        final byte status;
        final long value;
        final BallotNum maxVBal;

        public static int size() {
            return Byte.BYTES +
                    BallotNum.size() +
                    Long.BYTES;
        }

        public void store(ByteBuffer bb) {
            bb.put(status);
            bb.putLong(value);
            maxVBal.store(bb);
        }

        public Value(ByteBuffer bb, int id) {
            status = bb.get();
            if (status == VALUE_UNINITIALISED) {
                maxVBal = new BallotNum(-1, id);
                value = 0;
            }
            else {
                value = bb.getLong();
                maxVBal = new BallotNum(bb);
            }
        }

        public Value(byte status, BallotNum maxVBal, long value) {
            this.status = status;
            this.maxVBal = maxVBal;
            this.value = value;
        }
    }

    private LedgerImpl(int id, RandomAccessFile file, String name, String flushMode) {
        this.id = id;
        this.file = file;
        this.name = name;
        this.flushMode = flushMode;
        this.createMode = defaultCreateMode;
        this.openMode = defaultOpenMode;
    }

    /**
     * Checks the existence of the base path. Optionally creates the base path.
     */
    public static void checkBasePath(String basePath, boolean create) {
        File file = new File(basePath);
        if (!file.exists()) {
            if (!create) {
                log.error(LedgerImpl.class, "checkBasePath", "Directory specified by " + basePath + " does not exist");
            }
            if (log.isDebugEnabled()) {
                log.debug(LedgerImpl.class, "checkBasePath", "Creating base path " + basePath);
            }
            if (!file.mkdirs()) {
                throw new LedgerException("Failed to create " + basePath);
            }
        }
        if (!file.isDirectory() || !file.canRead() || !file.canWrite()) {
            throw new LedgerException("Failed to verify " + basePath);
        }
    }

    /**
     * Converts a logical name to a file name that. Optionally creates the path
     * to the file.
     */
    private static String getFileName(String basePath, String name, boolean checkParent) {
        File file = new File(basePath, name);
        String s = file.getPath();
        if (checkParent) {
            File parentFile = file.getParentFile();
            if (parentFile.exists()) {
                if (!parentFile.isDirectory() || !parentFile.canWrite()
                        || !parentFile.canRead()) {
                    throw new LedgerException("Parent path does not exist");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(LedgerImpl.class, "getFileName", "Creating path " + parentFile.getPath());
                }
                if (!parentFile.mkdirs()) {
                    throw new LedgerException("Failed to create path " + parentFile
                                            .getPath());
                }
            }
        }
        return s;
    }

    /**
     * Creates a new File based Storage Container object. If a container of the
     * same name already exists, it is over-written. By default the container is
     * opened in read/write mode.
     */
    public static Ledger createIfNotExisting(String basePath, String logicalName, int id) {
        log.info(LedgerImpl.class, "createIfNotExisting", "Creating Ledger " + logicalName + " in " + basePath);
        checkBasePath(basePath, true);
        String name = getFileName(basePath, logicalName, true);
        RandomAccessFile rafile;
        File file = new File(name);
        String createMode = defaultCreateMode;
        try {
            // Create the file atomically.
            boolean created = file.createNewFile();
            if (!created) {
                throw new LedgerException("Failed to create " + name);
            }
            rafile = new RandomAccessFile(name, createMode);
        } catch (IOException e) {
            throw new LedgerException("Error creating " + name, e);
        }
        LedgerImpl ledger = new LedgerImpl(id, rafile, logicalName, DEFAULT_FLUSH_MODE);
        ledger.header = initialHeader(id);
        ledger.writeHeader();
        return ledger;
    }

    /**
     * <p>
     * Opens an existing File based Storage Container object. If a container of
     * the specified name does not exist, an Exception is thrown. By default the
     * container is opened in read/write mode.
     * </p>
     */
    public static Ledger open(String basePath, String logicalName, int id)
            throws LedgerException {
        log.info(LedgerImpl.class, "open", "Opening Ledger " + logicalName);
        checkBasePath(basePath, false);
        String name = getFileName(basePath, logicalName, false);
        RandomAccessFile rafile = null;
        File file = new File(name);
        try {
            if (!file.exists() || !file.isFile() || !file.canRead()
                    || !file.canWrite()) {
                throw new LedgerException("Ledger " + name + " not found");
            }
            String openMode = defaultOpenMode;
            rafile = new RandomAccessFile(name, openMode);
        } catch (FileNotFoundException e) {
            throw new LedgerException("Ledger " + name + " not found");
        }
        return new LedgerImpl(id, rafile, logicalName, DEFAULT_FLUSH_MODE).readHeader(id);
    }

    public static void delete(String basePath, String logicalName) throws LedgerException {
        checkBasePath(basePath, false);
        String name = getFileName(basePath, logicalName, false);
        File file = new File(name);
        if (file.exists()) {
            if (file.isFile()) {
                if (!file.delete()) {
                    throw new LedgerException("Failed to delete " + name);
                }
            } else {
                throw new LedgerException("Ledger " + name + " not found");
            }
        }
    }

    /* (non-Javadoc)
     * @see org.simpledbm.rss.api.st.StorageContainerFactory#exists(java.lang.String)
     */
    public static boolean exists(String basePath, String logicalName) {
        checkBasePath(basePath, false);
        String name = getFileName(basePath, logicalName, false);
        File file = new File(name);
        return file.exists();
    }

    static Header initialHeader(int id) {
        return new Header(id,
                new BallotNum(-1, id),
                new BallotNum(-1, id),
                -1);
    }

    LedgerImpl readHeader(int id) {
        if (header != null)
            throw new IllegalStateException();
        byte[] data = new byte[Header.size()];
        int n = read(0, data, 0, data.length);
        if (n != data.length)
            throw new LedgerException("Failed to read header from ledger " + name);
        header = new Header(ByteBuffer.wrap(data));
        if (header.id != id)
            throw new LedgerException("Invalid Ledger - id is " + header.id + " expected " + id);
        return this;
    }

    void writeHeader() {
        byte[] data = new byte[PAGE_SIZE];
        header.store(ByteBuffer.wrap(data));
        write(0, data, 0, data.length);
        flush();
    }

    /**
     * Checks if the file is available for reading and writing.
     *
     * @throws IllegalStateException Thrown if the file has been closed.
     */
    private void isValid() {
        if (file == null || !file.getChannel().isOpen()) {
            throw new IllegalStateException("Ledger " + name + " is not open");
        }
    }

    public final synchronized void write(long position,
                                         byte[] data,
                                         int offset,
                                         int length) {
        isValid();
        try {
            file.seek(position);
            file.write(data, offset, length);
        } catch (IOException e) {
            throw new LedgerException("Failure when writing to ledger " + name, e);
        }
    }

    public final synchronized int read(long position,
                                       byte[] data,
                                       int offset,
                                       int length) {
        isValid();
        int n = 0;
        try {
            file.seek(position);
            n = file.read(data, offset, length);
        } catch (IOException e) {
            throw new LedgerException("Failure when reading from ledger " + name, e);
        }
        return n;
    }

    public final synchronized void flush() {
        isValid();
        try {
            // FIXME hard coded values
            if ("force.true".equals(flushMode)) {
                file.getChannel().force(true);
            } else if ("force.false".equals(flushMode)) {
                file.getChannel().force(false);
            }
        } catch (IOException e) {
            throw new LedgerException("Failure when flushing ledger " + name + " to disk", e);
        }
    }

    public final synchronized void close() throws LedgerException {
        isValid();
        try {
            file.close();
        } catch (IOException e) {
            throw new LedgerException("Error closing ledger " + name, e);
        }
        log.info(LedgerImpl.class, "close", "Ledger " + name + " closed");
    }

    public final synchronized void lock() {
        isValid();
        if (lock != null) {
            throw new LedgerException("Ledger is aleady locked " + name);
        }
        try {
            FileChannel channel = file.getChannel();
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                // ignore this error
            }
            if (lock == null) {
                throw new LedgerException("Failed to lock ledger " + name);
            }
        } catch (IOException e) {
            throw new LedgerException("Failed to lock ledger " + name);
        }
    }

    public final synchronized void unlock() {
        isValid();
        if (lock == null) {
            throw new LedgerException("Ledger is not locked " + name);
        }
        try {
            lock.release();
            lock = null;
        } catch (IOException e) {
            throw new LedgerException("Failed to release lock on ledger " + name);
        }
    }

    long getOffsetOf(long decreeNum) {
        if (decreeNum < 0)
            throw new IllegalArgumentException("decree number cannot be < 0");
        return PAGE_SIZE+decreeNum*(Value.size());
    }

    @Override
    public void setOutcome(long decreeNum, long data) {
        // Since value is committed ballot must be set to neg INF i.e. null
        setValue(decreeNum, new Value(VALUE_COMMITTED, new BallotNum(-1,id), data));
        // This is not efficient as we don't have caching yet
        // We want to ensure that commitNum tracks the lowest consecutive committed decree
        // If we see the next decree is committed, we increment it but we need to also see
        // if we can advance even more
        if (header.commitNum+1 == decreeNum) {
            header.commitNum = decreeNum;
            for (long i = header.commitNum+1; i <= getLastDnum(); i++) {
                Value v = getValue(i);
                if (v.status == VALUE_COMMITTED) {
                    header.commitNum++;
                }
                else {
                    break;
                }
            }
            writeHeader();
        }
    }

    public void setValue(long decreeNum, Value v) {
        long offset = getOffsetOf(decreeNum);
        byte[] bytes = new byte[Value.size()];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        v.store(bb);
        write(offset, bytes, 0, bytes.length);
        flush();
    }

    public Value getValue(long decreeNum) {
        long offset = getOffsetOf(decreeNum);
        try {
            long length = file.length();
            if (length < offset+Value.size())
                return new Value(VALUE_UNINITIALISED, new BallotNum(-1,id), 0);
        }
        catch (IOException e) {
            throw new LedgerException("Cannot get length of ledger " + name, e);
        }
        byte[] bytes = new byte[Value.size()];
        read(offset, bytes, 0, bytes.length);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new Value(bb, id);
    }

    @Override
    public Long getOutcome(long decreeNum) {
        Value v = getValue(decreeNum);
        if (v.status != VALUE_COMMITTED)
            return null;
        return getValue(decreeNum).value;
    }

    @Override
    public void setLastTried(BallotNum ballot) {
        header.lastTried = ballot;
        writeHeader();
    }

    @Override
    public BallotNum getLastTried() {
        return header.lastTried;
    }

    @Override
    public void setMaxVBal(BallotNum ballot, long dnum, long value) {
        if (getOutcome(dnum) != null)
            throw new IllegalArgumentException("Outcome already stored at decree number " + dnum);
        setValue(dnum,new Value(VALUE_IN_BALLOT, ballot, value));
    }

    @Override
    public BallotNum getMaxVBal(long dnum) {
        return getValue(dnum).maxVBal;
    }

    @Override
    public Decree getMaxVal(long dnum) {
        return new Decree(dnum, getValue(dnum).value);
    }

    @Override
    public void setMaxBal(BallotNum ballot) {
        header.lastBallot = ballot;
        writeHeader();
    }

    @Override
    public BallotNum getMaxBal() {
        return header.lastBallot;
    }

    @Override
    public long getCommitNum() {
        return header.commitNum;
    }

    long getLastDnum() {
        try {
            long length = file.length();
            length -= PAGE_SIZE;
            if (length <= 0)
                return 0;
            return length/Value.size();
        }
        catch (IOException e) {
            throw new LedgerException("Cannot get length of ledger " + name, e);
        }
    }

    @Override
    public List<BallotedDecree> getUndecidedBallots() {
        List<BallotedDecree> ballots = new ArrayList<>();
        long cnum = getCommitNum();
        long dnum = getLastDnum();

        for (long i = cnum+1; i <= dnum; i++) {
            Value v = getValue(i);
            if (v != null && v.status == VALUE_IN_BALLOT) {
                ballots.add(new BallotedDecree(v.maxVBal, new Decree(i,v.value)));
            }
        }
        return ballots;
    }
}
