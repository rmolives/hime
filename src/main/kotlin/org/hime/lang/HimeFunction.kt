package org.hime.lang

import org.hime.cast
import org.hime.parse.AstNode
import org.hime.parse.Token
import org.hime.toToken

typealias Hime_HimeFunction = (List<Token>) -> Token                        // 自举函数
typealias Hime_Function = (List<Token>, SymbolTable) -> Token
typealias Hime_StaticFunction = (AstNode, SymbolTable) -> Token

class HimeFunction(
    val env: Env,
    val funcType: FuncType,
    val func: Any,
    val paramTypes: List<HimeType>,
    val variadic: Boolean,
    val varType: HimeType = env.getType("any")
) {
    // 接受任意类型，任意个数的参数的函数
    constructor(env: Env, funcType: FuncType, func: Any) : this(env, funcType, func, listOf(), true)

    // 接受任意类型，指定个数的参数的函数
    constructor(env: Env, funcType: FuncType, func: Any, size: Int) :
            this(env, funcType, func, List(size, fun(_) = env.getType("any")), false)

    fun call(args: List<Token>, symbol: SymbolTable = env.symbol): Token {
        himeAssertRuntime(funcType != FuncType.STATIC) { "static function definition." }
        himeAssertRuntime(args.size >= paramTypes.size) { "not enough arguments." }
        for (i in paramTypes.indices)
            himeAssertRuntime(env.isType(args[i], paramTypes[i])) {
                "${paramTypes[i].name} expected but ${args[i].type.name} at position $i"
            }
        if (!variadic)
            himeAssertRuntime(args.size == paramTypes.size) { "too many arguments." }
        else {
            for (i in paramTypes.size until args.size)
                himeAssertRuntime(env.isType(args[i], varType)) {
                    "${varType.name} expected but ${args[i].type.name} at position $i"
                }
        }
        val result = when (this.funcType) {
            FuncType.USER_DEFINED -> cast<Hime_HimeFunction>(func)(args)
            FuncType.BUILT_IN -> cast<Hime_Function>(func)(args, symbol)
            else -> toToken(env) // 不可能进入该分支
        }
        return result
    }

    override fun toString(): String {
        return "<Function: ${this.func.hashCode()}>"
    }
}

enum class FuncType {
    BUILT_IN, USER_DEFINED, STATIC;
}