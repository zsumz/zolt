package com.zolt.toml.dependency;

import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;

abstract class ZoltTomlDependencySectionsWriterTestSupport {
    final ZoltTomlParser parser = new ZoltTomlParser();
    final ZoltTomlWriter writer = new ZoltTomlWriter();
}
