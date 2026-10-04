package dev.jit.core;

import dev.jit.objects.Commit;
import dev.jit.storage.ObjectStore;
import dev.jit.storage.Refs;
import dev.jit.util.Hashing;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a user types into an object id, like git rev-parse:
 *   HEAD, @          what HEAD points at
 *   main, v1         looked up as refs/main, refs/tags/v1, refs/heads/main, ... (first hit wins)
 *   3b18e5           a short id (at least 4 hex chars)
 *   X~2              X's first parent's first parent
 *   X^2              X's second parent (only merges have one); X^ = X^1 = X~1
 *   X^{tree}         the tree X points at; X^{commit} checks X is a commit
 */
public final class Revision {
    private Revision() {}

    // git's lookup order for a short name, from `git help revisions`
    private static final List<String> REF_PATTERNS = List.of(
            "%s", "refs/%s", "refs/tags/%s", "refs/heads/%s", "refs/remotes/%s", "refs/remotes/%s/HEAD");

    private static final Pattern SUFFIX = Pattern.compile("~(\\d*)|\\^\\{(tree|commit)}|\\^(\\d*)");

    public static String resolve(Repository repo, String rev) throws IOException {
        int cut = indexOfSuffix(rev);
        String id = resolveBase(repo, rev.substring(0, cut), rev);

        Matcher m = SUFFIX.matcher(rev);
        int pos = cut;
        while (pos < rev.length()) {
            if (!m.find(pos) || m.start() != pos) throw unknown(rev);
            if (m.group(2) != null) {                                       // ^{tree} / ^{commit}
                id = m.group(2).equals("tree") ? repo.objects().peelToTree(id) : idOfCommit(repo, id, rev);
            } else if (m.group(1) != null) {                                // ~n: walk n first-parents
                for (int n = number(m.group(1)); n > 0; n--) id = parent(repo, id, 1, rev);
            } else {                                                        // ^n: n-th parent; ^0 = the commit itself
                int n = number(m.group(3));
                id = n == 0 ? idOfCommit(repo, id, rev) : parent(repo, id, n, rev);
            }
            pos = m.end();
        }
        return id;
    }

    private static String resolveBase(Repository repo, String name, String rev) throws IOException {
        if (name.isEmpty()) throw unknown(rev);
        if (name.equals("@")) name = "HEAD";
        ObjectStore store = repo.objects();

        if (Hashing.isFullHex(name)) {
            if (!store.contains(name)) throw unknown(rev);
            return name;
        }
        for (String pattern : REF_PATTERNS) {                               // refs win over short ids, like git
            String ref = pattern.formatted(name);
            if (!Refs.isValidName(ref)) continue;
            Optional<String> id = repo.refs().read(ref);
            if (id.isPresent()) return id.get();
        }
        if (name.matches("[0-9a-f]{4,39}")) return store.resolvePrefix(name);
        throw unknown(rev);
    }

    private static String parent(Repository repo, String id, int n, String rev) throws IOException {
        List<String> parents = commit(repo, id, rev).parents();
        if (n > parents.size()) throw unknown(rev);                         // e.g. HEAD~5 with only 3 commits
        return parents.get(n - 1);
    }

    private static Commit commit(Repository repo, String id, String rev) throws IOException {
        try {
            return repo.objects().readCommit(id);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(rev + ": " + e.getMessage());
        }
    }

    private static String idOfCommit(Repository repo, String id, String rev) throws IOException {
        commit(repo, id, rev);
        return id;
    }

    private static int indexOfSuffix(String rev) {
        for (int i = 0; i < rev.length(); i++) if (rev.charAt(i) == '~' || rev.charAt(i) == '^') return i;
        return rev.length();
    }

    private static int number(String digits) { return digits.isEmpty() ? 1 : Integer.parseInt(digits); }

    private static IllegalStateException unknown(String rev) {
        return new IllegalStateException("unknown revision: " + rev);
    }
}
