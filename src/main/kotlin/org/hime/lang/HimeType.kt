package org.hime.lang

import org.hime.cast
import org.hime.generateRandomString

open class HimeType(
    open val name: String = generateRandomString(),
    open val children: MutableList<HimeType> = arrayListOf(),
    open val mode: HimeTypeMode = HimeTypeMode.BASIC,
    open val column: List<HimeType> = arrayListOf(),
    open val identifier: String = generateRandomString()
) {
    override fun equals(other: Any?): Boolean {
        return other is HimeType && cast<HimeType>(other).identifier == identifier
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    override fun toString(): String {
        return name
    }

    enum class HimeTypeMode {
        BASIC, INTERSECTION, UNION, COMPLEMENTARY, WRONG
    }
}

open class HimeTypeId(val env: Env, val type: HimeType = env.getType("any")) : HimeType(name = "id", identifier = "id_identifier")