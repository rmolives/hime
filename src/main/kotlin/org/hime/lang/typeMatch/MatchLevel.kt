package org.hime.lang.typeMatch

data class MatchLevel(
    val inheritMatch: Int = -1,
    val judgeMatch: Int = 0
) {
    operator fun plus(rhs: MatchLevel) =
        MatchLevel(
            if (this.inheritMatched()) this.inheritMatch + rhs.inheritMatch else rhs.inheritMatch,
            this.judgeMatch + rhs.judgeMatch
        )

    operator fun compareTo(rhs: MatchLevel): Int {
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

    fun incInherit() = MatchLevel(inheritMatch + (if (inheritMatched()) 1 else 0), judgeMatch)
}

val noMatchLevel = MatchLevel()
val sameMatchLevel = MatchLevel(inheritMatch = 0)
val judgeMatchLevel = MatchLevel(judgeMatch = 1)
