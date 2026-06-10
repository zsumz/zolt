package com.example

import spock.lang.Specification

final class CalculatorSpec extends Specification {
    def "adds numbers"() {
        expect:
        Calculator.add(2, 3) == 5
    }
}
