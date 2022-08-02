package org.hime.lang

import org.hime.cast
import org.hime.generateRandomString

open class HimeType(
    open val name: String = generateRandomString(),
    open val children: MutableList<HimeType> = arrayListOf(),
    open val mode: HimeTypeMode = HimeTypeMode.BASIC,
    open val column: List<HimeType> = arrayListOf()
) {

    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        return other is HimeType && cast<HimeType>(other).name == this.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    enum class HimeTypeMode {
        BASIC, INTERSECTION, UNION, COMPLEMENTARY, WEONG
    }
}

open class HimeTypeId(val env: Env, var type: HimeType = env.getType("any")) : HimeType("id")