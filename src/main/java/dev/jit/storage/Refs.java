package dev.jit.storage;

import dev.jit.util.Hashing;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;

/**
 * Named pointers to commits. Each ref is a tiny text file under .jit:
 *   refs/heads/main   "d0d854d9...\n"            a branch: points at a commit id
 *   HEAD              "ref: refs/heads/main\n"   a symbolic ref: points at another ref
 * HEAD normally points at a branch; "detached HEAD" just means it holds an id directly.
 */
public final class Refs {
    public static final String ZERO_ID = "0".repeat(40);   // as an expected old value: "must not exist yet"
    private static final String SYMREF_PREFIX = "ref: ";
    private static final int MAX_DEPTH = 5;                  // stop ref loops like A -> B -> A

    private final Path jitDir;

    public Refs(Path jitDir) { this.jitDir = jitDir; }

    /** The id a ref ends up at, following symbolic refs. Empty if it doesn't exist (e.g. a branch with no commits yet). */
    public Optional<String> read(String name) throws IOException {
        Optional<String> content = readRaw(deref(name));
        if (content.isEmpty()) return Optional.empty();
        String id = content.get();
        if (!Hashing.isFullHex(id)) throw new IllegalStateException("corrupt ref " + name + ": " + id);
        return Optional.of(id);
    }

    /** Where a symbolic ref points ("refs/heads/main"), or empty if it holds an id directly. */
    public Optional<String> readSymbolic(String name) throws IOException {
        return readRaw(name).filter(s -> s.startsWith(SYMREF_PREFIX)).map(s -> s.substring(SYMREF_PREFIX.length()));
    }

    /**
     * Point a ref at an id. Symbolic refs are followed: updating HEAD while it says "ref: refs/heads/main"
     * moves main, which is exactly what a commit needs.
     * expectedOld: null = don't check, ZERO_ID = must not exist, otherwise must currently hold that id.
     */
    public void update(String name, String newId, String expectedOld) throws IOException {
        if (!Hashing.isFullHex(newId) || newId.equals(ZERO_ID)) throw new IllegalArgumentException("invalid object id: " + newId);
        String target = deref(name);
        withLock(target, expectedOld, lock -> write(lock, newId + "\n"));
    }

    /** Make a symbolic ref, e.g. setSymbolic("HEAD", "refs/heads/feature") is what switching branches does. */
    public void setSymbolic(String name, String target) throws IOException {
        checkName(target);
        if (!target.startsWith("refs/")) throw new IllegalArgumentException("refusing to point " + name + " outside refs/: " + target);
        withLock(name, null, lock -> write(lock, SYMREF_PREFIX + target + "\n"));
    }

    /** Remove a ref (following symbolic refs, so deleting HEAD on a branch deletes the branch). */
    public void delete(String name, String expectedOld) throws IOException {
        String target = deref(name);
        withLock(target, expectedOld, lock -> {
            Files.deleteIfExists(path(target));
            Files.delete(lock);
        });
        // remove now-empty folders (refs/heads/feature/), else they'd block a future ref named refs/heads/feature.
        // Stops above refs/heads/, refs/tags/, ...: those stay even when empty, as init made them.
        Path refsDir = jitDir.resolve("refs");
        for (Path dir = path(target).getParent();
             dir.startsWith(refsDir) && dir.getNameCount() > refsDir.getNameCount() + 1;
             dir = dir.getParent()) {
            try { Files.delete(dir); } catch (DirectoryNotEmptyException e) { break; }
        }
    }

    /** Follow "ref: ..." links to the ref that actually holds (or will hold) an id. */
    String deref(String name) throws IOException {
        String cur = name;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            checkName(cur);
            Optional<String> content = readRaw(cur);
            if (content.isEmpty() || !content.get().startsWith(SYMREF_PREFIX)) return cur;
            cur = content.get().substring(SYMREF_PREFIX.length());
        }
        throw new IllegalStateException("symbolic ref loop at " + name);
    }

    /** Valid names: HEAD-like ("HEAD", "ORIG_HEAD") or under refs/, with git's main safety rules. */
    public static boolean isValidName(String name) {
        if (!name.matches("[A-Z_]+") && !name.startsWith("refs/")) return false;
        if (name.endsWith("/") || name.endsWith(".lock") || name.contains("..") || name.contains("//")
                || name.contains("@{") || name.matches(".*[\\x00-\\x20\\x7f~^:?*\\[\\\\].*")) return false;
        for (String part : name.split("/")) if (part.startsWith(".")) return false;
        return true;
    }

    private static void checkName(String name) {
        if (!isValidName(name)) throw new IllegalArgumentException("invalid ref name: " + name);
    }

    private Optional<String> readRaw(String name) throws IOException {
        Path p = path(name);
        if (!Files.isRegularFile(p)) return Optional.empty();
        return Optional.of(Files.readString(p).trim());
    }

    private Path path(String name) { return jitDir.resolve(name); }

    private interface LockedAction { void run(Path lock) throws IOException; }

    /**
     * Git's locking: create "<ref>.lock" exclusively (fails if another process holds it), check the old value,
     * write the new content into the lock file, then rename it over the ref. The rename is atomic, so a
     * reader sees either the old id or the new one, never half a file.
     */
    private void withLock(String name, String expectedOld, LockedAction action) throws IOException {
        Path ref = path(name);
        Path lock = ref.resolveSibling(ref.getFileName() + ".lock");
        Files.createDirectories(ref.getParent());
        try {
            Files.createFile(lock);                                  // atomic "create only if absent"
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("unable to lock " + name + ": " + lock + " exists.\n"
                    + "Another jit process may be running; if not, delete that file.");
        }
        try {
            if (expectedOld != null) {
                String actual = readRaw(name).orElse(ZERO_ID);
                if (!actual.equals(expectedOld))
                    throw new IllegalStateException("cannot update " + name + ": expected " + expectedOld + ", found " + actual);
            }
            action.run(lock);
        } finally {
            Files.deleteIfExists(lock);                              // no-op if the action already moved/deleted it
        }
    }

    private void write(Path lock, String content) throws IOException {
        try (OutputStream out = Files.newOutputStream(lock)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        Files.move(lock, lock.resolveSibling(lock.getFileName().toString().replaceFirst("\\.lock$", "")),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
