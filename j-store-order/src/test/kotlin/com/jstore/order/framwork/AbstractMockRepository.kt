package com.jstore.order.framwork

import com.jstore.common.framework.Entity
import com.jstore.common.framework.Identify
import com.jstore.common.framework.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class AbstractMockRepository<I : Identify, E : Entity<I>> : Repository<I, E> {
    protected val objList: MutableList<E> = ArrayList()
    private val idxMap: ConcurrentMap<I, Int> = ConcurrentHashMap()

    abstract fun nextId(): I
    abstract fun copyAnEntity(nextId: I, entity: E): E

    override fun save(entity: E): E {
        entity.id.let { id ->
            idxMap[id]?.let { index ->
                objList[index] = entity
                return entity
            }
            synchronized(objList) {
                val i = objList.size
                idxMap.putIfAbsent(id, i)?.let { actualIndex ->
                    objList[actualIndex] = entity
                    return entity
                }
                objList.add(entity)
                return entity
            }
        }
    }

    override fun findById(id: I): E? {
        return idxMap[id]?.let { objList[it] }
    }
}