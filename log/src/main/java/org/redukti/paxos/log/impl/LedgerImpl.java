package org.redukti.paxos.log.impl;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.log.api.LedgerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// Ledger is made up of two files
// status files stores the basic paxos data for a specific decree number, upto max of 100
//   decree numbers can be handled concurrently
// log file stores the outcome of each decree number

public class LedgerImpl implements Ledger {

    static final Logger log = LoggerFactory.getLogger(LedgerImpl.class);

    /**
     * Size of pages in log file (we extend file by page at a time)
     */
    static final int PAGE_SIZE = 8 * 1024;
    /**
     * The decree value is just a long for now, and an extra byte
     * to help us decide if value is set or not (magic value 42)
     */
    static final int VALUE_SIZE = Long.BYTES + Byte.BYTES;
    public static final int MAGIC = 42;

    /**
     * Log file is where decrees are stored
     */
    private final RandomAccessFile logFile;
    /**
     * Status file is where we store the ballot data for
     * a particular decree number; this file holds statuses
     * for last N decree numbers
     */
    private final RandomAccessFile statusFile;

    /**
     * Process id of the owner
     */
    private final int id;
    /**
     * Logical name, actual name is created by adding an extension
     */
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

    /**
     * Values stored for each decree as ballots take
     * place, taken straight from paxos paper
     */
    static final class DecreeBallotStatus {
        /**
         * Decree number whose ballot status is being recorded
         */
        long decreeNum;
        /**
         * the number of the last ballot that p tried to begin for the given decreeNum
         */
        BallotNum lastTried;
        /**
         * the number of the last ballot in which p voted, for the given decreeNum
         */
        BallotNum prevBallot;
        /**
         * The decree for which p last voted, for given decreeNum
         */
        Decree prevDecree;
        /**
         * The number of the last ballot in which p agreed to participate, for the given decreeNum
         */
        BallotNum lastBallot;

        public static int size() {
            return Long.BYTES +
                    BallotNum.size() * 3 +
                    Decree.size();
        }

        public void store(ByteBuffer bb) {
            bb.putLong(decreeNum);
            lastTried.store(bb);
            prevBallot.store(bb);
            prevDecree.store(bb);
            lastBallot.store(bb);
        }

        public DecreeBallotStatus(ByteBuffer bb) {
            decreeNum = bb.getLong();
            lastTried = new BallotNum(bb);
            prevBallot = new BallotNum(bb);
            prevDecree = new Decree(bb);
            lastBallot = new BallotNum(bb);
        }

        public DecreeBallotStatus(long decreeNum, BallotNum lastTried, BallotNum prevBallot, Decree prevDecree, BallotNum lastBallot) {
            this.decreeNum = decreeNum;
            this.lastTried = lastTried;
            this.prevBallot = prevBallot;
            this.prevDecree = prevDecree;
            this.lastBallot = lastBallot;
        }

        public DecreeBallotStatus(long decreeNum, int id) {
            this(decreeNum,
                    new BallotNum(-1, id),
                    new BallotNum(-1, id),
                    new Decree(-1, 0),
                    new BallotNum(-1, id));
        }
    }

    /**
     * Cached status values for last 100 decree numbers
     */
    DecreeBallotStatus[] decreeBallotStatus = new DecreeBallotStatus[100];
    /**
     * Mapping from decree number to offset in decreeBallotStatus
     */
    Map<Long, Integer> decreeNumMap = new HashMap<>();

