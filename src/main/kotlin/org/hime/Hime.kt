package org.hime

import org.hime.core.SymbolTable
import org.hime.core.core
import org.hime.parse.*
import java.io.File
import java.nio.file.Files

val defaultSymbolTable = SymbolTable(HashMap(), core)

fun call(code: String, symbolTable: SymbolTable = defaultSymbolTable): Token {
    val asts = parser(lexer(format(code)))
    var result = NIL
    for (ast in asts)
        result = org.hime.core.call(ast, symbolTable)
    return result
}

fun call(file: File, symbolTable: SymbolTable = defaultSymbolTable): Token {
    return call(Files.readString(file.toPath()), symbolTable)
}
