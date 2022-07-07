package org.hime

import org.hime.core.SymbolTable
import org.hime.core.core
import org.hime.core.eval
import org.hime.parse.*

val defaultSymbolTable = SymbolTable(HashMap(), core)

fun call(code: String, symbol: SymbolTable = defaultSymbolTable): Token {
    // Three steps to obtain the AST tree:
    //   1st. Preprocess the code.
    //   2nd. Use lexer to scan the whole code, returning a sequence of lexemes
    //   3rd. Use parser
    val asts = parser(lexer(preprocessor(code)))
    var result = NIL
    // Iterate the AST
    for (ast in asts)
        result = eval(ast, symbol)
    return result
}
