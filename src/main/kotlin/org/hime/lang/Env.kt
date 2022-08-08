package org.hime.lang

import org.hime.cast
import org.hime.core.initCore
import org.hime.lang.exception.HimeRuntimeException
import org.hime.parse.AstNode
import org.hime.parse.AstType
import org.hime.parse.Token
import org.hime.toToken
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

class Env(val io: IOConfig = IOConfig(System.out, System.err, System.`in`)) {
    var types = HashMap<String, HimeType>()
    var symbol = SymbolTable(this, HashMap())

    lateinit var typeAny: HimeType

    lateinit var himeTrue: Token
    lateinit var himeFalse: Token
    lateinit var himeNil: Token
    lateinit var himeEmpty: Token
    lateinit var himeEmptyStream: Token
    lateinit var himeLb: Token
    lateinit var himeRb: Token

    lateinit var himeAstEmpty: AstNode

    private lateinit var eqs: MutableMap<HimeType, (Token, Token) -> Boolean>
    private lateinit var ords: MutableMap<HimeType, MutableMap<String, (Token, Token) -> Boolean>>
    private lateinit var ops: MutableMap<HimeType, MutableMap<String, (Token, Token) -> Token>>

    fun himeAdd(t1: Token, t2: Token): Token {
        return findOpFunc(t1, "add")(t1, t2)
    }

    fun himeSub(t1: Token, t2: Token): Token {
        return findOpFunc(t1, "sub")(t1, t2)
    }

    fun himeMult(t1: Token, t2: Token): Token {
        return findOpFunc(t1, "mult")(t1, t2)
    }

    fun himeDiv(t1: Token, t2: Token): Token {
        return findOpFunc(t1, "div")(t1, t2)
    }

    private fun findOpFunc(t1: Token, funcName: String, type: HimeType = getType("op")): (Token, Token) -> Token {
        fun findOpChild(type: HimeType): Boolean {
            if (ops.containsKey(type))
                return true
            for (child in type.children)
                if (findOpChild(child))
                    return true
            return false
        }
        for (child in type.children) {
            if (isType(t1, child) && findOpChild(child))
                return findOpFunc(t1, funcName, child)
        }
        return if (ops[type] != null) ops[type]?.get(funcName) ?: fun(_: Token, _: Token) =
            BigInteger.ZERO.toToken(this) else fun(_: Token, _: Token) = BigInteger.ZERO.toToken(this)
    }

    fun himeEq(t1: Token, t2: Token): Boolean {
        return findEqFunc(t1)(t1, t2)
    }

    private fun findEqFunc(t1: Token, type: HimeType = getType("eq")): (Token, Token) -> Boolean {
        fun findEqChild(type: HimeType): Boolean {
            if (eqs.containsKey(type))
                return true
            for (child in type.children)
                if (findEqChild(child))
                    return true
            return false
        }
        for (child in type.children) {
            if (isType(t1, child) && findEqChild(child))
                return findEqFunc(t1, child)
        }
        return eqs[type] ?: fun(t1: Token, t2: Token) = t1 == t2
    }

    fun himeGreater(t1: Token, t2: Token): Boolean {
        return findOrdFunc(t1, "greater")(t1, t2)
    }

    fun himeGreaterOrEq(t1: Token, t2: Token): Boolean {
        return findOrdFunc(t1, "greaterOrEq")(t1, t2)
    }

    fun himeLess(t1: Token, t2: Token): Boolean {
        return findOrdFunc(t1, "less")(t1, t2)
    }

    fun himeLessOrEq(t1: Token, t2: Token): Boolean {
        return findOrdFunc(t1, "lessOrEq")(t1, t2)
    }

    private fun findOrdFunc(t: Token, funcName: String, type: HimeType = getType("ord")): (Token, Token) -> Boolean {
        fun findOrdChild(type: HimeType): Boolean {
            if (ords.containsKey(type))
                return true
            for (child in type.children)
                if (findOrdChild(child))
                    return true
            return false
        }
        for (child in type.children) {
            if (isType(t, child) && findOrdChild(child))
                return findOrdFunc(t, funcName, child)
        }
        return if (ords[type] != null) ords[type]?.get(funcName) ?: fun(_: Token, _: Token) =
            false else fun(_: Token, _: Token) = false
    }

    init {
        initType()
        initWord()
        initEqs()
        initOrds()
        initOps()
        initSymbols()
    }

