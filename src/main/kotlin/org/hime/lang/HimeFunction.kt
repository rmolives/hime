package org.hime.lang

import org.hime.cast
import org.hime.parse.ASTNode
import org.hime.parse.Token
import org.hime.toToken

typealias Hime_HimeFunction = (List<Token>) -> Token                        // 自举函数
typealias Hime_Function = (List<Token>, SymbolTable) -> Token
typealias Hime_StaticFunction = (ASTNode, SymbolTable) -> Token

class HimeFunction(
    private val env: Env,
    private val funcType: FuncType,
    private val func: Any,
    private val paramTypes: List<HimeType>,
    private val variadic: Boolean
) {
    // 接受任意类型，任意个数的参数的函数
    constructor(env: Env, funcType: FuncType, func: Any) : this(env, funcType, func, listOf(), true)

    // 接受任意类型，指定个数的参数的函数
    constructor(env: Env, funcType: FuncType, func: Any, size: Int) :
            this(env, funcType, func, List(size, fun(_) = env.getType("any")), false)

    fun call(ast: ASTNode, symbol: SymbolTable): Token {
        if (funcType == FuncType.STATIC) {
            ast.tok = cast<Hime_StaticFunction>(func)(ast, symbol)
            ast.clear()
            return ast.tok
        }
        for (i in 0 until ast.size())
            ast[i].tok = eval(env, ast[i].copy(), symbol.createChild())
        val args = ArrayList<Token>()
        for (i in 0 until ast.size())
            args.add(ast[i].tok)
        return call(args, symbol)
    }

    fun call(args: List<Token>, symbol: SymbolTable): Token {
        himeAssertRuntime(funcType != FuncType.STATIC) { "static function definition." }
        himeAssertRuntime(args.size >= paramTypes.size) { "not enough arguments." }
        for (i in paramTypes.indices)
            himeAssertRuntime(env.isType(args[i], paramTypes[i])) { "${paramTypes[i].name} expected but ${args[i].type.name} at position $i" }
        if (!variadic)
            himeAssertRuntime(args.size == paramTypes.size) { "too many arguments." }
        val result = when (this.funcType) {
            FuncType.USER_DEFINED -> cast<Hime_HimeFunction>(func)(args)
            FuncType.BUILT_IN -> cast<Hime_Function>(func)(args, symbol)
            else -> toToken(env) // 不可能进入该分支
        }
        return result
    }

    fun call(args: List<Token>): Token {
        himeAssertRuntime(funcType == FuncType.USER_DEFINED) { "call non user defined function." }
        return call(args, env.symbols.createChild())
    }

    override fun toString(): String {
        return "<Function: ${this.func.hashCode()}>"
    }
}

enum class FuncType {
    BUILT_IN, USER_DEFINED, STATIC;
}