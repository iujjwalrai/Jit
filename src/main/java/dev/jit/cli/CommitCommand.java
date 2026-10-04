package dev.jit.cli;

import dev.jit.core.Config;
import dev.jit.core.Identity;
import dev.jit.core.Repository;
import dev.jit.core.TreeWriter;
import dev.jit.index.Index;
import dev.jit.objects.Commit;
import dev.jit.objects.Tree;
import dev.jit.storage.ObjectStore;
import dev.jit.storage.Refs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * jit commit -m <message>... [--allow-empty]
 * The three plumbing steps in one: write-tree (from the index), commit-tree -p HEAD, update-ref HEAD.
 * Only what's been `jit add`ed goes in; other edits stay in the working directory for a later commit.
 */
public final class CommitCommand implements Command {
    public String name()  { return "commit"; }
    public String usage() { return "-m <msg>... [--allow-empty]  record the staged files as a new commit"; }

    public int run(String[] args) throws Exception {
        List<String> messages = new ArrayList<>();
        boolean allowEmpty = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-m" -> {
                    if (i + 1 == args.length) throw new IllegalArgumentException("option -m needs a value");
                    messages.add(args[++i]);
                }
                case "--allow-empty" -> allowEmpty = true;
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (messages.isEmpty()) throw new IllegalArgumentException("no message: use jit commit -m <message>");
        String message = cleanup(CommitTreeCommand.joinMessages(messages));
        if (message.isEmpty()) throw new IllegalArgumentException("aborting commit due to empty commit message");

        Repository repo = Repository.findFromCwd();
        ObjectStore store = repo.objects();
        Refs refs = repo.refs();

        Optional<String> parent = refs.read("HEAD");                      // empty on the very first commit
        String tree = new TreeWriter(store).write(Index.load(repo.indexFile()));
        String parentTree = parent.isPresent() ? store.readCommit(parent.get()).tree() : ObjectStore.hash(new Tree(List.of()));
        if (!allowEmpty && tree.equals(parentTree)) {                     // nothing staged since last commit
            System.out.println("nothing to commit (stage changes with \"jit add\")");
            return 1;
        }

        Config config = repo.config();
        Commit commit = new Commit(tree, parent.stream().toList(), Identity.author(config), Identity.committer(config), message);
        String id = store.write(commit);
        refs.update("HEAD", id, parent.orElse(Refs.ZERO_ID));            // fails if HEAD moved while we worked

        String where = refs.readSymbolic("HEAD").map(r -> r.replaceFirst("^refs/heads/", "")).orElse("detached HEAD");
        System.out.println("[" + where + (parent.isEmpty() ? " (root-commit)" : "") + " " + id.substring(0, 7) + "] "
                + commit.subject());
        return 0;
    }

    /**
     * Git's "whitespace" cleanup for -m messages: strip trailing spaces from each line, drop blank lines
     * at the start and end, squeeze runs of blank lines into one, and end with a single newline.
     */
    static String cleanup(String message) {
        StringBuilder out = new StringBuilder();
        boolean pendingBlank = false;
        for (String line : message.split("\n", -1)) {
            String trimmed = line.stripTrailing();
            if (trimmed.isEmpty()) {
                pendingBlank = true;
                continue;
            }
            if (pendingBlank && !out.isEmpty()) out.append('\n');         // at most one blank line, never a leading one
            out.append(trimmed).append('\n');
            pendingBlank = false;
        }
        return out.toString();
    }
}
