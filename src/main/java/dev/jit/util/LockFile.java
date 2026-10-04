package dev.jit.util;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Git's way of safely replacing a file (refs, the index, ...):
 *   1. create "<file>.lock" exclusively: if it already exists, someone else is writing, so stop
 *   2. write the new content into the lock file
 *   3. commit(): rename the lock over the real file. The rename is atomic, so readers see the old
 *      content or the new, never half of each.
 * Closing without commit() throws the lock away and leaves the real file untouched.
 *
 *   try (LockFile lock = LockFile.acquire(path)) { lock.write(bytes); lock.commit(); }
 */
public final class LockFile implements AutoCloseable {
    private final Path target;
    private final Path lock;
    private boolean done;

    private LockFile(Path target, Path lock) {
        this.target = target;
        this.lock = lock;
    }

    public static LockFile acquire(Path target) throws IOException {
        Path lock = target.resolveSibling(target.getFileName() + ".lock");
        Files.createDirectories(target.getParent());
        try {
            Files.createFile(lock);                                  // atomic "create only if absent"
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("unable to lock " + target + ": " + lock + " exists.\n"
                    + "Another jit process may be running; if not, delete that file.");
        }
        return new LockFile(target, lock);
    }

    public void write(byte[] content) throws IOException { Files.write(lock, content); }

    public void commit() throws IOException {
        Files.move(lock, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        done = true;
    }

    @Override public void close() throws IOException {
        if (!done) Files.deleteIfExists(lock);                       // rollback: we never touched the target
    }
}
