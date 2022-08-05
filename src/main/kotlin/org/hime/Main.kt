package org.hime

import org.hime.lang.Env
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    // 一旦没有任何参数，将进入REPL
    if (args.isEmpty())
        repl()
    else if (args[0] == "--version" || args[0] == "-v")
        println("Hime V0.2\nWuMoe Community.")
    else
        call(Env(), Files.readString(Path.of(args[0])))
}