package com.zolt.quarkus;

final class QuarkusApplicationModelFactoryTestSupport {
    private QuarkusApplicationModelFactoryTestSupport() {
    }

    static QuarkusApplicationModelApi fakeApi() {
        return QuarkusApplicationModelFactoryTestDoubles.fakeApi();
    }

    static QuarkusApplicationModelApi fakeApiWithArtifactKey() {
        return QuarkusApplicationModelFactoryTestDoubles.fakeApiWithArtifactKey();
    }
}
