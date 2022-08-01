package org.hime.lang

import org.hime.cast
import org.hime.core.HimeFunction
import org.hime.parse.Token
import org.hime.toToken
import java.math.BigDecimal
import java.math.MathContext

val types: MutableMap<String, () -> HimeType> =
    mutableMapOf(
        "int" to HimeTypeInt::make,
        "real" to HimeTypeReal::make,
        "num" to HimeTypeNum::make,
        "eq" to HimeTypeEq::make,
        "ord" to HimeTypeOrd::make,
        "string" to HimeTypeString::make,
        "list" to HimeTypeList::make,
        "id" to HimeTypeId::make,
        "bool" to HimeTypeBool::make,
        "table" to HimeTypeTable::make,
        "byte" to HimeTypeByte::make,
        "word" to HimeTypeWord::make,
        "thread" to HimeTypeThread::make,
        "lock" to HimeTypeLock::make,
        "function" to HimeTypeFunction::make,
        "type" to HimeTypeType::make,
        "any" to HimeTypeAny::make
    )

fun addType(name: String, type: () -> HimeType): HimeType {
    types[name] = type
    return getType(name)
}

fun isType(token: Token, type: HimeType) = type::class.java.isAssignableFrom(token.type::class.java)

fun getType(name: String) = types[name]?.let { it() } ?: throw HimeRuntimeException("$name type does not exist.")

open class HimeType(open val name: String) {
    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        return other is HimeType && cast<HimeType>(other).name == this.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

open class HimeTypeAny(override val name: String = "any") : HimeType(name) {
    companion object {
        fun make() = HimeTypeAny()
    }
}

open class HimeTypeEq(override val name: String = "eq") : HimeTypeAny(name) {
    companion object {
        fun make() = HimeTypeEq()
    }

    open fun eq(t1: Token, t2: Token) = t1 == t2
}

open class HimeTypeOrd(override val name: String = "ord") : HimeTypeEq(name) {
    companion object {
        fun make() = HimeTypeOrd()
    }

    open fun greater(t1: Token, t2: Token) = false
    open fun less(t1: Token, t2: Token) = false
    open fun greaterOrEq(t1: Token, t2: Token) = false
    open fun lessOrEq(t1: Token, t2: Token) = false
}

open class HimeTypeNum(override val name: String = "num") : HimeTypeOrd(name) {
    companion object {
        fun make() = HimeTypeNum()
    }

    override fun greater(t1: Token, t2: Token) =
        BigDecimal(t1.value.toString()) > BigDecimal(t2.value.toString())

    override fun less(t1: Token, t2: Token) =
        BigDecimal(t1.value.toString()) < BigDecimal(t2.value.toString())

    override fun greaterOrEq(t1: Token, t2: Token) =
        BigDecimal(t1.value.toString()) >= BigDecimal(t2.value.toString())

    override fun lessOrEq(t1: Token, t2: Token) =
        BigDecimal(t1.value.toString()) <= BigDecimal(t2.value.toString())

    open fun add(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).add(BigDecimal(t2.toString()))).toToken()

    open fun subtract(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).subtract(BigDecimal(t2.toString()))).toToken()

    open fun multiply(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).multiply(BigDecimal(t2.toString()))).toToken()

    open fun divide(t1: Token, t2: Token) =
        (BigDecimal(t1.toString()).divide(BigDecimal(t2.toString()), MathContext.DECIMAL64)).toToken()
}

open class HimeTypeReal(override val name: String = "real") : HimeTypeNum(name) {
    companion object {
        fun make() = HimeTypeReal()
    }
}

open class HimeTypeInt(override val name: String = "int") : HimeTypeReal(name) {
    companion object {
        fun make() = HimeTypeInt()
    }
}

open class HimeTypeString(override val name: String = "string") : HimeTypeEq(name) {
    companion object {
        fun make() = HimeTypeString()
    }
}

open class HimeTypeList(override val name: String = "list") : HimeTypeEq(name) {
    companion object {
        fun make() = HimeTypeList()
    }
}

open class HimeTypeId(var type: HimeType = getType("any")) : HimeTypeEq("id") {
    companion object {
        fun make() = HimeTypeId()
    }
}

open class HimeTypeTable : HimeTypeAny("table") {
    companion object {
        fun make() = HimeTypeTable()
    }
}

open class HimeTypeType : HimeTypeEq("type") {
    companion object {
        fun make() = HimeTypeType()
    }
}

open class HimeTypeByte : HimeTypeEq("byte") {
    companion object {
        fun make() = HimeTypeByte()
    }
}

