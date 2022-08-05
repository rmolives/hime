package org.hime.core

import org.hime.lang.*
import org.hime.parse.Token
import org.hime.toToken
import java.text.SimpleDateFormat
import java.util.*

fun initTime(env: Env) {
    env.symbol.table.putAll(
        mutableMapOf(
            "time" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(_: List<Token>, _: SymbolTable): Token {
                        return Date().time.toToken(env)
                    },
                    0
                )
            ).toToken(env),
            "time-format" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        himeAssertType(args[1], "int", env)
                        return SimpleDateFormat(args[0].toString()).format(args[1].toString().toLong())
                            .toToken(env)
                    },
                    2
                )
            ).toToken(env),
            "time-parse" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        return SimpleDateFormat(args[0].toString()).parse(args[1].value.toString()).time.toToken(env)
                    },
                    2
                )
            ).toToken(env)
        )
    )
}
