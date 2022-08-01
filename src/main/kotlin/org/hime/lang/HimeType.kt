package org.hime.lang

import org.hime.cast

open class HimeType(open val name: String, open val children: MutableList<HimeType> = arrayListOf()) {
    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        return other is HimeType && cast<HimeType>(other).name == this.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
open class HimeTypeId(val env: Env, var type: HimeType = env.getType("any")) : HimeType("id")