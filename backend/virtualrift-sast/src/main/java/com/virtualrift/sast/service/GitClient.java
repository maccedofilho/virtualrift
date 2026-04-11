package com.virtualrift.sast.service;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public interface GitClient {

    void cloneRepository(URI repositoryUri, Path destination, Duration timeout);
}
