package com.jstore.common.query

interface Page<T> {
    val currentPage: Int
    val totalElements: Int
    val records: List<T>
}

data class SortedPage<T>(
    override val currentPage: Int,
    override val totalElements: Int,
    override val records: List<T>,
) : Page<T>
