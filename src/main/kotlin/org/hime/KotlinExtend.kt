package org.hime

import org.hime.core.HimeFunction
import org.hime.lang.type.getType
import org.hime.parse.NIL
import org.hime.parse.Token
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * 将对象转换为Token
 * @return 转换结果
 */
fun Any.toToken(): Token {
    return when (this) {
        is Token -> this
        is Float -> this.toDouble().toToken()
        is BigInteger -> Token(getType("int"), this)
        is BigDecimal -> if (this.signum() == 0 || this.scale() <= 0 || this.stripTrailingZeros().scale() <= 0)
            this.toBigIntegerExact().toToken()
        else Token(getType("real"), this)
        is String -> Token(getType("string"), this)
        is Int -> this.toLong().toToken()
        is Long -> BigInteger.valueOf(this).toToken()
        is Double -> BigDecimal.valueOf(this).toToken()
        is Map<*, *> -> {
            val table = HashMap<Token, Token>()
            for ((key, value) in this)
                table[key?.toToken() ?: NIL] = value?.toToken() ?: NIL
            return Token(getType("table"), table)
        }
        is List<*> -> {
            val array = ArrayList<Token>()
            for (e in this)
                array.add(e?.toToken() ?: NIL)
            Token(getType("list"), array)
        }
        is Boolean -> Token(getType("bool"), this)
        is Byte -> Token(getType("byte"), this)
        is HimeFunction -> Token(getType("function"), this)
        is ReentrantLock -> Token(getType("lock"), this)
        else -> Token(getType("any"), this)
    }
}

inline fun <reified R> cast(any: Any?) =
    any as? R ?: throw java.lang.RuntimeException("null is not ${R::class.java.name}.")