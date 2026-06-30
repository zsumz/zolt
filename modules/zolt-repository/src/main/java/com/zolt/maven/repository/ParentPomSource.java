package com.zolt.maven.repository;

import com.zolt.maven.Coordinate;

@FunctionalInterface
interface ParentPomSource {
    RawPom load(Coordinate coordinate);
}
