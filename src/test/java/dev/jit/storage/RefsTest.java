package dev.jit.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RefsTest {

    private static final String A = "d0d854d9f93be5112f739843d7256f13116db42d";
    private static final String B = "6ce492f2a7e2e682b89e979bca44a22b28e5eaa9";

    @TempDir Path jit;
    Refs refs;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(jit.resolve("HEAD"), "ref: refs/heads/main\n");   // what jit init writes
        refs = new Refs(jit);
    }

    @Test
    void unbornBranchReadsAsEmpty() throws Exception {
        assertEquals(Optional.empty(), refs.read("HEAD"));                 // fresh repo: main has no commits yet
        assertEquals(Optional.of("refs/heads/main"), refs.readSymbolic("HEAD"));
    }

    @Test
    void updatingHeadMovesTheBranch() throws Exception {
        refs.update("HEAD", A, null);
        assertEquals(A + "\n", Files.readString(jit.resolve("refs/heads/main")));
        assertEquals("ref: refs/heads/main\n", Files.readString(jit.resolve("HEAD")));   // HEAD itself unchanged
        assertEquals(Optional.of(A), refs.read("HEAD"));
    }

    @Test
    void detachedHeadHoldsAnId() throws Exception {
        Files.writeString(jit.resolve("HEAD"), A + "\n");
        assertEquals(Optional.empty(), refs.readSymbolic("HEAD"));
        refs.update("HEAD", B, null);
        assertEquals(Optional.of(B), refs.read("HEAD"));
        assertFalse(Files.exists(jit.resolve("refs/heads/main")));
    }

    @Test
    void expectedOldValueIsChecked() throws Exception {
        refs.update("refs/heads/main", A, Refs.ZERO_ID);                    // ok: didn't exist
        assertThrows(IllegalStateException.class, () -> refs.update("refs/heads/main", B, Refs.ZERO_ID));
        assertThrows(IllegalStateException.class, () -> refs.update("refs/heads/main", B, B));
        refs.update("refs/heads/main", B, A);                               // ok: still at A
        assertEquals(Optional.of(B), refs.read("refs/heads/main"));
        assertFalse(Files.exists(jit.resolve("refs/heads/main.lock")));     // lock released even after failures
    }

    @Test
    void existingLockBlocksUpdates() throws Exception {
        Files.createDirectories(jit.resolve("refs/heads"));
        Files.createFile(jit.resolve("refs/heads/main.lock"));              // another process "holding" it
        assertThrows(IllegalStateException.class, () -> refs.update("HEAD", A, null));
        assertTrue(Files.exists(jit.resolve("refs/heads/main.lock")));      // we must not delete someone else's lock
    }

    @Test
    void setSymbolicSwitchesBranch() throws Exception {
        refs.update("refs/heads/feature", B, null);
        refs.setSymbolic("HEAD", "refs/heads/feature");
        assertEquals(Optional.of(B), refs.read("HEAD"));
        assertThrows(IllegalArgumentException.class, () -> refs.setSymbolic("HEAD", "config"));
    }

    @Test
    void deleteRemovesRefAndEmptyFolders() throws Exception {
        refs.update("refs/heads/feature/login", A, null);
        refs.delete("refs/heads/feature/login", A);
        assertFalse(Files.exists(jit.resolve("refs/heads/feature")));
        assertTrue(Files.isDirectory(jit.resolve("refs/heads")));
        refs.update("refs/heads/feature", A, null);                         // the name is free again
    }

    @Test
    void symbolicLoopIsAnError() throws Exception {
        Files.createDirectories(jit.resolve("refs/heads"));
        Files.writeString(jit.resolve("refs/heads/a"), "ref: refs/heads/b\n");
        Files.writeString(jit.resolve("refs/heads/b"), "ref: refs/heads/a\n");
        assertThrows(IllegalStateException.class, () -> refs.read("refs/heads/a"));
    }

    @Test
    void validatesNames() {
        assertTrue(Refs.isValidName("HEAD"));
        assertTrue(Refs.isValidName("refs/heads/feature/login"));
        for (String bad : new String[] {"main", "config", "refs/heads/../../config", "refs/heads/a b",
                "refs/heads/x.lock", "refs/heads/", "refs/heads/.hidden", "refs/heads/a~1", "refs/heads/a^", "refs/heads/a:b"})
            assertFalse(Refs.isValidName(bad), bad);
    }
}
