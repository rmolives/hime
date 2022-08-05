package org.hime.lang

import org.hime.cast
import org.hime.parse.ASTNode
import org.hime.parse.Token

class HimeFunctionScheduler(private val env: Env, private val functions: MutableList<HimeFunction> = ArrayList()) {
    fun add(function: HimeFunction): HimeFunctionScheduler {
        functions.add(function)
        return this
    }

    fun call(ast: ASTNode, symbol: SymbolTable = env.symbol): Token {
        for (function in functions) {
            if (function.funcType == FuncType.STATIC) {
                ast.tok = cast<Hime_StaticFunction>(function.func)(ast, symbol)
                ast.clear()
                return ast.tok
            }
        }
        for (i in 0 until ast.size())
            ast[i].tok = eval(env, ast[i].copy(), symbol.createChild())
        val args = ArrayList<Token>()
        for (i in 0 until ast.size())
            args.add(ast[i].tok)
        return call(args, symbol)
    }

    fun call(args: List<Token>, symbol: SymbolTable = env.symbol): Token {
        var maxWeight = Int.MIN_VALUE
        var function: HimeFunction? = null
        val its =
            functions.filter { args.size == it.paramTypes.size || (it.variadic && args.size >= it.paramTypes.size) }
        loop@ for (it in its) {
            var weight = 0
            for (index in 0 until it.paramTypes.size) {
                val type = env.getTypeWeight(args[index], it.paramTypes[index])
                if (!type.first)
                    continue@loop
                weight += env.getTypeWeight(args[index], it.paramTypes[index]).second
            }
            if (weight >= maxWeight) {
                maxWeight = weight
                function = it
            }
        }
        return function?.call(args, symbol) ?: throw HimeRuntimeException("No matching function was found.")
    }
}