    private fun initType() {
        typeAny = HimeType("any")
        val functionType = HimeType("function")
        types["function"] = functionType
        addType(typeAny, arrayListOf())
        addType(functionType)
        addType(HimeType("eq"))
        addType(HimeType("op"))
        addType(HimeType("ord"), "eq")
        addType(HimeType("num"), "op", "ord")
        addType(HimeType("real"), "num")
        addType(HimeType("int"), "real")
        addType(HimeType("structure"))
        addType(HimeType("string"), "eq")
        addType(HimeType("list"), "eq")
        addType(HimeType("bool"), "eq")
        addType(HimeType("word"), "eq")
        addType(HimeType("thread"))
        addType(HimeType("lock"))
        addType(HimeType("type"))
        addType(HimeType("byte"), "eq")
    }

    private fun initEqs() {
        eqs = HashMap()
        eqs[getType("real")] = fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) == BigDecimal(t2.toString())
    }

    private fun initOrds() {
        ords = HashMap()
        ords[getType("real")] = HashMap()
        ords[getType("real")]?.set("greater",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) > BigDecimal(t2.toString()))
        ords[getType("real")]?.set("less",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) < BigDecimal(t2.toString()))
        ords[getType("real")]?.set("greaterOrEq",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) >= BigDecimal(t2.toString()))
        ords[getType("real")]?.set("lessOrEq",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) <= BigDecimal(t2.toString()))
    }

    private fun initOps() {
        ops = HashMap()
        ops[getType("real")] = HashMap()
        ops[getType("real")]?.set("add",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()).add(BigDecimal(t2.toString())).toToken(this))
        ops[getType("real")]?.set("sub",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()).subtract(BigDecimal(t2.toString())).toToken(this))
        ops[getType("real")]?.set("mult",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()).multiply(BigDecimal(t2.toString())).toToken(this))
        ops[getType("real")]?.set("div",
            fun(t1: Token, t2: Token) =
                BigDecimal(t1.toString()).divide(BigDecimal(t2.toString()), MathContext.DECIMAL64).toToken(this))
    }

    private fun initWord() {
        himeTrue = Token(getType("bool"), true)
        himeFalse = Token(getType("bool"), false)
        himeNil = Token(getType("word"), "nil")
        himeEmpty = Token(getType("word"), "empty")
        himeEmptyStream = Token(getType("word"), "empty-stream")
        himeLb = Token(getType("id"), "(")
        himeRb = Token(getType("id"), ")")
        himeAstEmpty = AstNode(himeEmpty, AstType.FUNCTION)
    }

    private fun initSymbols() {
        initCore(this)
    }

    fun addType(type: HimeType, father: List<HimeType> = arrayListOf(typeAny)) {
        types[type.name] = type
        for (f in father)
            f.children.add(type)
        symbol.put("${type.name}?", HimeFunctionScheduler(this).add(
            HimeFunction(
                this,
                FuncType.BUILT_IN,
                fun(args: List<Token>, _: SymbolTable): Token {
                    for (arg in args)
                        if (!this.isType(arg, type))
                            return this.himeFalse
                    return this.himeTrue
                },
                listOf(this.getType("any")),
                true
            )
        ).toToken(this))
    }

    fun addType(type: HimeType, vararg father: String) {
        return addType(type, father.map(::getType))
    }
    fun isType(token: Token, type: HimeType) = type == getType("any") || typeMatch(token, type).matched()

    fun typeMatch(token: Token, type: HimeType): TypeMatchLevel {
        //同类型匹配
        if (token.type == type)
            return sameMatchLevel

        //继承匹配
        var maxMatchLevel = noMatchLevel
        for (child in type.children) {
            val result = typeMatch(token, child)
            if (result > maxMatchLevel)
                maxMatchLevel = result
        }
        if (maxMatchLevel.matched())
            return maxMatchLevel.incInherit()

        //裁决器匹配
        if (type.mode == HimeType.HimeTypeMode.JUDGE) {
            val res = type.judge?.call(listOf(token)) ?: throw HimeRuntimeException("judge is null.")
            himeAssertType(res, "bool", this)
            return if (cast(res.value)) judgeMatchLevel else noMatchLevel
        }

        return noMatchLevel
    }

    fun getType(name: String) =
        when (name) {
            "id" -> HimeTypeId(this)
            else -> types[name]
                ?: throw HimeRuntimeException("$name type does not exist.")
        }
}