package com.jstore.com.jstore.framework

interface Entity<I : Identify> {
    fun getId(): I
}