package dev.jit.objects;

import dev.jit.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommitTest {

    // reference ids from: GIT_AUTHOR_*/GIT_COMMITTER_* = "Ujjwal Rai", "ujjwal@example.com", "1700000000 +0530"
    private static final String TREE = "68aba62e560c0ebc3396e8ae9335232cd93a3f60";   // hello.txt only
    private static final String C1   = "d0d854d9f93be5112f739843d7256f13116db42d";
    private static final String C2   = "6ce492f2a7e2e682b89e979bca44a22b28e5eaa9";
    private static final Signature ME = new Signature("Ujjwal Rai", "ujjwal@example.com", 1700000000L, ZoneOffset.ofHoursMinutes(5, 30));

    @Test
    void rootCommitMatchesGit() {       // git commit-tree $TREE -m "first commit"
        Commit c = new Commit(TREE, List.of(), ME, ME, "first commit\n");
        assertEquals(C1, ObjectStore.hash(c));
    }

    @Test
    void commitWithParentMatchesGit() { // git commit-tree $TREE -p $C1 -m second -m "body para"
        Commit c = new Commit(TREE, List.of(C1), ME, ME, "second\n\nbody para\n");
        assertEquals(C2, ObjectStore.hash(c));
    }

    @Test
    void mergeWithNegativeOffsetMatchesGit() {   // GIT_AUTHOR_DATE="1700000000 -0700" git commit-tree $TREE -p $C1 -p $C2 -m merge
        Signature west = new Signature("Ujjwal Rai", "ujjwal@example.com", 1700000000L, ZoneOffset.ofHours(-7));
        Commit c = new Commit(TREE, List.of(C1, C2), west, ME, "merge\n");
        assertEquals("1e3709685d634c613338cdb5122403e84eee76fd", ObjectStore.hash(c));
    }

    @Test
    void messageIsStoredExactly() {     // printf 'from stdin no newline' | git commit-tree $TREE
        Commit c = new Commit(TREE, List.of(), ME, ME, "from stdin no newline");
        assertEquals("4ed5fb1d6124963dfcc1131890a23ee5c9d12acd", ObjectStore.hash(c));
    }

    @Test
    void parseRoundTrips() {
        Commit c = new Commit(TREE, List.of(C1, C2), ME, ME, "multi\n\nline\n\n\nmessage\n");
        assertEquals(c, Commit.parse(c.body()));
    }

    @Test
    void parseSkipsHeadersItDoesNotKnow() {
        String text = "tree " + TREE + "\n"
                + "author " + ME.format() + "\n"
                + "committer " + ME.format() + "\n"
                + "gpgsig -----BEGIN PGP SIGNATURE-----\n"
                + " abc123\n"                                  // continuation line: starts with a space
                + " -----END PGP SIGNATURE-----\n"
                + "\nsigned\n";
        Commit c = Commit.parse(text.getBytes(StandardCharsets.UTF_8));
        assertEquals(TREE, c.tree());
        assertEquals("signed\n", c.message());
    }

    @Test
    void signatureFormatsAndParses() {
        assertEquals("Ujjwal Rai <ujjwal@example.com> 1700000000 +0530", ME.format());
        assertEquals(ME, Signature.parse(ME.format()));
        assertEquals("-0700", Signature.formatOffset(ZoneOffset.ofHours(-7)));
        assertEquals("-0030", Signature.formatOffset(ZoneOffset.ofHoursMinutes(0, -30)));   // sign without whole hours
        assertEquals(ZoneOffset.ofHoursMinutes(0, -30), Signature.parseOffset("-0030"));
        assertThrows(IllegalArgumentException.class, () -> new Signature("a <b>", "x@y", 0, ZoneOffset.UTC));
    }
}
