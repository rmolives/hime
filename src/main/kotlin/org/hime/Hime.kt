package org.hime

import org.hime.core.SymbolTable
import org.hime.core.core
import org.hime.core.eval
import org.hime.parse.*

val defaultSymbolTable = SymbolTable(HashMap(), core)

fun call(code: String, symbol: SymbolTable = defaultSymbolTable): Token {
    val asts = parser(lexer(preprocessor(code)))
    var result = NIL
    for (ast in asts)
        result = eval(ast, symbol)
    return result
}
