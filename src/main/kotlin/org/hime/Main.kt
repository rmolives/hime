package org.hime

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    // Once there's not any args, cli mode will be enabled.
    if (args.isEmpty())
        repl()
    // Otherwise we read a file to interpret it.
    else
        call(Files.readString(Path.of(args[0])))
}