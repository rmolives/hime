package org.hime.core

import org.hime.lang.Env
import org.hime.lang.FuncType
import org.hime.lang.HimeFunction
import org.hime.lang.SymbolTable
import org.hime.parse.Token
import org.hime.toToken

fun initRegex(env: Env) {
    env.symbol.table.putAll(
        mutableMapOf(
            "match" to (HimeFunction(env, FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString().matches(Regex(args[1].toString())).toToken(env)
            }, 2)).toToken(env)
        )
    )
}
