package sh.zolt.maven.repository;

import sh.zolt.maven.Coordinate;

@FunctionalInterface
interface ParentPomSource {
    RawPom load(Coordinate coordinate);
}
