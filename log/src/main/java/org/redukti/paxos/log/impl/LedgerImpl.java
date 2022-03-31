package org.redukti.paxos.log.impl;

import org.redukti.paxos.log.api.BallotNum;
import org.redukti.paxos.log.api.Decree;
import org.redukti.paxos.log.api.Ledger;
import org.redukti.paxos.log.api.LedgerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class LedgerImpl implements Ledger {

    final static Logger log = LoggerFactory.getLogger(LedgerImpl.class);

    /**
     * The underlying file object.
     */
    private final RandomAccessFile file;

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

    public LedgerImpl(RandomAccessFile file, String name, String flushMode) {
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
     * Creates a new File based Storage Container object. If a container of the
     * same name already exists, it is over-written. By default the container is
     * opened in read/write mode.
     */
    public static final Ledger createIfNotExisting(String basePath, String logicalName) {
        if (log.isDebugEnabled()) {
            log.debug("Creating Ledger " + logicalName);
        }
        checkBasePath(basePath, true);
        String name = getFileName(basePath, logicalName, true);
        RandomAccessFile rafile;
        File file = new File(name);
        String createMode = defaultCreateMode;
        try {
            // Create the file atomically.
            if (!file.createNewFile()) {
                throw new LedgerException("Failed to create " + name);
            }
            rafile = new RandomAccessFile(name, createMode);
        } catch (IOException e) {
            throw new LedgerException("Error creating " + name, e);
        }
        return new LedgerImpl(rafile, logicalName, DEFAULT_FLUSH_MODE);
    }

    @Override
    public void setOutcome(long decreeNum, byte[] data) {

    }

    @Override
    public byte[] getOutcome(long decreeNum) {
        return new byte[0];
    }

    @Override
    public void setLastTried(BallotNum ballot) {

    }

    @Override
    public BallotNum getLastTried() {
        return null;
    }

    @Override
    public void setPrevBallot(BallotNum ballot) {

    }

    @Override
    public BallotNum getPrevBallot() {
        return null;
    }

    @Override
    public void setPrevDec(Decree decree) {

    }

    @Override
    public Decree getPrevDec() {
        return null;
    }

    @Override
    public void setLastBallot(BallotNum ballot) {

    }

    @Override
    public BallotNum getLastBallot() {
        return null;
    }
}
