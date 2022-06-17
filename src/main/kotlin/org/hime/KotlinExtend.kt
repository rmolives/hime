package org.hime

import org.hime.parse.Token
import org.hime.parse.Type
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger

fun BigDecimal.simplification(): BigDecimal {
    if (this == BigDecimal.ZERO)
        return this
    var s = this.toPlainString()
    if (s.contains(".")) {
        for (i in s.length - 1 downTo 0) {
            if (!s.contains("."))
                break
            if (s[i] != '0' || s[i] != '.')
                break
            s = s.substring(0, i)
        }
    }
    return BigDecimal(s)
}

fun Any.toToken(): Token {
    return when (this) {
        is Token -> this
        is BigInteger -> Token(Type.NUM, this)
        is BigDecimal -> Token(Type.REAL, this)
        is String -> Token(Type.STR, this)
        is Long -> Token(Type.NUM, BigInteger.valueOf(this))
        is Double -> Token(Type.REAL, BigDecimal.valueOf(this))
        is List<*> -> {
            val list = ArrayList<Token>()
            for (e in this)
                list.add(this.toToken())
            return Token(Type.LIST, list)
        }
        is Boolean -> Token(Type.BOOL, this)
        is Byte -> Token(Type.BYTE, this)
        is InputStream -> Token(Type.IO_INPUT, this)
        is OutputStream -> Token(Type.IO_OUT, this)
        else -> Token(Type.UNKNOWN, this)
    }
}

inline fun <reified R> cast(any: Any?) = any as? R ?: throw java.lang.RuntimeException("null is not ${R::class.java.name}.")