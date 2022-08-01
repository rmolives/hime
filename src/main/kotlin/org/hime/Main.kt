package org.hime

import org.hime.lang.Env
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val env = Env()
    // 一旦没有任何参数，将进入REPL
    if (args.isEmpty())
        repl(env)
    else if (args[0] == "--version" || args[0] == "-v")
        println("Hime V0.2\nhttps://www.wumoe.org.cn/\nWuMoe Community.")
    else
        call(env, Files.readString(Path.of(args[0])))
}