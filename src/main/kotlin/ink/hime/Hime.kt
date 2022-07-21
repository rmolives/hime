package ink.hime

import ink.hime.core.SymbolTable
import ink.hime.core.core
import ink.hime.core.eval
import ink.hime.parse.*

val defaultSymbolTable = SymbolTable(HashMap(), core)

/**
 * 执行代码
 * @param code   代码
 * @param symbol 符号表
 * @return       结果
 */
fun call(code: String, symbol: SymbolTable = defaultSymbolTable): Token {
    val asts = parser(lexer(preprocessor(code)))
    var result = NIL
    for (ast in asts)
        result = eval(ast, symbol)
    return result
}
