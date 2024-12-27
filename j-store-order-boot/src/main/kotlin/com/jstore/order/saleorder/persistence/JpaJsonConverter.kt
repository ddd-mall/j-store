package com.jstore.com.jstore.order.saleorder.persistence

import jakarta.persistence.AttributeConverter

class JpaJsonConverter: AttributeConverter<Any, String> {
    override fun convertToDatabaseColumn(attribute: Any?): String {
        TODO("Not yet implemented")
    }

    override fun convertToEntityAttribute(dbData: String?): Any {
        TODO("Not yet implemented")
    }
}