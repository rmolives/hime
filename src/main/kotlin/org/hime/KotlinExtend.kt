package org.hime

import org.hime.core.HimeFunction
import org.hime.parse.ASTNode
import org.hime.parse.NIL
import org.hime.parse.Token
import org.hime.parse.Type
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.locks.ReentrantLock

fun Token.isNum(): Boolean = this.type == Type.INT || this.type == Type.REAL

/**
 * 将对象转换为Token
 * @return 转换结果
 */
fun Any.toToken(): Token {
    return when (this) {
        is Token -> this
        is ASTNode -> Token(Type.AST, this)
        is Float -> this.toDouble().toToken()
        is BigInteger -> Token(Type.INT, this)
        is BigDecimal -> if (this.signum() == 0 || this.scale() <= 0 || this.stripTrailingZeros().scale() <= 0)
            this.toBigIntegerExact().toToken()
        else Token(Type.REAL, this)
        is String -> Token(Type.STR, this)
        is Int -> this.toLong().toToken()
        is Long -> BigInteger.valueOf(this).toToken()
        is Double -> BigDecimal.valueOf(this).toToken()
        is Map<*, *> -> {
            val table = HashMap<Token, Token>()
            for ((key, value) in this)
                table[key?.toToken() ?: NIL] = value?.toToken() ?: NIL
            return Token(Type.TABLE, table)
        }
        is List<*> -> {
            val array = ArrayList<Token>()
            for (e in this)
                array.add(e?.toToken() ?: NIL)
            Token(Type.LIST, array)
        }
        is Boolean -> Token(Type.BOOL, this)
        is Byte -> Token(Type.BYTE, this)
        is HimeFunction -> Token(Type.FUNCTION, this)
        is ReentrantLock -> Token(Type.LOCK, this)
        else -> Token(Type.UNKNOWN, this)
    }
}

inline fun <reified R> cast(any: Any?) =
    any as? R ?: throw java.lang.RuntimeException("null is not ${R::class.java.name}.")