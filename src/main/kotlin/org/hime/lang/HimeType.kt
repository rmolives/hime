package org.hime.lang

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

    enum class HimeTypeMode {
        BASIC, INTERSECTION, UNION, COMPLEMENTARY, WEONG
    }
}

open class HimeTypeId(val env: Env, var type: HimeType = env.getType("any")) : HimeType("id")