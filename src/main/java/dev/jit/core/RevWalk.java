package dev.jit.core;

import dev.jit.objects.Commit;
import dev.jit.storage.ObjectStore;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Visits every commit reachable from a starting point, newest first, each exactly once.
 * History is a graph, not a list: after a merge there are two parents to follow, and both
 * branches eventually lead back to shared commits. So, like git:
 *   - keep a queue ordered by commit time (newest out first)
 *   - remember what's been queued, so shared ancestors aren't shown twice
 *   - ties go to whichever was queued first (a merge's first parent before its second)
 */
public final class RevWalk {
    public record Entry(String id, Commit commit) {}

    private record Queued(Entry entry, long seq) {}

    private final ObjectStore store;
    private final PriorityQueue<Queued> queue = new PriorityQueue<>(
            Comparator.comparingLong((Queued q) -> -q.entry.commit().committer().epochSeconds())
                      .thenComparingLong(Queued::seq));
    private final Set<String> seen = new HashSet<>();
    private long seq;

    public RevWalk(ObjectStore store, String start) throws IOException {
        this.store = store;
        push(start);
    }

    /** The next commit, or null when history runs out. */
    public Entry next() throws IOException {
        Queued q = queue.poll();
        if (q == null) return null;
        for (String parent : q.entry.commit().parents()) push(parent);
        return q.entry;
    }

    private void push(String id) throws IOException {
        if (seen.add(id)) queue.add(new Queued(new Entry(id, store.readCommit(id)), seq++));
    }
}
