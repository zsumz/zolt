package com.zolt.maven;

@FunctionalInterface
public interface ParentPomSource {
    RawPom load(Coordinate coordinate);
}
