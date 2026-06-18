package com.example.protobuf;

import com.example.greeter.api.GreeterGrpc;
import com.example.greeter.api.HelloRequest;

public final class Main {
    private Main() {
    }

    public static String greetingContract() {
        return GreeterGrpc.serviceName() + ":" + HelloRequest.getDefaultInstance().getClass().getSimpleName();
    }

    public static void main(String[] args) {
        System.out.println(greetingContract());
    }
}
