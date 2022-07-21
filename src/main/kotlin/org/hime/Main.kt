package org.hime

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    // 一旦没有任何参数，将进入REPL
    if (args.isEmpty())
        repl()
    else
        call(Files.readString(Path.of(args[0])))
}