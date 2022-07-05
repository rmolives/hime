package org.hime.parse

import org.hime.cast
import org.hime.core.SymbolTable
import org.hime.core.eval
import org.hime.draw.Coordinate
import org.hime.parse.Type.*
import java.math.BigDecimal
import java.math.BigInteger

typealias Hime_HimeFunction = (List<Token>) -> Token
typealias Hime_HimeFunctionPair = Pair<ASTNode, Hime_HimeFunction>
typealias Hime_Function = (List<Token>, SymbolTable) -> Token
typealias Hime_StaticFunction = (ASTNode, SymbolTable) -> Token

val TRUE = Token(BOOL, true)
val FALSE = Token(BOOL, false)
val NIL = Token(Type.NIL, "nil")

val LB = Token(Type.LB, "(")
val RB = Token(Type.RB, ")")

class Token(val type: Type, val value: Any) {
    override fun toString(): String {
        return when(this.type) {
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
            HIME_FUNCTION -> {
                val value = cast<Pair<List<String>, List<ASTNode>>>(cast<Hime_HimeFunctionPair>(this.value).first)
                val functionParmeters = value.first
                val asts = value.second
                val builder = StringBuilder("(lambda (")
                var flag = functionParmeters.size > 1
                for (parmeter in functionParmeters) {
                    builder.append("${if (flag) " " else ""}$parmeter")
                    flag = false
                }
                builder.append(") ")
                flag = asts.size > 1
                if (flag)
                    builder.append("(begin")
                for (ast in asts)
                    builder.append("${if (flag) " " else ""}$ast")
                if (flag)
                    builder.append("))")
                return builder.toString()
            }
            DRAW -> "<Draw: ${this.value.hashCode()}>"
            COORDINATE -> "(${cast<Coordinate>(this.value).x}, ${cast<Coordinate>(this.value).y})"
            else -> this.value.toString()
        }
    }
}

fun structureHimeFunction(functionParmeters: List<String>, ast: List<ASTNode>, symbol: SymbolTable): Token {
    return Token(HIME_FUNCTION,
        Pair(Pair(functionParmeters, ast), fun(args: List<Token>): Token {
            assert(args.size >= functionParmeters.size)
            val newSymbolTable = symbol.createChild()
            for (i in functionParmeters.indices)
                newSymbolTable.put(functionParmeters[i], args[i])
            var result = NIL
            for (astNode in ast)
                result = eval(astNode.copy(), newSymbolTable)
            return result
        })
    )
}

enum class Type {
    UNKNOWN,
    LB, RB, EMPTY, NIL,
    ID, BOOL, STR, LIST,
    IO_INPUT, IO_OUT, BYTE,
    NUM, REAL, BIG_NUM, BIG_REAL, AST,
    FUNCTION, STATIC_FUNCTION, HIME_FUNCTION,
    DRAW, COORDINATE
}