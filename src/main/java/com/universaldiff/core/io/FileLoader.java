package com.universaldiff.core.io;

import com.universaldiff.core.model.FileDescriptor;

import java.io.IOException;
import java.nio.file.Path;

public interface FileLoader {

    FileDescriptor detect(Path path) throws IOException;
}
