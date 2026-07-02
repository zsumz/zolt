package sh.zolt.explain;

final class MigrationExplainGoldenFixtures {
    private MigrationExplainGoldenFixtures() {
    }

    static String mavenSimpleText() {
        return MigrationExplainMavenFixtures.simpleText();
    }

    static String mavenSimpleJson() {
        return MigrationExplainMavenFixtures.simpleJson();
    }

    static String gradleSimpleText() {
        return MigrationExplainGradleFixtures.simpleText();
    }

    static String gradleSimpleJson() {
        return MigrationExplainGradleFixtures.simpleJson();
    }

    static String gradleEnterpriseSpringText() {
        return MigrationExplainGradleFixtures.enterpriseSpringText();
    }
}
