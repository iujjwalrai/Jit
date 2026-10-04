package dev.jit.cli;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommitTreeCommandTest {

    @Test
    void joinsMessagesLikeGit() {   // each case checked against git commit-tree -m ...
        assertEquals("first commit\n", CommitTreeCommand.joinMessages(List.of("first commit")));
        assertEquals("second\n\nbody para\n", CommitTreeCommand.joinMessages(List.of("second", "body para")));
        assertEquals("trailing newline\n", CommitTreeCommand.joinMessages(List.of("trailing newline\n")));
        assertEquals("", CommitTreeCommand.joinMessages(List.of("")));
    }
}
