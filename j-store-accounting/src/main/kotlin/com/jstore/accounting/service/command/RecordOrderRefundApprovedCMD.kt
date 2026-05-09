package com.jstore.accounting.service.command

import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.common.properties.Price
import java.time.LocalDate

data class RecordOrderRefundApprovedCMD(
    val orderId: String,
    val merchantId: String,
    val refundAmount: Price,
    val accountingDate: LocalDate,
    val sourceDocument: SourceDocument,
    val originalSourceDocument: SourceDocument,
)
