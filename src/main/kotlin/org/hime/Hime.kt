package org.hime

import org.hime.core.SymbolTable
import org.hime.core.eval
import org.hime.lang.Env
import org.hime.parse.*

/**
 * 执行代码
 * @param env    环境
 * @param code   代码
 * @param symbol 符号表
 * @return       结果
 */
fun call(env: Env, code: String, symbol: SymbolTable = env.symbols): Token {
    val asts = parser(env, lexer(env, preprocessor(code)))
    var result = env.himeNil
    for (ast in asts)
        result = eval(env, ast, symbol)
    return result
}
