package com.jstore.common.utils.cache

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class WeakReferenceCache<K, V> {

    private val cache: ConcurrentHashMap<K & Any, V & Any> = ConcurrentHashMap()

    private class KeyedReference<K, V>(val key: K, value: V) : WeakReference<V>(value)

}