open class HimeTypeWord : HimeTypeEq("word") {
    companion object {
        fun make() = HimeTypeWord()
    }
}

open class HimeTypeBool : HimeTypeEq("bool") {
    companion object {
        fun make() = HimeTypeBool()
    }
}

open class HimeTypeThread : HimeTypeAny("thread") {
    companion object {
        fun make() = HimeTypeThread()
    }
}

open class HimeTypeLock : HimeTypeAny("lock") {
    companion object {
        fun make() = HimeTypeLock()
    }
}

open class HimeTypeFunction : HimeTypeAny("function") {
    companion object {
        fun make() = HimeTypeFunction()
    }
}

open class HimeTypeCustomAny(name: String) : HimeTypeAny(name) {
    companion object {
        fun make(name: String) = fun() = HimeTypeCustomAny(name)
    }
}

open class HimeTypeCustomEq(name: String, private val eqFunction: HimeFunction) : HimeTypeEq(name) {
    companion object {
        fun make(name: String, eqFunction: HimeFunction) = fun() = HimeTypeCustomEq(name, eqFunction)
    }

    override fun eq(t1: Token, t2: Token): Boolean {
        val result = eqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }
}

open class HimeTypeCustomOrd(
    name: String,
    private val eqFunction: HimeFunction,
    private val greaterFunction: HimeFunction,
    private val lessFunction: HimeFunction,
    private val greaterOrEqFunction: HimeFunction,
    private val lessOrEqFunction: HimeFunction
) : HimeTypeOrd(name) {
    companion object {
        fun make(
            name: String,
            eqFunction: HimeFunction,
            greaterFunction: HimeFunction,
            lessFunction: HimeFunction,
            greaterOrEqFunction: HimeFunction,
            lessOrEqFunction: HimeFunction
        ) = fun() =
            HimeTypeCustomOrd(name, eqFunction, greaterFunction, lessFunction, greaterOrEqFunction, lessOrEqFunction)
    }

    override fun eq(t1: Token, t2: Token): Boolean {
        val result = eqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun greater(t1: Token, t2: Token): Boolean {
        val result = greaterFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun less(t1: Token, t2: Token): Boolean {
        val result = lessFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun greaterOrEq(t1: Token, t2: Token): Boolean {
        val result = greaterOrEqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun lessOrEq(t1: Token, t2: Token): Boolean {
        val result = lessOrEqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }
}

open class HimeTypeCustomNum(
    name: String,
    private val eqFunction: HimeFunction,
    private val greaterFunction: HimeFunction,
    private val lessFunction: HimeFunction,
    private val greaterOrEqFunction: HimeFunction,
    private val lessOrEqFunction: HimeFunction,
    private val addFunction: HimeFunction,
    private val subFunction: HimeFunction,
    private val multFunction: HimeFunction,
    private val divFunction: HimeFunction
) : HimeTypeNum(name) {
    companion object {
        fun make(
            name: String,
            eqFunction: HimeFunction,
            greaterFunction: HimeFunction,
            lessFunction: HimeFunction,
            greaterOrEqFunction: HimeFunction,
            lessOrEqFunction: HimeFunction,
            addFunction: HimeFunction,
            subFunction: HimeFunction,
            multFunction: HimeFunction,
            divFunction: HimeFunction
        ) = fun() =
            HimeTypeCustomNum(
                name,
                eqFunction,
                greaterFunction,
                lessFunction,
                greaterOrEqFunction,
                lessOrEqFunction,
                addFunction,
                subFunction,
                multFunction,
                divFunction
            )
    }

    override fun eq(t1: Token, t2: Token): Boolean {
        val result = eqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun greater(t1: Token, t2: Token): Boolean {
        val result = greaterFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun less(t1: Token, t2: Token): Boolean {
        val result = lessFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun greaterOrEq(t1: Token, t2: Token): Boolean {
        val result = greaterOrEqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun lessOrEq(t1: Token, t2: Token): Boolean {
        val result = lessOrEqFunction.call(arrayListOf(t1, t2))
        himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool" }
        return cast<Boolean>(result.value)
    }

    override fun add(t1: Token, t2: Token) = addFunction.call(arrayListOf(t1, t2))

    override fun subtract(t1: Token, t2: Token) = subFunction.call(arrayListOf(t1, t2))

    override fun multiply(t1: Token, t2: Token) = multFunction.call(arrayListOf(t1, t2))

    override fun divide(t1: Token, t2: Token) = divFunction.call(arrayListOf(t1, t2))
}