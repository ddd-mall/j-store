package com.jstore.common.framework

interface Entity<I : Identify> {
    val id: I
}