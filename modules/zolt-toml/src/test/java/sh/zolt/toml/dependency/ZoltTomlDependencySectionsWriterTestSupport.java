package sh.zolt.toml.dependency;

import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;

abstract class ZoltTomlDependencySectionsWriterTestSupport {
    final ZoltTomlParser parser = new ZoltTomlParser();
    final ZoltTomlWriter writer = new ZoltTomlWriter();
}
