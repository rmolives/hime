package org.hime.lang

import org.hime.parse.FALSE
import org.hime.parse.Token
import org.hime.toToken
import java.math.BigDecimal
import java.math.MathContext

val types: MutableMap<String, HimeType> =
    mutableMapOf(
        "int" to HimeTypeInt(),
        "real" to HimeTypeReal(),
        "num" to HimeTypeNum(),
        "eq" to HimeTypeEq(),
        "ord" to HimeTypeOrd(),
        "string" to HimeTypeString(),
        "list" to HimeTypeList(),
        "id" to HimeTypeId(),
        "bool" to HimeTypeBool(),
        "table" to HimeTypeTable(),
        "byte" to HimeTypeByte(),
        "word" to HimeTypeWord(),
        "thread" to HimeTypeThread(),
        "lock" to HimeTypeLock(),
        "function" to HimeTypeFunction(),
        "any" to HimeTypeAny()
    )

fun isType(token: Token, type: HimeType) = type::class.java.isAssignableFrom(token.type::class.java)

fun getType(name: String) = types[name] ?: throw HimeRuntimeException("$name type does not exist.")

open class HimeType(open val name: String)

open class HimeTypeAny(override val name: String = "any") : HimeType(name)

open class HimeTypeEq(override val name: String = "eq") : HimeTypeAny(name) {
    open fun eq(t1: Token, t2: Token) = (t1 == t2).toToken()
}

open class HimeTypeOrd(override val name: String = "ord") : HimeTypeEq(name) {
    open fun greater(t1: Token, t2: Token) = FALSE
    open fun less(t1: Token, t2: Token) = FALSE
    open fun greaterOrEq(t1: Token, t2: Token) = FALSE
    open fun lessOrEq(t1: Token, t2: Token) = FALSE
}

open class HimeTypeNum(override val name: String = "num") : HimeTypeOrd(name) {
    override fun greater(t1: Token, t2: Token) =
        (BigDecimal(t1.value.toString()) > BigDecimal(t2.value.toString())).toToken()

    override fun less(t1: Token, t2: Token) =
        (BigDecimal(t1.value.toString()) < BigDecimal(t2.value.toString())).toToken()

    override fun greaterOrEq(t1: Token, t2: Token) =
        (BigDecimal(t1.value.toString()) >= BigDecimal(t2.value.toString())).toToken()

    override fun lessOrEq(t1: Token, t2: Token) =
        (BigDecimal(t1.value.toString()) <= BigDecimal(t2.value.toString())).toToken()

    open fun add(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).add(BigDecimal(t2.toString()))).toToken()

    open fun subtract(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).subtract(BigDecimal(t2.toString()))).toToken()

    open fun multiply(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).multiply(BigDecimal(t2.toString()))).toToken()

    open fun divide(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).divide(BigDecimal(t2.toString()), MathContext.DECIMAL64)).toToken()
}

open class HimeTypeInt(override val name: String = "int") : HimeTypeNum(name)

open class HimeTypeReal(override val name: String = "real") : HimeTypeNum(name)

open class HimeTypeString(override val name: String = "string") : HimeTypeEq(name)

open class HimeTypeList(override val name: String = "list") : HimeTypeEq(name)

open class HimeTypeId : HimeTypeEq("id")

open class HimeTypeTable : HimeTypeAny("table")

open class HimeTypeByte : HimeTypeEq("byte")

open class HimeTypeWord : HimeTypeEq("word")

open class HimeTypeBool : HimeTypeEq("bool")

open class HimeTypeThread : HimeTypeAny("thread")

open class HimeTypeLock : HimeTypeAny("lock")

open class HimeTypeFunction : HimeTypeAny("function")