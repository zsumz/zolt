package com.example.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MainTest {
    @Test
    void usesGeneratedProtobufAndGrpcTypes() {
        assertEquals("com.example.greeter.Greeter:HelloRequest", Main.greetingContract());
    }
}
