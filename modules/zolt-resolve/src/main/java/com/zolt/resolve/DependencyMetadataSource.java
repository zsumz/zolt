package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import java.util.List;

@FunctionalInterface
public interface DependencyMetadataSource {
    EffectiveRawPom load(Coordinate coordinate);

    default void preload(List<Coordinate> coordinates) {
        // Default metadata sources do not preload. The resolver repository context
        // opts into bounded parallel fetching for real repository-backed metadata.
    }
}
