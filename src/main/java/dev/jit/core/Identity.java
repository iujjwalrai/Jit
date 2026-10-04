package dev.jit.core;

import dev.jit.objects.Signature;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Works out the author/committer of a new commit, the same way git does:
 *   1. environment: JIT_AUTHOR_NAME, JIT_AUTHOR_EMAIL, JIT_AUTHOR_DATE (and JIT_COMMITTER_*)
 *   2. .jit/config: user.name, user.email
 *   3. date defaults to now, in this machine's timezone
 * The env vars are what make commit ids reproducible in tests: same inputs, same timestamp, same id.
 */
public final class Identity {
    private Identity() {}

    public static Signature author(Config config)    { return resolve("AUTHOR", config, System.getenv(), Instant.now()); }
    public static Signature committer(Config config) { return resolve("COMMITTER", config, System.getenv(), Instant.now()); }

    static Signature resolve(String role, Config config, Map<String, String> env, Instant now) {
        String name  = pick(env, "JIT_" + role + "_NAME",  config, "user.name");
        String email = pick(env, "JIT_" + role + "_EMAIL", config, "user.email");

        String date = env.get("JIT_" + role + "_DATE");             // "<epoch seconds> <+hhmm>", git's raw format
        if (date != null) {
            String[] parts = date.trim().split(" ");
            if (parts.length != 2) throw new IllegalArgumentException("JIT_" + role + "_DATE must look like '1700000000 +0530'");
            return new Signature(name, email, Long.parseLong(parts[0]), Signature.parseOffset(parts[1]));
        }
        return new Signature(name, email, now.getEpochSecond(), ZoneId.systemDefault().getRules().getOffset(now));
    }

    private static String pick(Map<String, String> env, String envKey, Config config, String configKey) {
        return Optional.ofNullable(env.get(envKey))
                .or(() -> config.get(configKey))
                .orElseThrow(() -> new IllegalStateException(
                        "identity unknown: set " + configKey + " in .jit/config, e.g.\n\n"
                        + "  [user]\n\tname = Your Name\n\temail = you@example.com\n\n"
                        + "or set " + envKey));
    }
}
