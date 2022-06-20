package org.hime

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.isEmpty())
        repl()
    else
        call(Files.readString(Path.of(args[0])))
}