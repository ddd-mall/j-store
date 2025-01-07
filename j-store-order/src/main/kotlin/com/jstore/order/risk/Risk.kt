package com.jstore.order.risk

interface Risk {
    fun checkRisk(): Boolean
    fun handleRisk(): Boolean
}

class RiskImpl : Risk {

    override fun checkRisk(): Boolean {
        return true
    }

    override fun handleRisk(): Boolean {
        return true
    }

}






