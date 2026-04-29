package com.jstore.accounting.service.command

import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.common.properties.Price
import java.time.LocalDate

data class RecordOrderPaidCMD(
    val orderId: String,
    val merchantId: String,
    val paidAmount: Price,
    val accountingDate: LocalDate,
    val sourceDocument: SourceDocument,
)
