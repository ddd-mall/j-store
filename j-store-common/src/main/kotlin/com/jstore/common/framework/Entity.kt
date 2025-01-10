package com.jstore.common.framework

interface Entity<I : Identify> {
    fun getId(): I?
}