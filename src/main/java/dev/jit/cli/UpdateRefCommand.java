package dev.jit.cli;

import dev.jit.core.Repository;
import dev.jit.core.Revision;
import dev.jit.objects.ObjectType;
import dev.jit.storage.Refs;

import java.util.Arrays;

/**
 * jit update-ref <ref> <new> [<old>]      point a ref at a commit
 * jit update-ref -d <ref> [<old>]         delete a ref
 * <old> makes it a safe compare-and-swap: refuse unless the ref still holds <old> (all zeros = must not exist).
 */
public final class UpdateRefCommand implements Command {
    public String name()  { return "update-ref"; }
    public String usage() { return "[-d] <ref> [<new>] [<old>]  set or delete a ref"; }

    public int run(String[] args) throws Exception {
        boolean delete = args.length > 0 && args[0].equals("-d");
        String[] rest = delete ? Arrays.copyOfRange(args, 1, args.length) : args;
        int max = delete ? 2 : 3, min = delete ? 1 : 2;
        if (rest.length < min || rest.length > max)
            throw new IllegalArgumentException("usage: jit update-ref <ref> <new> [<old>]  |  jit update-ref -d <ref> [<old>]");

        Repository repo = Repository.findFromCwd();
        String ref = rest[0];
        if (!Refs.isValidName(ref)) throw new IllegalArgumentException("invalid ref name: " + ref + " (use HEAD or refs/...)");
        String oldArg = rest.length == max ? rest[max - 1] : null;
        String old = oldArg == null || oldArg.equals(Refs.ZERO_ID) ? oldArg : Revision.resolve(repo, oldArg);

        if (delete) {
            repo.refs().delete(ref, old);
            return 0;
        }
        String id = Revision.resolve(repo, rest[1]);
        String target = repo.refs().readSymbolic(ref).orElse(ref);   // HEAD on a branch -> the branch
        if (target.startsWith("refs/heads/") && repo.objects().read(id).type() != ObjectType.COMMIT)
            throw new IllegalArgumentException("a branch can only point at a commit: " + id);
        repo.refs().update(ref, id, old);
        return 0;
    }
}
