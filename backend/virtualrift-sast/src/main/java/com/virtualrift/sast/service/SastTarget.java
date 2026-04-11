package com.virtualrift.sast.service;

import java.nio.file.Path;

public class SastTarget implements AutoCloseable {

    private final Path path;
    private final Runnable cleanup;

    private SastTarget(Path path, Runnable cleanup) {
        this.path = path;
        this.cleanup = cleanup;
    }

    public static SastTarget local(Path path) {
        return new SastTarget(path, () -> { });
    }

    public static SastTarget temporary(Path path, Runnable cleanup) {
        return new SastTarget(path, cleanup);
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        cleanup.run();
    }
}
