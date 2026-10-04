package dev.jit.core;

import dev.jit.objects.Blob;
import dev.jit.objects.Commit;
import dev.jit.objects.FileMode;
import dev.jit.objects.Signature;
import dev.jit.objects.Tree;
import dev.jit.objects.TreeEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RevisionTest {

    private static final Signature ME = new Signature("Ujjwal Rai", "ujjwal@example.com", 1700000000L, ZoneOffset.UTC);

    @TempDir Path tmp;
    Repository repo;
    String blob, tree, c1, c2, side, merge;

    /**
     *   c1 <- c2 <- merge      (main, HEAD)
     *    ^          /
     *    +-- side <+           (refs/heads/side, also tag v1)
     */
    @BeforeEach
    void setUp() throws Exception {
        repo = Repository.init(tmp);
        blob  = repo.objects().write(new Blob("hello world\n".getBytes(StandardCharsets.UTF_8)));
        tree  = repo.objects().write(new Tree(List.of(new TreeEntry(FileMode.REGULAR, "hello.txt", blob))));
        c1    = commit("one");
        c2    = commit("two", c1);
        side  = commit("side", c1);
        merge = commit("merge", c2, side);
        repo.refs().update("HEAD", merge, null);
        repo.refs().update("refs/heads/side", side, null);
        repo.refs().update("refs/tags/v1", side, null);
    }

    private String commit(String msg, String... parents) throws Exception {
        return repo.objects().write(new Commit(tree, List.of(parents), ME, ME, msg + "\n"));
    }

    private String rev(String s) throws Exception { return Revision.resolve(repo, s); }

    @Test
    void namesAndIds() throws Exception {
        assertEquals(merge, rev("HEAD"));
        assertEquals(merge, rev("@"));
        assertEquals(merge, rev("main"));                    // -> refs/heads/main
        assertEquals(side, rev("side"));
        assertEquals(side, rev("v1"));                       // -> refs/tags/v1
        assertEquals(side, rev("refs/heads/side"));
        assertEquals(c1, rev(c1));
        assertEquals(c1, rev(c1.substring(0, 7)));           // short id
    }

    @Test
    void ancestry() throws Exception {
        assertEquals(c2, rev("HEAD~"));
        assertEquals(c2, rev("HEAD^"));
        assertEquals(c2, rev("HEAD~1"));
        assertEquals(c1, rev("HEAD~2"));
        assertEquals(side, rev("HEAD^2"));                   // second parent of the merge
        assertEquals(c1, rev("HEAD^2~1"));
        assertEquals(c1, rev("main^^"));
        assertEquals(merge, rev("HEAD^0"));
    }

    @Test
    void peeling() throws Exception {
        assertEquals(tree, rev("HEAD^{tree}"));
        assertEquals(tree, rev("HEAD~2^{tree}"));
        assertEquals(merge, rev("HEAD^{commit}"));
        assertThrows(IllegalStateException.class, () -> rev(tree + "^{commit}"));
    }

    @Test
    void unknownRevisions() {
        for (String bad : new String[] {"nope", "HEAD~3", "HEAD^3", "c1^", "HEAD^{blob}", "HEAD~x", "", "ffff"})
            assertThrows(IllegalStateException.class, () -> rev(bad), bad);
        assertThrows(IllegalStateException.class, () -> rev(blob + "^"));   // a blob has no parents
    }

    @Test
    void unbornHeadIsUnknown() throws Exception {
        Repository fresh = Repository.init(tmp.resolve("fresh"));
        assertThrows(IllegalStateException.class, () -> Revision.resolve(fresh, "HEAD"));
    }
}
