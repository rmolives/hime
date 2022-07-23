package org.hime

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    // 一旦没有任何参数，将进入REPL
    if (args.isEmpty())
        repl()
    else if (args[0] == "--version" || args[0] == "-v")
        println("Hime V0.1\nhttps://www.wumoe.org.cn/\nWuMoe Inc.")
    else
        call(Files.readString(Path.of(args[0])))
}