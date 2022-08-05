package org.hime.core

import org.hime.cast
import org.hime.lang.*
import org.hime.parse.Token
import org.hime.toToken
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

fun initFile(env: Env) {
    env.symbol.table.putAll(
        mutableMapOf(
            "file-exists" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        return File(args[0].toString()).exists().toToken(env)
                    },
                    1
                )
            ).toToken(env),
            "file-list" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        val list = ArrayList<Token>()
                        val files = File(args[0].toString()).listFiles()
                        for (file in files!!)
                            list.add(file.path.toToken(env))
                        return list.toToken(env)
                    },
                    1
                )
            ).toToken(env),
            "file-mkdirs" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        val file = File(args[0].toString())
                        if (!file.parentFile.exists())
                            !file.parentFile.mkdirs()
                        if (!file.exists())
                            file.createNewFile()
                        return env.himeNil
                    },
                    1
                )
            ).toToken(env),
            "file-new" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        val file = File(args[0].toString())
                        if (!file.exists())
                            file.createNewFile()
                        return env.himeNil
                    },
                    1
                )
            ).toToken(env),
            "file-read-string" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        return Files.readString(Paths.get(args[0].toString())).toToken(env)
                    },
                    1
                )
            ).toToken(env),
            "file-remove" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        File(args[0].toString()).delete()
                        return env.himeNil
                    },
                    1
                )
            ).toToken(env),
            "file-write-string" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        val file = File(args[0].toString())
                        if (!file.parentFile.exists())
                            !file.parentFile.mkdirs()
                        if (!file.exists())
                            file.createNewFile()
                        Files.writeString(file.toPath(), args[1].toString())
                        return env.himeNil
                    },
                    2
                )
            ).toToken(env),
            "file-read-bytes" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN,
                    fun(args: List<Token>, _: SymbolTable): Token {
                        val list = ArrayList<Token>()
                        val bytes = Files.readAllBytes(Paths.get(args[0].toString()))
                        for (byte in bytes)
                            list.add(byte.toToken(env))
                        return list.toToken(env)
                    },
                    1
                )
            ).toToken(env),
            "file-write-bytes" to HimeFunctionScheduler(env).add(
                HimeFunction(
                    env,
                    FuncType.BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        val file = File(args[0].toString())
                        if (!file.parentFile.exists())
                            !file.parentFile.mkdirs()
                        if (!file.exists())
                            file.createNewFile()
                        val list = cast<List<Token>>(args[1].value)
                        val bytes = ByteArray(list.size)
                        for (index in list.indices) {
                            himeAssertType(list[index], "byte", env)
                            bytes[index] = cast<Byte>(list[index].value)
                        }
                        Files.write(file.toPath(), bytes)
                        return env.himeNil
                    },
                    listOf(env.getType("any"), env.getType("list")),
                    false
                )
            ).toToken(env)
        )
    )
}