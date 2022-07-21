package ink.hime.parse

import ink.hime.cast
import ink.hime.core.FuncType
import ink.hime.core.HimeFunction
import ink.hime.core.SymbolTable
import ink.hime.core.eval
import ink.hime.parse.Type.*
import ink.hime.toToken
import java.math.BigDecimal
import java.util.*

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
            REAL -> cast<BigDecimal>(this.value).toPlainString()
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
        FUNCTION,
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
        }, parameters.size))
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
        FUNCTION,
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
        }, listOf(), true))
}

enum class Type {
    UNKNOWN,
    LB, RB, EMPTY, NIL,
    ID, BOOL, STR, LIST, BYTE, TABLE,
    NUM, INT, REAL, AST, EMPTY_STREAM,
    FUNCTION,
    THREAD, LOCK;
}