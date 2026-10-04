package dev.jit.cli;

import dev.jit.core.Config;
import dev.jit.core.Identity;
import dev.jit.core.Repository;
import dev.jit.objects.Commit;
import dev.jit.storage.ObjectStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** jit commit-tree <tree> [-p <parent>]... [-m <message>]...   (no -m: message read from stdin) */
public final class CommitTreeCommand implements Command {
    public String name()  { return "commit-tree"; }
    public String usage() { return "<tree> [-p <parent>]... [-m <msg>]...  create a commit object"; }

    public int run(String[] args) throws Exception {
        String tree = null;
        List<String> parents = new ArrayList<>(), messages = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p", "-m" -> {
                    if (i + 1 == args.length) throw new IllegalArgumentException("option " + args[i] + " needs a value");
                    (args[i].equals("-p") ? parents : messages).add(args[++i]);   // ++i: consume the value too
                }
                default -> tree = args[i];
            }
        }
        if (tree == null) throw new IllegalArgumentException("usage: jit commit-tree <tree> [-p <parent>]... [-m <message>]...");

        Repository repo = Repository.findFromCwd();
        ObjectStore store = repo.objects();
        String treeId = store.resolvePrefix(tree);
        store.readTree(treeId);                                      // fail now if it's missing or not a tree
        List<String> parentIds = new ArrayList<>();
        for (String p : parents) {
            String id = store.resolvePrefix(p);
            store.readCommit(id);                                    // parents must be commits
            parentIds.add(id);
        }
        String message = messages.isEmpty()
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)   // stdin: stored exactly as given
                : joinMessages(messages);

        Config config = repo.config();
        Commit commit = new Commit(treeId, parentIds, Identity.author(config), Identity.committer(config), message);
        System.out.println(store.write(commit));
        return 0;
    }

    /** Git's rule for -m: paragraphs separated by a blank line, and the result ends in exactly one newline. */
    static String joinMessages(List<String> messages) {
        StringBuilder sb = new StringBuilder();
        for (String m : messages) {
            if (!sb.isEmpty()) sb.append('\n');                      // previous one already ends in \n -> blank line
            sb.append(m);
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        }
        return sb.toString();
    }
}
