package org.hime.parse

import org.hime.cast
import org.hime.core.FuncType
import org.hime.core.HimeFunction
import org.hime.core.SymbolTable
import org.hime.core.eval
import org.hime.lang.HimeType
import org.hime.lang.getType
import org.hime.toToken
import java.math.BigDecimal
import java.util.*

val TRUE = Token(getType("bool"), true)
val FALSE = Token(getType("bool"), false)
val NIL = Token(getType("word"), "nil")

val EMPTY = Token(getType("word"), "empty")
val EMPTY_STREAM = Token(getType("word"), "empty-stream")

val LB = Token(getType("id"), "(")
val RB = Token(getType("id"), ")")

/**
 * @param type  类型
 * @param value 内容
 */
class Token(val type: HimeType, val value: Any) {
    override fun equals(other: Any?): Boolean {
        return other.hashCode() == this.hashCode()
    }

    override fun hashCode(): Int {
        return Objects.hash(type, value)
    }

    override fun toString(): String {
        return when (this.type) {
            getType("real") -> cast<BigDecimal>(this.value).toPlainString()
            else -> this.value.toString()
        }
    }
}

/**
 * 建立过程
 * @param parameters 形式参数
 * @param paramTypes 类型
 * @param asts      一系列组合式
 * @param symbol    符号表
 * @return          返回Hime_HimeFunction
 */
fun structureHimeFunction(
    parameters: List<String>,
    paramTypes: List<HimeType>,
    asts: List<ASTNode>,
    symbol: SymbolTable
): Token {
    return Token(
        getType("function"),
        HimeFunction(FuncType.USER_DEFINED, fun(args: List<Token>): Token {
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
        }, paramTypes, false)
    )
}

/**
 * 建立变长
 * @param parameters 形式参数
 * @param paramTypes 类型
 * @param asts      一系列组合式
 * @param symbol    符号表
 * @return          返回Hime_HimeFunction
 */
fun variableHimeFunction(
    parameters: List<String>,
    paramTypes: List<HimeType>,
    asts: List<ASTNode>,
    symbol: SymbolTable
): Token {
    return Token(
        getType("function"),
        HimeFunction(FuncType.USER_DEFINED, fun(args: List<Token>): Token {
            // 判断参数的数量
            assert(args.size >= parameters.size - 1)
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
        }, paramTypes, true)
    )
}