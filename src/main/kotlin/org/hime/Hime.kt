package org.hime

import org.hime.core.SymbolTable
import org.hime.core.core
import org.hime.parse.*

var symbolTable = SymbolTable(HashMap(), core)

fun call(code: String): Token {
    val asts = parser(lexer(format(code)))
    var result = NIL
    for (ast in asts)
        result = org.hime.core.call(ast, symbolTable)
    return result
}