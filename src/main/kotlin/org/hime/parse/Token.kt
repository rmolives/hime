package org.hime.parse

import org.hime.cast
import org.hime.core.SymbolTable
import org.hime.core.eval
import org.hime.parse.Type.*
import java.math.BigDecimal
import java.math.BigInteger

typealias Hime_HimeFunction = (List<Token>) -> Token                        // 自举函数
typealias Hime_Function = (List<Token>, SymbolTable) -> Token
typealias Hime_StaticFunction = (ASTNode, SymbolTable) -> Token

val TRUE = Token(BOOL, true)
val FALSE = Token(BOOL, false)
val NIL = Token(Type.NIL, "nil")

val EMPTY_STREAM = Token(Type.EMPTY_STREAM, "empty-stream")

val LB = Token(Type.LB, "(")
val RB = Token(Type.RB, ")")

class Token(val type: Type, val value: Any) {
    override fun toString(): String {
        return when (this.type) {
            STR, Type.LB, Type.RB, EMPTY, Type.NIL, ID -> cast<String>(this.value)
            BOOL -> cast<Boolean>(this.value).toString()
            NUM -> cast<Int>(this.value).toString()
            REAL -> cast<Float>(this.value).toString()
            BIG_NUM -> cast<BigInteger>(this.value).toString()
            BIG_REAL -> cast<BigDecimal>(this.value).toPlainString()
            LIST ->  {
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
 *
 */
fun structureHimeFunction(parmeters: List<String>, ast: List<ASTNode>, symbol: SymbolTable): Token {
    return Token(
        HIME_FUNCTION,
        // Initialize function executing environment.
        fun(args: List<Token>): Token {
            // When parameter becomes less, cause a runtime error.
            assert(args.size >= parmeters.size)
            // Build function scope for local variables.
            val newSymbolTable = symbol.createChild()
            // Load args.
            for (i in parmeters.indices)
                newSymbolTable.put(parmeters[i], args[i])
            var result = NIL
            // Analyse AST to execute it.
            for (astNode in ast)
                result = eval(astNode.copy(), newSymbolTable)
            return result
        })
}

enum class Type {
    UNKNOWN,
    LB, RB, EMPTY, NIL,
    ID, BOOL, STR, LIST, BYTE,
    NUM, REAL, BIG_NUM, BIG_REAL, AST, EMPTY_STREAM,
    FUNCTION, STATIC_FUNCTION, HIME_FUNCTION;
}