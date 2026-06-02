package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;

@FunctionalInterface
public interface DependencyMetadataSource {
    EffectiveRawPom load(Coordinate coordinate);
}
