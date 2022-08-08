package org.hime.lang

data class TypeMatchLevel(
    val inheritMatch: Int = -1,
    val judgeMatch: Int = 0
) {
    operator fun plus(rhs: TypeMatchLevel) =
        TypeMatchLevel(
            if (this.inheritMatched()) this.inheritMatch + rhs.inheritMatch else rhs.inheritMatch,
            this.judgeMatch + rhs.judgeMatch
        )

    operator fun compareTo(rhs: TypeMatchLevel): Int {
        val inheritMatchCmp =
            if (this.inheritMatched() && rhs.inheritMatched()) // 继承匹配越大，总匹配度越小
                rhs.inheritMatch.compareTo(this.inheritMatch)
            else // 但继承匹配小于0代表根本没有匹配到
                this.inheritMatch.compareTo(rhs.inheritMatch)
        if (inheritMatchCmp != 0)
            return inheritMatchCmp

        return this.judgeMatch.compareTo(rhs.judgeMatch)
    }

    fun inheritMatched() = inheritMatch >= 0
    fun judgeMatched() = judgeMatch > 0

    fun matched() = inheritMatched() || judgeMatched()

    fun incInherit() = TypeMatchLevel(inheritMatch + (if (inheritMatched()) 1 else 0), judgeMatch)
}

val noMatchLevel = TypeMatchLevel()
val sameMatchLevel = TypeMatchLevel(inheritMatch = 0)
val judgeMatchLevel = TypeMatchLevel(judgeMatch = 1)
