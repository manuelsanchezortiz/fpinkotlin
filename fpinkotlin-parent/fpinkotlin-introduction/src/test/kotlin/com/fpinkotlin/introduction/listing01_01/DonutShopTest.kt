package com.fpinkotlin.introduction.listing01_01

import org.junit.Assert.assertEquals
import org.junit.Test


class DonutShopTest: StringSpec()  {

    @Test
    fun testBuyCoffee() {
        val creditCard = CreditCard()
        buyDonut(creditCard)
        buyDonut(creditCard)
        assertEquals(Donut.price * 2, creditCard.total)
    }

}