    private LedgerImpl(int id, RandomAccessFile logFile, RandomAccessFile statusFile, String name, String flushMode) {
        this.id = id;
        this.logFile = logFile;
        this.statusFile = statusFile;
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
                log.error("Directory specified by {0} does not exist", basePath);
            }
            if (log.isDebugEnabled()) {
                log.debug("Creating base path " + basePath);
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
                    log.debug("Creating path " + parentFile.getPath());
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
     * Creates a new Ledger. Will fail if ledger already exists
     */
    public static Ledger createIfNotExisting(String basePath, String logicalName, int id) {
        log.info("Creating Ledger " + logicalName + " in " + basePath);
        checkBasePath(basePath, true);
        String name = getFileName(basePath, logicalName, true);
        String statusFile = name + ".status";
        String logFile = name + ".log";
        RandomAccessFile raStatusFile = createNamedFile(statusFile);
        RandomAccessFile raLogFile = createNamedFile(logFile);
        LedgerImpl ledger = new LedgerImpl(id, raLogFile, raStatusFile, logicalName, DEFAULT_FLUSH_MODE);
        return ledger.initializeStatuses().writeAllStatuses();
    }

    private static RandomAccessFile createNamedFile(String name) {
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
        return rafile;
    }

    /**
     * Opens a ledger, will fail if ledger does not exist
     */
    public static Ledger open(String basePath, String logicalName, int id)
            throws LedgerException {
        log.info("Opening Ledger " + logicalName);
        checkBasePath(basePath, false);
        String name = getFileName(basePath, logicalName, false);
        String statusFile = name + ".status";
        String logFile = name + ".log";
        RandomAccessFile raStatusFile = openNamedFile(statusFile);
        RandomAccessFile raLogFile = openNamedFile(logFile);
        return new LedgerImpl(id, raLogFile, raStatusFile, logicalName, DEFAULT_FLUSH_MODE).readAllStatuses();
    }

    private static RandomAccessFile openNamedFile(String name) {
        RandomAccessFile rafile;
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
        return rafile;
    }

    @Override
    public void close() {
        close(statusFile);
        close(logFile);
        log.info("Ledger " + name + " closed");
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

    public static boolean exists(String basePath, String logicalName) {
        checkBasePath(basePath, false);
        String name = getFileName(basePath, logicalName, false);
        File file = new File(name);
        return file.exists();
    }

    private static void deleteRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files == null)
                return;
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Deleting " + file.getAbsolutePath());
                    }
                    if (!file.delete()) {
                        throw new LedgerException("Failed to delete " + file.getAbsolutePath());
                    }
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(" Deleting " + dir.getAbsolutePath());
        }
        if (!dir.delete()) {
            throw new LedgerException("Failed to delete " + dir.getAbsolutePath());
        }
    }

    public static void drop(String basePath) {
        checkBasePath(basePath, false);
        File file = new File(basePath);
        deleteRecursively(file);
    }

    /**
     * Initializes all the statuses to null status
     */
    LedgerImpl initializeStatuses() {
        DecreeBallotStatus status = new DecreeBallotStatus(-1, id);
        Arrays.fill(decreeBallotStatus, status);
        return this;
    }

    /**
     * Reads all the statuses into the cache
     */
    LedgerImpl readAllStatuses() {
        decreeNumMap.clear();
        final int size = DecreeBallotStatus.size();
        byte[] data = new byte[size];
        ByteBuffer bb = ByteBuffer.wrap(data);
        long position = 0;
        for (int i = 0; i < decreeBallotStatus.length; i++) {
            int n = read(statusFile, position, data, 0, data.length);
            if (n != data.length)
                throw new LedgerException("Failed to read status record " + i + " from ledger " + name);
            decreeBallotStatus[i] = new DecreeBallotStatus(bb.clear());
            if (decreeBallotStatus[i].decreeNum >= 0)
                decreeNumMap.put(decreeBallotStatus[i].decreeNum, i);
            position += size;
        }
        return this;
    }

    /**
     * Writes all status to disk
     */
    LedgerImpl writeAllStatuses() {
        final int size = DecreeBallotStatus.size();
        byte[] data = new byte[size];
        ByteBuffer bb = ByteBuffer.wrap(data);
        long position = 0;
        for (DecreeBallotStatus ballotStatus : decreeBallotStatus) {
            bb.clear();
            ballotStatus.store(bb);
            write(statusFile, position, data, 0, data.length);
            position += size;
        }
        flush(statusFile);
        return this;
    }

    /**
     * Checks if the file is available for reading and writing.
     *
     * @throws IllegalStateException Thrown if the file has been closed.
     */
    private void isValid(RandomAccessFile file) {
        if (file == null || !file.getChannel().isOpen()) {
            throw new IllegalStateException("Ledger " + name + " is not open");
        }
    }

    void write(RandomAccessFile file,
               long position,
               byte[] data,
               int offset,
               int length) {
        isValid(file);
        try {
            file.seek(position);
            file.write(data, offset, length);
        } catch (IOException e) {
            throw new LedgerException("Failure when writing to ledger " + name, e);
        }
    }

    int read(RandomAccessFile file,
             long position,
             byte[] data,
             int offset,
             int length) {
        isValid(file);
        int n;
        try {
            file.seek(position);
            n = file.read(data, offset, length);
        } catch (IOException e) {
            throw new LedgerException("Failure when reading from ledger " + name, e);
        }
        return n;
    }

    void flush(RandomAccessFile file) {
        isValid(file);
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

    void close(RandomAccessFile file) throws LedgerException {
        isValid(file);
        try {
            file.close();
        } catch (IOException e) {
            log.error("Error closing ledger " + name, e);
        }
    }

    long getOffsetOf(long decreeNum) {
        if (decreeNum < 0)
            throw new IllegalArgumentException("decree number cannot be < 0");
        return decreeNum * (VALUE_SIZE);
    }

    @Override
    public synchronized void setOutcome(long decreeNum, long data) {
        long offset = getOffsetOf(decreeNum);
        extend(offset);
        byte[] bytes = new byte[VALUE_SIZE];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.put((byte) MAGIC);
        bb.putLong(data);
        write(logFile, offset, bytes, 0, bytes.length);
        flush(logFile);
    }

    void extend(long offset) {
        try {
            long length = logFile.length();
            if (offset <= length)
                return;
            byte[] chunk = new byte[PAGE_SIZE];
            long initial = length % PAGE_SIZE;
            long start = length;
            if (initial > 0) {
                write(logFile, start, chunk, 0, (int) initial);
                start += initial;
            }
            while (start < offset) {
                write(logFile, start, chunk, 0, chunk.length);
                start += chunk.length;
            }
        } catch (IOException e) {
            throw new LedgerException("Error extending ledger " + name);
        }
    }

    @Override
    public synchronized Long getOutcome(long decreeNum) {
        long offset = getOffsetOf(decreeNum);
        try {
            long length = logFile.length();
            if (length < offset + VALUE_SIZE)
                return null;
        } catch (IOException e) {
            throw new LedgerException("Cannot get length of ledger " + name, e);
        }
        byte[] bytes = new byte[VALUE_SIZE];
        read(logFile, offset, bytes, 0, bytes.length);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        byte check = bb.get();
        if (check != 42)
            return null;
        return bb.getLong();
    }

    int findUnusedSlot() {
        for (int i = 0; i < decreeBallotStatus.length; i++)
            if (decreeBallotStatus[i].decreeNum == -1)
                return i;
        return -1;
    }

    int findOldestSlot() {
        long decreeNum = Long.MAX_VALUE;
        int offset = -1;
        for (int i = 0; i < decreeBallotStatus.length; i++) {
            if (decreeBallotStatus[i].decreeNum < decreeNum) {
                decreeNum = decreeBallotStatus[i].decreeNum;
                offset = i;
            }
        }
        assert offset >= 0;
        return offset;
    }

    int getHeaderOffset(long decreeNum) {
        Integer offset = decreeNumMap.get(decreeNum);
        if (offset == null) {
            offset = findUnusedSlot();
            if (offset >= 0) {
                initStatusRecord(decreeNum, offset);
                return offset;
            }
            offset = findOldestSlot();
            if (offset >= 0) {
                decreeNumMap.remove(decreeBallotStatus[offset].decreeNum);
                initStatusRecord(decreeNum, offset);
                return offset;
            }
        }
        return offset;
    }

    void initStatusRecord(long decreeNum, Integer offset) {
        decreeNumMap.put(decreeNum, offset);
        decreeBallotStatus[offset] = new DecreeBallotStatus(decreeNum, id);
        writeStatus(offset);
    }

    void writeStatus(int i) {
        final int size = DecreeBallotStatus.size();
        byte[] data = new byte[size];
        ByteBuffer bb = ByteBuffer.wrap(data);
        long position = (long) i * size;
        decreeBallotStatus[i].store(bb);
        write(statusFile, position, data, 0, data.length);
        flush(statusFile);
    }

    @Override
    public synchronized void setLastTried(long decreeNum, BallotNum ballot) {
        int offset = getHeaderOffset(decreeNum);
        decreeBallotStatus[offset].lastTried = ballot;
        writeStatus(offset);
    }

    @Override
    public synchronized BallotNum getLastTried(long decreeNum) {
        int offset = getHeaderOffset(decreeNum);
        return decreeBallotStatus[offset].lastTried;
    }

    @Override
    public synchronized void setPrevBallot(long decreeNum, BallotNum ballot) {
        int offset = getHeaderOffset(decreeNum);
        decreeBallotStatus[offset].prevBallot = ballot;
        writeStatus(offset);
    }

    @Override
    public synchronized BallotNum getPrevBallot(long decreeNum) {
        int offset = getHeaderOffset(decreeNum);
        return decreeBallotStatus[offset].prevBallot;
    }

    @Override
    public synchronized void setPrevDec(long decreeNum, Decree decree) {
        int offset = getHeaderOffset(decreeNum);
        decreeBallotStatus[offset].prevDecree = decree;
        writeStatus(offset);
    }

    @Override
    public synchronized Decree getPrevDec(long decreeNum) {
        int offset = getHeaderOffset(decreeNum);
        return decreeBallotStatus[offset].prevDecree;
    }

    @Override
    public synchronized void setNextBallot(long decreeNum, BallotNum ballot) {
        int offset = getHeaderOffset(decreeNum);
        decreeBallotStatus[offset].lastBallot = ballot;
        writeStatus(offset);
    }

    @Override
    public synchronized BallotNum getNextBallot(long decreeNum) {
        int offset = getHeaderOffset(decreeNum);
        return decreeBallotStatus[offset].lastBallot;
    }

    @Override
    public long getMaxDecreeNum() {
        return 0;
    }
}
