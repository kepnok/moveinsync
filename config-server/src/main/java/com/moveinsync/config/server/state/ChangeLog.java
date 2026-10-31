package com.moveinsync.config.server.state;

import com.moveinsync.config.core.model.ChangeSet;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class ChangeLog {
    private final int capacity;
    private final ArrayDeque<ChangeSet> buffer;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long headVersion = 0L;

    public ChangeLog() {
        this(5000);
    }

    public ChangeLog(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    public void load(List<ChangeSet> recent) {
        lock.writeLock().lock();
        try {
            buffer.clear();
            if (recent != null) {
                for (ChangeSet cs : recent) {
                    appendInternal(cs);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void append(ChangeSet cs) {
        lock.writeLock().lock();
        try {
            appendInternal(cs);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void appendInternal(ChangeSet cs) {
        if (buffer.size() >= capacity) {
            buffer.removeFirst();
        }
        buffer.addLast(cs);
        headVersion = cs.version();
    }

    public DeltaResult since(String tenantId, long sinceVersion) {
        lock.readLock().lock();
        try {
            long currentHead = headVersion;
            if (sinceVersion == currentHead) {
                return new DeltaResult.Delta(sinceVersion, currentHead, Collections.emptyList());
            }

            if (buffer.isEmpty()) {
                return new DeltaResult.FullRequired(currentHead);
            }

            long oldestRetained = buffer.getFirst().version();
            // If sinceVersion is older than oldestRetained - 1, we cannot build a complete contiguous delta
            if (sinceVersion < oldestRetained - 1 || sinceVersion > currentHead) {
                return new DeltaResult.FullRequired(currentHead);
            }

            List<ChangeSet> result = new ArrayList<>();
            for (ChangeSet cs : buffer) {
                if (cs.version() > sinceVersion && cs.version() <= currentHead) {
                    ChangeSet filtered = cs.filteredForTenant(tenantId);
                    result.add(filtered);
                }
            }

            return new DeltaResult.Delta(sinceVersion, currentHead, Collections.unmodifiableList(result));
        } finally {
            lock.readLock().unlock();
        }
    }

    public long headVersion() {
        return headVersion;
    }

    public int size() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}

