package org.hime

import org.hime.parse.ASTNode
import org.hime.parse.Token
import org.hime.parse.Type
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.floor

val INT_MAX: BigInteger = BigInteger.valueOf(Int.MAX_VALUE.toLong())
val FLOAT_MAX: BigDecimal = BigDecimal.valueOf(Float.MAX_VALUE.toDouble())

fun Token.isNum(): Boolean = this.isSmallNum() || this.isBigNum()
fun Token.isSmallNum(): Boolean = this.type == Type.NUM || this.type == Type.REAL
fun Token.isBigNum(): Boolean = this.type == Type.BIG_NUM || this.type == Type.BIG_REAL
fun Any.toToken(): Token {
    return when (this) {
        is Token -> this
        is Int -> Token(Type.NUM, this)
        is ASTNode -> Token(Type.AST, this)
        is Float -> if (floor(this.toDouble()).compareTo(this) == 0)
            this.toInt().toToken()
        else Token(Type.REAL, this)
        is BigInteger -> if (this <= INT_MAX) this.toInt().toToken() else Token(Type.BIG_NUM, this)
        is BigDecimal -> if (this.signum() == 0 || this.scale() <= 0 || this.stripTrailingZeros().scale() <= 0)
            this.toBigIntegerExact().toToken()
        else if (this <= FLOAT_MAX) this.toFloat().toToken() else Token(Type.BIG_REAL, this)
        is String -> Token(Type.STR, this)
        is Long -> Token(Type.BIG_NUM, BigInteger.valueOf(this))
        is Double -> Token(Type.BIG_REAL, BigDecimal.valueOf(this))
        is List<*> -> {
            val list = ArrayList<Token>()
            for (e in this)
                list.add(e!!.toToken())
            return Token(Type.LIST, list)
        }
        is Boolean -> Token(Type.BOOL, this)
        else -> Token(Type.UNKNOWN, this)
    }
}

inline fun <reified R> cast(any: Any?) =
    any as? R ?: throw java.lang.RuntimeException("null is not ${R::class.java.name}.")