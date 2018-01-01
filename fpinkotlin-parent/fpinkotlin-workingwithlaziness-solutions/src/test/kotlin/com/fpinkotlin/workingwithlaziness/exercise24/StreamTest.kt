package com.fpinkotlin.workingwithlaziness.exercise24

import com.fpinkotlin.common.range
import com.fpinkotlin.generators.IntGenerator
import com.fpinkotlin.generators.forAll
import io.kotlintest.specs.StringSpec

class LazyTest: StringSpec() {

    init {

        "filter" {
            forAll(IntGenerator(0, 10_000), { a ->
                var incCalls = 0
                fun inc(i: Int): Int {
                    incCalls++
                    return i + 1
                }
                val list = range(0, a).filter { it % 2 == 0 }
                val stream = Stream.iterate(Lazy{ inc(-1) }, ::inc).takeWhileViaFoldRight { it < a }.filter { it % 2 == 0 }
                val evaluated = incCalls
                val result1 = stream.toList().toString()
                val result2 = stream.toList().toString()
                list.toString() == result1 && result2 == result1 && evaluated == 1 && incCalls == a + 1 // + 1 for seed
            }, 10)
        }
    }

}
