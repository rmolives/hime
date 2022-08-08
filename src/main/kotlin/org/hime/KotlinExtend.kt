package org.hime

import org.hime.lang.Env
import org.hime.lang.HimeFunctionScheduler
import org.hime.lang.exception.HimeRuntimeException
import org.hime.lang.HimeType
import org.hime.parse.Token
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * 将对象转换为Token
 * @return 转换结果
 */
fun Any.toToken(env: Env): Token {
    return when (this) {
        is Token -> this
        is HimeType -> Token(env.getType("type"), this)
        is Float -> this.toDouble().toToken(env)
        is BigInteger -> Token(env.getType("int"), this)
        is BigDecimal -> if (this.signum() == 0 || this.scale() <= 0 || this.stripTrailingZeros().scale() <= 0)
            this.toBigIntegerExact().toToken(env)
        else Token(env.getType("real"), this)
        is String -> Token(env.getType("string"), this)
        is Int -> this.toLong().toToken(env)
        is Long -> BigInteger.valueOf(this).toToken(env)
        is Double -> BigDecimal.valueOf(this).toToken(env)
        is Map<*, *> -> {
            val table = HashMap<Token, Token>()
            for ((key, value) in this)
                table[key?.toToken(env) ?: env.himeNil] = value?.toToken(env) ?: env.himeNil
            return Token(env.getType("table"), table)
        }
        is List<*> -> {
            val array = ArrayList<Token>()
            for (e in this)
                array.add(e?.toToken(env) ?: env.himeNil)
            Token(env.getType("list"), array)
        }
        is Boolean -> Token(env.getType("bool"), this)
        is Byte -> Token(env.getType("byte"), this)
        is HimeFunctionScheduler -> Token(env.getType("function"), this)
        is ReentrantLock -> Token(env.getType("lock"), this)
        else -> Token(env.getType("any"), this)
    }
}

inline fun <reified R> cast(any: Any?) =
    any as? R ?: throw HimeRuntimeException("null is not ${R::class.java.name}.")