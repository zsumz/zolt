# zolt vs Maven — differential resolution summary

Pre-fix baseline captured on 2026-07-10. The
`software.amazon.awssdk:s3:2.29.45` and
`org.apache.hadoop:hadoop-client:3.4.1` rows are retained as evidence for
resolver defects fixed by targeted regression tests; rerun the harness
before replacing this baseline with post-fix results.

| root | verdict | match | divergences |
| --- | --- | --- | --- |
| com.google.guava:guava:33.4.0-jre | clean | 7 | - |
| com.fasterxml.jackson.core:jackson-databind:2.18.2 | clean | 3 | - |
| org.apache.commons:commons-lang3:3.17.0 | clean | 1 | - |
| org.slf4j:slf4j-api:2.0.16 | clean | 1 | - |
| org.apache.logging.log4j:log4j-core:2.24.3 | clean | 2 | - |
| com.google.code.gson:gson:2.11.0 | clean | 2 | - |
| com.squareup.okhttp3:okhttp:4.12.0 | 3 divergences | 5 | version-diff/expected-newest-wins: 3 |
| com.squareup.retrofit2:retrofit:2.11.0 | clean | 3 | - |
| com.google.protobuf:protobuf-java:4.29.3 | clean | 1 | - |
| org.assertj:assertj-core:3.27.2 | clean | 2 | - |
| org.mockito:mockito-core:5.15.2 | clean | 4 | - |
| org.junit.jupiter:junit-jupiter:5.11.4 | clean | 8 | - |
| org.springframework.boot:spring-boot-starter-web:3.4.1 | 1 divergences | 33 | version-diff/expected-newest-wins: 1 |
| org.springframework:spring-context:6.2.1 | clean | 8 | - |
| org.hibernate.orm:hibernate-core:6.6.4.Final | 1 divergences | 16 | version-diff/expected-newest-wins: 1 |
| io.micrometer:micrometer-core:1.14.2 | clean | 5 | - |
| org.eclipse.jetty:jetty-server:12.0.16 | clean | 5 | - |
| org.apache.tomcat.embed:tomcat-embed-core:10.1.34 | clean | 2 | - |
| io.netty:netty-all:4.1.117.Final | clean | 31 | - |
| io.grpc:grpc-netty-shaded:1.69.0 | clean | 16 | - |
| org.apache.kafka:kafka-clients:3.9.0 | clean | 5 | - |
| software.amazon.awssdk:s3:2.29.45 | 16 divergences | 30 | only-maven/INVESTIGATE: 16 |
| org.apache.hadoop:hadoop-client:3.4.1 | zolt-hard-fail/intended-no-uninterpolated-versions | 0 | - |
| org.elasticsearch.client:elasticsearch-rest-high-level-client:7.17.28 | clean | 49 | - |
| com.netflix.eureka:eureka-client:2.0.4 | clean | 37 | - |

## Divergence class totals

- `only-maven/INVESTIGATE`: 16
- `version-diff/expected-newest-wins`: 5
- `zolt-hard-fail/intended-no-uninterpolated-versions`: 1
