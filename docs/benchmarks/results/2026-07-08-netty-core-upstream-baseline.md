# 2026-07-08 Netty Core Upstream Maven Baseline

Status: validated local upstream-tool baseline.

This run proves the Netty benchmark lane can execute against a pinned real
Netty checkout before a Zolt adapter exists. It is not a Zolt comparison.

- Project: Netty
- Upstream: `https://github.com/netty/netty`
- Branch: `4.1`
- Commit: `bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6`
- Artifact: `target/benchmarks/netty-core-upstream-baseline-r1`
- Runner: local macOS 15.1.1 arm64
- Maven: Apache Maven 3.9.16
- Java: GraalVM Java 21.0.5
- Repeat count: 1
- Result status: complete

| Workflow | Maven median | Samples |
| --- | ---: | ---: |
| Clean package Netty core Java reactor | 47,635 ms | 1 |
| Parallel clean package Netty core Java reactor | 30,444 ms | 1 |
| Clean compile common module | 4,052 ms | 1 |
| Warm no-op common module | 2,407 ms | 1 |
| Implementation change in common | 3,584 ms | 1 |

The core Java reactor package lane includes:

```text
common, buffer, transport, resolver, codec, codec-dns, codec-haproxy,
codec-http, codec-http2, codec-memcache, codec-mqtt, codec-redis,
codec-smtp, codec-socks, codec-stomp, codec-xml, handler, handler-proxy,
handler-ssl-ocsp
```

Native platform modules and testsuites are intentionally excluded from this
baseline. Full native packaging is a separate future lane because the pinned
Netty 4.1 snapshot can require native-source artifacts that are not available
from public Maven repositories.
