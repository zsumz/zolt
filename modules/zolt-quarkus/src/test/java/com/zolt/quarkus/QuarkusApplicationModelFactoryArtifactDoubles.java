package com.zolt.quarkus;

import java.util.Map;

final class QuarkusApplicationModelFactoryArtifactDoubles {
    private QuarkusApplicationModelFactoryArtifactDoubles() {
    }

    public record FakeArtifactKey(String groupId, String artifactId, String classifier, String type) {
        public static FakeArtifactKey of(String groupId, String artifactId, String classifier, String type) {
            return new FakeArtifactKey(groupId, artifactId, classifier, type);
        }
    }

    public interface FakePlatformImports {
    }

    public static final class FakePlatformImportsImpl implements FakePlatformImports {
        private Map<String, String> properties = Map.of();

        public void setPlatformProperties(Map<String, String> properties) {
            this.properties = Map.copyOf(properties);
        }

        Map<String, String> properties() {
            return properties;
        }
    }
}
