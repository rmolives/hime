package org.hime.core

import org.hime.cast
import org.hime.lang.*
import org.hime.parse.Token
import org.hime.toToken
import java.util.HashMap

fun initTable(env: Env) {
    env.symbol.table.putAll(
        mutableMapOf(
            "table" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(_: List<Token>, _: SymbolTable): Token {
                        return mapOf<Token, Token>().toToken(env)
                    },
                    0
                )
            ).toToken(env),
            "table-put" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        val table = HashMap(cast<Map<Token, Token>>(args[0].value))
                        table[args[1]] = args[2]
                        return table.toToken(env)
                    },
                    listOf(env.getType("table"), env.getType("any"), env.getType("any")),
                    false
                )
            ).toToken(env),
            "table-get" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        himeAssertRuntime(args.size > 1) { "not enough arguments." }
                        himeAssertType(args[0], "table", env)
                        val table = cast<Map<Token, Token>>(args[0].value)
                        return table[args[1]] ?: env.himeNil
                    },
                    listOf(env.getType("table"), env.getType("any")),
                    false
                )
            ).toToken(env),
            "table-remove" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        val table = HashMap(cast<Map<Token, Token>>(args[0].value))
                        table.remove(args[1])
                        return table.toToken(env)
                    },
                    listOf(env.getType("table"), env.getType("any")),
                    false
                )
            ).toToken(env),
            "table-keys" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        return cast<Map<Token, Token>>(args[0].value).keys.toList().toToken(env)
                    },
                    listOf(env.getType("table")),
                    false
                )
            ).toToken(env),
            "table-put!" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        cast<MutableMap<Token, Token>>(args[0].value)[args[1]] = args[2]
                        return args[0].toToken(env)
                    },
                    listOf(env.getType("table"), env.getType("any"), env.getType("any")),
                    false
                )
            ).toToken(env),
            "table-remove!" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        cast<MutableMap<Token, Token>>(args[0].value).remove(args[1])
                        return args[0].toToken(env)
                    },
                    listOf(env.getType("table"), env.getType("any")),
                    false
                )
            ).toToken(env)
        )
    )
}