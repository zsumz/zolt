package com.example;

public final class Calculator {
    private Calculator() {
    }

    public static int add(int left, int right) {
        return left + right;
    }

    public static void main(String[] args) {
        System.out.println(add(2, 3));
    }
}

