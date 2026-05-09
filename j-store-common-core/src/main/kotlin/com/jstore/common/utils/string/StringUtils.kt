package com.jstore.common.utils.string

object StringUtils {

    fun isBlank(cs: CharSequence?): Boolean {
        return isEmpty(cs)
    }

    fun isNotBlank(cs: CharSequence?): Boolean {
        return !isBlank(cs)
    }

    fun isEmpty(cs: CharSequence?): Boolean {
        if (cs == null) {
            return true
        }
        val length = cs.length
        if (length > 0) {
            for (i in 0 until length) {
                if (!Character.isWhitespace(cs[i])) {
                    return false
                }
            }
        }
        return true
    }

    fun isNotEmpty(cs: CharSequence?): Boolean {
        return !isEmpty(cs)
    }

    fun isAllEmpty(vararg css: CharSequence?): Boolean {
        for (cs in css) {
            if (isNotEmpty(cs)) {
                return false
            }
        }
        return true
    }

}