package com.zolt.quarkus.bootstrap;

final class QuarkusApplicationModelFactoryTestDoubles {
    private QuarkusApplicationModelFactoryTestDoubles() {
    }

    static QuarkusApplicationModelApi fakeApi() {
        return new QuarkusApplicationModelApi(
                QuarkusApplicationModelFactoryModelDoubles.FakeApplicationModelBuilder.class.getName(),
                QuarkusApplicationModelFactoryModelDoubles.FakeResolvedDependencyBuilder.class.getName(),
                QuarkusApplicationModelFactoryArtifactDoubles.FakePlatformImports.class.getName(),
                QuarkusApplicationModelFactoryArtifactDoubles.FakePlatformImportsImpl.class.getName(),
                QuarkusApplicationModelFactoryArtifactDoubles.FakeArtifactKey.class.getName());
    }

    static QuarkusApplicationModelApi fakeApiWithArtifactKey() {
        return fakeApi();
    }
}
