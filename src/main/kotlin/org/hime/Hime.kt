package org.hime

import org.hime.lang.SymbolTable
import org.hime.lang.eval
import org.hime.lang.Env
import org.hime.lang.exception.HimeException
import org.hime.parse.*

/**
 * 执行代码
 * @param env    环境
 * @param code   代码
 * @param symbol 符号表
 * @return       结果
 */
fun call(env: Env, code: String, symbol: SymbolTable = env.symbol): Token {
    return try {
        val codes = splitCode(preprocessor(code))
        var result = env.himeNil
        for (exp in codes)
            result = eval(env, parser(env, lexer(env, exp)), symbol)
        result
    } catch (e: HimeException) {
        env.io.err.print("error: ")
        env.io.err.println(e.message)
        env.himeNil
    }
}
