package org.hime

import org.hime.lang.Env
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * REPL
 */
fun repl() {
    val reader = BufferedReader(InputStreamReader(System.`in`))
    var codeBuilder = StringBuilder()
    var env = Env()
    var size = 0
    while (true) {
        env.io.out.print("[Hime] >>> ")
        if (size > 0)
            codeBuilder.append(" ")
        while (size-- > 0)
            env.io.out.print("    ")
        var index = 0
        var flag = 0
        val read = reader.readLine()
        if (read.startsWith(":clear"))
            env = Env()
        else if (read.startsWith(":load"))
            codeBuilder.append(Files.readString(Path.of(read.substring(6))))
        else {
            codeBuilder.append(read)
            val code = codeBuilder.toString()
            while (code[index] != '(')
                ++index
            do {
                if (code[index] == '\"') {
                    var skip = false
                    while (true) {
                        ++index
                        if (index < code.length - 1 && code[index] == '\\') {
                            skip = false
                            continue
                        } else if (index >= code.length - 1 || code[index] == '\"') {
                            if (skip) {
                                skip = false
                                continue
                            } else
                                break
                        } else if (skip) {
                            skip = false
                            continue
                        }
                    }
                    ++index
                    continue
                }
                if (code[index] == '(')
                    ++flag
                else if (code[index] == ')')
                    --flag
                ++index
            } while (index < code.length)
        }
        if (flag == 0) {
            val result = call(env, codeBuilder.toString())
            codeBuilder = StringBuilder()
            if (result != env.himeNil)
                env.io.out.println(result.toString())
        }
        size = flag
    }
}