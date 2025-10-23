package com.universaldiff.core.io;

import java.nio.charset.Charset;
import java.nio.file.Path;

public interface EncodingDetector {

    Charset detect(Path path);
}
