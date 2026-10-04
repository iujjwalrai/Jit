package dev.jit.storage;

import dev.jit.objects.ObjectType;

/** An object read back from disk: its type and body, before parsing into Blob/Tree/Commit. */
public record RawObject(ObjectType type, byte[] body) {}