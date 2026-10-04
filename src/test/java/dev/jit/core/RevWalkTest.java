package dev.jit.core;

import dev.jit.objects.Commit;
import dev.jit.objects.Signature;
import dev.jit.objects.Tree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RevWalkTest {

    @TempDir Path tmp;
    Repository repo;
    String tree;

    @BeforeEach
    void setUp() throws Exception {
        repo = Repository.init(tmp);
        tree = repo.objects().write(new Tree(List.of()));
    }

    private String commit(String msg, long time, String... parents) throws Exception {
        Signature s = new Signature("U", "u@example.com", time, ZoneOffset.ofHoursMinutes(5, 30));
        return repo.objects().write(new Commit(tree, List.of(parents), s, s, msg + "\n"));
    }

    private List<String> walk(String start) throws Exception {
        RevWalk w = new RevWalk(repo.objects(), start);
        List<String> subjects = new ArrayList<>();
        for (RevWalk.Entry e; (e = w.next()) != null; ) subjects.add(e.commit().subject());
        return subjects;
    }

    @Test
    void newestFirstEachOnceSameOrderAsGit() throws Exception {
        // the history git log printed as: merge side, two, side work, one
        String one   = commit("one", 1700000000);
        String two   = commit("two", 1700000100, one);
        String side  = commit("side work", 1700000100, one);         // same time as "two": first parent wins the tie
        String merge = commit("merge side", 1700000200, two, side);
        assertEquals(List.of("merge side", "two", "side work", "one"), walk(merge));   // "one" reached twice, shown once
    }

    @Test
    void followsTimeNotJustParents() throws Exception {
        String base  = commit("base", 100);
        String old   = commit("old branch", 150, base);
        String fresh = commit("fresh", 300, base);
        String merge = commit("merge", 400, old, fresh);
        assertEquals(List.of("merge", "fresh", "old branch", "base"), walk(merge));
    }

    @Test
    void subjectIsFirstParagraphOnOneLine() {
        Signature s = new Signature("U", "u@example.com", 0, ZoneOffset.UTC);
        assertEquals("wrapped subject line", new Commit(tree, List.of(), s, s, "wrapped\nsubject line\n\nbody\n").subject());
        assertEquals("", new Commit(tree, List.of(), s, s, "").subject());
    }
}
