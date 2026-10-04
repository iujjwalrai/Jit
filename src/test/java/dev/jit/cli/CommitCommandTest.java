package dev.jit.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommitCommandTest {

    @Test
    void cleanupMatchesGit() {
        // git commit -m "  padded subject  \n\n" -m "\n\n\npara two   "  stored exactly this:
        assertEquals("  padded subject\n\npara two\n",
                CommitCommand.cleanup(CommitTreeCommand.joinMessages(java.util.List.of("  padded subject  \n\n", "\n\n\npara two   "))));
        assertEquals("one\n", CommitCommand.cleanup("one"));
        assertEquals("a\n\nb\n", CommitCommand.cleanup("\n\na  \n\n\n\nb\n\n"));
        assertEquals("", CommitCommand.cleanup("  \n\t\n"));             // all whitespace -> empty -> commit aborts
    }
}
