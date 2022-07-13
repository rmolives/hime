package org.hime.parse

import org.hime.cast
import org.hime.core.SymbolTable
import org.hime.core.eval
import org.hime.parse.Type.*
import org.hime.toToken
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

typealias Hime_HimeFunction = (List<Token>) -> Token                        // 自举函数
typealias Hime_Function = (List<Token>, SymbolTable) -> Token
typealias Hime_StaticFunction = (ASTNode, SymbolTable) -> Token

val TRUE = Token(BOOL, true)
val FALSE = Token(BOOL, false)
val NIL = Token(Type.NIL, "nil")

val EMPTY_STREAM = Token(Type.EMPTY_STREAM, "empty-stream")

val LB = Token(Type.LB, "(")
val RB = Token(Type.RB, ")")

/**
 * @param type  类型
 * @param value 内容
 */
class Token(val type: Type, val value: Any) {
    override fun equals(other: Any?): Boolean {
        return other.hashCode() == this.hashCode()
    }

    override fun hashCode(): Int {
        return Objects.hash(type, value)
    }

    override fun toString(): String {
        return when (this.type) {
            STR, Type.LB, Type.RB, EMPTY, Type.NIL, ID -> cast<String>(this.value)
            BOOL -> cast<Boolean>(this.value).toString()
            NUM -> cast<Int>(this.value).toString()
            REAL -> cast<Float>(this.value).toString()
            BIG_NUM -> cast<BigInteger>(this.value).toString()
            BIG_REAL -> cast<BigDecimal>(this.value).toPlainString()
            LIST -> {
                val builder = StringBuilder("[")
                val list = cast<List<Token>>(this.value)
                for (i in list.indices)
                    builder.append(if (i == 0) list[i].toString() else ", ${list[i]}")
                builder.append("]")
                return builder.toString()
            }
            FUNCTION, STATIC_FUNCTION -> "<Function: ${this.value.hashCode()}>"
            HIME_FUNCTION -> "<Function: ${this.value.hashCode()}>"
            else -> this.value.toString()
        }
    }
}

/**
 * 建立过程
 * @param parameters 形式参数
 * @param asts      一系列组合式
 * @param symbol    符号表
 * @return          返回Hime_HimeFunction
 */
fun structureHimeFunction(parameters: List<String>, asts: List<ASTNode>, symbol: SymbolTable): Token {
    return Token(
        HIME_FUNCTION,
        fun(args: List<Token>): Token {
            // 判断参数的数量
            assert(args.size >= parameters.size)
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            for (i in parameters.indices)
                newSymbol.put(parameters[i], args[i])
            var result = NIL
            for (astNode in asts)
                result = eval(astNode.copy(), newSymbol)
            return result
        })
}

/**
 * 建立变长
 * @param parameters 形式参数
 * @param asts      一系列组合式
 * @param symbol    符号表
 * @return          返回Hime_HimeFunction
 */
fun variableHimeFunction(parameters: List<String>, asts: List<ASTNode>, symbol: SymbolTable): Token {
    return Token(
        HIME_FUNCTION,
        fun(args: List<Token>): Token {
            // 判断参数的数量
            assert(args.size >= parameters.size)
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            for (i in 0 until parameters.size - 1)
                newSymbol.put(parameters[i], args[i])
            val variableArgs = ArrayList<Token>()
            for (i in parameters.size - 1 until args.size)
                variableArgs.add(args[i])
            newSymbol.put(parameters[parameters.size - 1], variableArgs.toToken())
            var result = NIL
            for (astNode in asts)
                result = eval(astNode.copy(), newSymbol)
            return result
        })
}

enum class Type {
    UNKNOWN,
    LB, RB, EMPTY, NIL,
    ID, BOOL, STR, LIST, BYTE, TABLE,
    NUM, REAL, BIG_NUM, BIG_REAL, AST, EMPTY_STREAM,
    FUNCTION, STATIC_FUNCTION, HIME_FUNCTION;
}