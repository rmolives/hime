package org.hime.lang

import org.hime.core.SymbolTable
import org.hime.core.initCore
import org.hime.parse.ASTNode
import org.hime.parse.AstType
import org.hime.parse.Token
import org.hime.toToken
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

class Env {
    private lateinit var types: MutableMap<String, HimeType>
    private lateinit var typeAny: HimeType
    lateinit var himeTrue: Token
    lateinit var himeFalse: Token
    lateinit var himeNil: Token
    lateinit var himeEmpty: Token
    lateinit var himeEmptyStream: Token
    lateinit var himeLb: Token
    lateinit var himeRb: Token

    lateinit var symbols: SymbolTable

    lateinit var himeAstEmpty: ASTNode

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
        for (child in type.children) {
            if (isType(t1, child) && ops.containsKey(child))
                return findOpFunc(t1, funcName, child)
        }
        return if (ops[type] != null) ops[type]?.get(funcName) ?: fun(_: Token, _: Token) =
            BigInteger.ZERO.toToken(this) else fun(_: Token, _: Token) = BigInteger.ZERO.toToken(this)
    }

    fun himeEq(t1: Token, t2: Token): Boolean {
        return findEqFunc(t1)(t1, t2)
    }

    private fun findEqFunc(t1: Token, type: HimeType = getType("eq")): (Token, Token) -> Boolean {
        for (child in type.children) {
            if (isType(t1, child) && eqs.containsKey(child))
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

    fun findOrdFunc(t1: Token, funcName: String, type: HimeType = getType("ord")): (Token, Token) -> Boolean {
        for (child in type.children) {
            if (isType(t1, child) && ords.containsKey(child))
                return findOrdFunc(t1, funcName, child)
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
        types = HashMap()
        types["int"] = HimeType("int")
        types["real"] = HimeType("real")
        types["string"] = HimeType("string")
        types["list"] = HimeType("list")
        types["bool"] = HimeType("bool")
        types["word"] = HimeType("word")
        types["thread"] = HimeType("thread")
        types["lock"] = HimeType("lock")
        types["function"] = HimeType("function")
        types["type"] = HimeType("type")
        types["num"] = HimeType("num", arrayListOf(getType("int"), getType("real")))
        types["op"] = HimeType("op", arrayListOf(getType("num")))
        types["eq"] = HimeType(
            "eq",
            arrayListOf(
                getType("num"),
                getType("string"),
                getType("list"),
                getType("bool"),
                getType("word"),
                getType("type")
            )
        )
        types["ord"] = HimeType("ord", arrayListOf(getType("num")))
        typeAny = HimeType("any")
    }

    private fun initEqs() {
        eqs = HashMap()
        eqs[getType("bool")] = fun(t1: Token, t2: Token) = t1 == t2
        eqs[getType("num")] = fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) == BigDecimal(t2.toString())
        eqs[getType("string")] = fun(t1: Token, t2: Token) = t1 == t2
        eqs[getType("list")] = fun(t1: Token, t2: Token) = t1 == t2
        eqs[getType("word")] = fun(t1: Token, t2: Token) = t1 == t2
        eqs[getType("type")] = fun(t1: Token, t2: Token) = t1 == t2
    }

    private fun initOrds() {
        ords = HashMap()
        ords[getType("num")] = HashMap()
        ords[getType("num")]?.set("greater",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) > BigDecimal(t2.toString()))
        ords[getType("num")]?.set("less",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) < BigDecimal(t2.toString()))
        ords[getType("num")]?.set("greaterOrEq",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) >= BigDecimal(t2.toString()))
        ords[getType("num")]?.set("lessOrEq",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()) <= BigDecimal(t2.toString()))
    }

    private fun initOps() {
        ops = HashMap()
        ops[getType("num")] = HashMap()
        ops[getType("num")]?.set("add",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()).add(BigDecimal(t2.toString())).toToken(this))
        ops[getType("num")]?.set("sub",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()).subtract(BigDecimal(t2.toString())).toToken(this))
        ops[getType("num")]?.set("mult",
            fun(t1: Token, t2: Token) = BigDecimal(t1.toString()).multiply(BigDecimal(t2.toString())).toToken(this))
        ops[getType("num")]?.set("div",
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
        himeAstEmpty = ASTNode(himeEmpty, AstType.FUNCTION)
    }

    private fun initSymbols() {
        symbols = SymbolTable(HashMap(), null, IOConfig(System.out, System.err, System.`in`))
        initCore(this)
    }

    fun isType(token: Token, type: HimeType): Boolean {
        if (type.name == "id")
            return token.type is HimeTypeId
        if (type == typeAny || token.type == type)
            return true
        for (child in type.children)
            if (isType(token, child))
                return true
        return false
    }

    fun getType(name: String) =
        if (name == "id") HimeTypeId(this) else if (name == "any") typeAny else types[name] ?: throw HimeRuntimeException("$name type does not exist.")
}