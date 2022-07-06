package org.hime

import org.hime.core.SymbolTable
import org.hime.core.core
import org.hime.parse.Type
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

fun repl() {
    val reader = BufferedReader(InputStreamReader(System.`in`))
    var codeBuilder = StringBuilder()
    var symbolTable = SymbolTable(HashMap(), core)
    var size = 0
    while (true) {
        print("[Hime] >>> ")
        if (size > 0)
            codeBuilder.append(" ")
        while (size-- > 0)
            print("    ")
        var index = 0
        var flag = 0
        val builder = StringBuilder()
        val read = reader.readLine()
        if (read.startsWith(":clear"))
            symbolTable = SymbolTable(HashMap(), core)
        else if (read.startsWith(":load"))
            builder.append(Files.readString(Path.of(read.substring(6))))
        else {
            codeBuilder.append(read)
            val code = codeBuilder.toString()
            while (code[index] != '(')
                ++index
            do {
                if (code[index] == '\"') {
                    builder.append("\"")
                    val value = StringBuilder()
                    var skip = false
                    while (true) {
                        ++index
                        if (index < code.length - 1 && code[index] == '\\') {
                            if (skip) {
                                skip = false
                                value.append("\\\\")
                            } else
                                skip = true
                            continue
                        } else if (index >= code.length - 1 || code[index] == '\"') {
                            if (skip) {
                                skip = false
                                value.append("\\\"")
                                continue
                            } else
                                break
                        }
                        value.append(code[index])
                    }
                    builder.append(value.append("\"").toString())
                    ++index
                    continue
                }
                if (code[index] == '(')
                    ++flag
                else if (code[index] == ')')
                    --flag
                builder.append(code[index++])
            } while (index < code.length)
        }
        if (flag == 0) {
            codeBuilder = StringBuilder()
            val result = call(builder.toString(), symbolTable)
            if (result.type != Type.NIL)
                println(result.toString())
        }
        size = flag
    }
}