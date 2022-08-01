package org.hime.core

import ch.obermuhlner.math.big.BigDecimalMath
import org.hime.call
import org.hime.cast
import org.hime.core.FuncType.BUILT_IN
import org.hime.core.FuncType.STATIC
import org.hime.lang.HimeRuntimeException
import org.hime.lang.himeAssertRuntime
import org.hime.lang.*
import org.hime.parse.*
import org.hime.toToken
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

fun initCore(env: Env) {
    env.symbols.table.putAll(
        mutableMapOf(
            "true" to env.himeTrue,
            "false" to env.himeFalse,
            "nil" to env.himeNil,
            "empty-stream" to env.himeEmptyStream,
            "def-symbol" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                himeAssertRuntime(!symbol.table.containsKey(ast[0].tok.toString())) { "repeat binding ${ast[0].tok}." }
                himeAssertRuntime(ast[0].isNotEmpty()) { "Irregular definition." }
                val parameters = ArrayList<String>()
                for (i in 0 until ast[0].size())
                    parameters.add(ast[0][i].tok.toString())
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.put(
                    ast[0].tok.toString(),
                    (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                        if (ast.type == AstType.FUNCTION) {
                            var result = env.himeNil
                            val newSymbol = symbol.createChild()
                            for (node in ast.children)
                                result = eval(env, ASTNode(eval(env, node, newSymbol)), newSymbol)
                            return result
                        }
                        val newAsts = ArrayList<ASTNode>()
                        for (node in asts) {
                            val newAst = node.copy()

                            // 递归替换宏
                            fun rsc(ast: ASTNode, id: String, value: ASTNode) {
                                if (env.isType(ast.tok, env.getType("id")) && ast.tok.toString() == id) {
                                    ast.tok = value.tok
                                    ast.children = value.children
                                }
                                for (child in ast.children)
                                    rsc(child, id, value)
                            }
                            himeAssertRuntime(ast.size() >= parameters.size) { "" }
                            for (i in parameters.indices)
                                rsc(newAst, parameters[i], ast[i])
                            newAsts.add(newAst)
                        }
                        val newSymbol = symbol.createChild()
                        var result = env.himeNil
                        for (astNode in newAsts)
                            result = eval(env, astNode.copy(), newSymbol)
                        return result
                    })).toToken(env)
                )
                return env.himeNil
            })).toToken(env),
            "cons-stream" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                // 对(cons-stream t1 t2)中的t1进行求值
                val t1 = eval(env, ast[0], symbol.createChild())
                val asts = ArrayList<ASTNode>()
                // 对t1后面的内容都作为begin添加到asts中
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                // 建立过程，类似(delay t2*)
                return arrayListOf(
                    t1,
                    structureHimeFunction(env, arrayListOf(), arrayListOf(), asts, symbol.createChild())
                ).toToken(env)
            })).toToken(env),
            "stream-car" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                // 因为(cons-stream t1 t2)的t1已经被求值，所以就直接返回
                return cast<List<Token>>(args[0].value)[0]
            }, listOf(env.getType("list")), false)).toToken(env),
            "stream-cdr" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = cast<List<Token>>(args[0].value)
                val list = ArrayList<Token>()
                // 将stream-cdr传入的列表，除去第1个外的所有元素都应用force，即便(cons-stream t1 t2)只返回包含2个元素的列表
                for (i in 1 until tokens.size) {
                    himeAssertRuntime(
                        env.isType(
                            tokens[i],
                            env.getType("function")
                        )
                    ) { "${tokens[i]} is not function." }
                    list.add(cast<HimeFunction>(tokens[i].value).call(arrayListOf()))
                }
                // 如果列表中只存在一个元素，那么就返回这个元素
                if (list.size == 1)
                    return list[0].toToken(env)
                // 否则返回整个列表
                return list.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "stream-map" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                if (args[1] == env.himeEmptyStream)
                    return env.himeEmptyStream
                himeAssertRuntime(env.isType(args[1], env.getType("list"))) { "${args[1]} is not list." }
                // 每个list只包含2个元素，一个已经求值，一个待求值，这里包含(stream-map function list*)的所有list
                var lists = ArrayList<List<Token>>()
                // 将所有list添加到lists中
                for (i in 1 until args.size)
                    lists.add(cast<List<Token>>(args[i].value))
                val result = ArrayList<Token>()
                // 直到遇见env.himeEmptyStream才结束
                top@ while (true) {
                    // 例如对于(stream-map f (stream-cons a b) (stream-cons c d))，则执行(f a c)等
                    val parameters = ArrayList<Token>()
                    // 将所有首项添加到parameters
                    for (list in lists)
                        parameters.add(list[0])
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    for (arg in parameters)
                        asts.add(ASTNode(arg))
                    // 将parameters按匹配的类型添加到函数中并执行
                    result.add(cast<HimeFunction>(args[0].value).call( asts, symbol))
                    val temp = ArrayList<List<Token>>()
                    // 重新计算lists，并应用delay
                    for (list in lists) {
                        val t = cast<HimeFunction>(list[1].value).call( arrayListOf())
                        if (t == env.himeEmptyStream)
                            break@top
                        temp.add(cast<List<Token>>(t.value))
                    }
                    lists = temp
                }
                return result.toToken(env)
            }, listOf(env.getType("list"), env.getType("any")), true)).toToken(env),
            "stream-for-each" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                if (args[1] == env.himeEmptyStream)
                    return env.himeEmptyStream
                himeAssertRuntime(env.isType(args[1], env.getType("list"))) { "${args[1]} is not list." }
                // 每个list只包含2个元素，一个已经求值，一个待求值，这里包含(stream-map function list*)的所有list
                var lists = ArrayList<List<Token>>()
                // 将所有list添加到lists中
                for (i in 1 until args.size)
                    lists.add(cast<List<Token>>(args[i].value))
                // 直到遇见env.himeEmptyStream才结束
                top@ while (true) {
                    // 例如对于(stream-map f (stream-cons a b) (stream-cons c d))，则执行(f a c)等
                    val parameters = ArrayList<Token>()
                    // 将所有首项添加到parameters
                    for (list in lists)
                        parameters.add(list[0])
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    for (arg in parameters)
                        asts.add(ASTNode(arg))
                    // 将parameters按匹配的类型添加到函数中并执行
                    cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                    val temp = ArrayList<List<Token>>()
                    // 重新计算lists，并应用delay
                    for (list in lists) {
                        himeAssertRuntime(
                            env.isType(
                                list[1],
                                env.getType("function")
                            )
                        ) { "${list[1]} is not function." }
                        val t = cast<HimeFunction>(list[1].value).call( arrayListOf())
                        if (t == env.himeEmptyStream)
                            break@top
                        temp.add(cast<List<Token>>(t.value))
                    }
                    lists = temp
                }
                return env.himeNil
            }, listOf(env.getType("list"), env.getType("any")), true)).toToken(env),
            "stream-filter" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                if (args[1] == env.himeEmptyStream)
                    return arrayListOf<Token>().toToken(env)
                himeAssertRuntime(env.isType(args[1], env.getType("list"))) { "${args[1]} is not list." }
                val result = ArrayList<Token>()
                var tokens = cast<List<Token>>(args[1].value)
                while (tokens[0].value != env.himeEmptyStream) {
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    asts.add(ASTNode(tokens[0]))
                    val op = cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                    himeAssertRuntime(env.isType(op, env.getType("bool"))) { "$op is not bool." }
                    if (cast<Boolean>(op.value))
                        result.add(tokens[0])
                    val temp = cast<HimeFunction>(tokens[1].value).call( arrayListOf())
                    if (temp == env.himeEmptyStream)
                        break
                    tokens = cast<List<Token>>(temp.value)
                }
                return result.toToken(env)
            }, listOf(env.getType("function")), true)).toToken(env),
            "stream-ref" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.size > 1) { "not enough arguments." }
                himeAssertRuntime(env.isType(args[0], env.getType("list"))) { "${args[0]} is not list." }
                himeAssertRuntime(env.isType(args[1], env.getType("int"))) { "${args[1]} is not int." }
                var temp = cast<List<Token>>(args[0].value)
                var index = args[1].value.toString().toInt()
                while ((index--) != 0) {
                    himeAssertRuntime(env.isType(temp[1], env.getType("function"))) { "${temp[1]} is not function." }
                    temp = cast<List<Token>>(cast<HimeFunction>(temp[1].value).call( arrayListOf()).value)
                }
                return temp[0]
            }, listOf(env.getType("list"), env.getType("any")), false)).toToken(env),
            // (delay e) => (lambda () e)
            "delay" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
                val asts = ArrayList<ASTNode>()
                for (i in 0 until ast.size())
                    asts.add(ast[i].copy())
                return structureHimeFunction(env, arrayListOf(), arrayListOf(), asts, symbol.createChild())
            })).toToken(env),
            // (force d) => (d)
            "force" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var result = env.himeNil
                for (token in args) {
                    himeAssertRuntime(env.isType(token, env.getType("function"))) { "$token is not function." }
                    result = cast<HimeFunction>(token.value).call( arrayListOf())
                }
                return result
            }, listOf(env.getType("any")), true)).toToken(env),
            // 局部变量绑定
            "let" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
                // 新建执行的新环境（继承）
                val newSymbol = symbol.createChild()
                var result = env.himeNil
                for (node in ast[0].children) {
                    if (node.tok.toString() == "apply") {
                        val parameters = ArrayList<String>()
                        val paramTypes = ArrayList<HimeType>()
                        for (i in 0 until ast[0].size()) {
                            himeAssertRuntime(
                                env.isType(
                                    ast[0][i].tok,
                                    env.getType("id")
                                )
                            ) { "${ast[0][i].tok} is not id." }
                            parameters.add(ast[0][i].tok.toString())
                            paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                        }
                        val asts = ArrayList<ASTNode>()
                        for (i in 1 until node.size())
                            asts.add(node[i].copy())
                        // 这里采用原环境的继承，因为let不可互相访问
                        newSymbol.put(
                            node[0].tok.toString(),
                            structureHimeFunction(env, parameters, paramTypes, asts, symbol.createChild())
                        )
                    } else {
                        var value = env.himeNil
                        for (e in node.children)
                            value = eval(env, e.copy(), symbol.createChild())
                        val type = cast<HimeTypeId>(node.tok.type).type
                        himeAssertRuntime(env.isType(value, type)) { "$value is not ${type.name}." }
                        newSymbol.put(node.tok.toString(), value)
                    }
                }
                for (i in 1 until ast.size())
                    result = eval(env, ast[i].copy(), newSymbol.createChild())
                return result
            })).toToken(env),
            "let*" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
                // 新建执行的新环境（继承）
                val newSymbol = symbol.createChild()
                var result = env.himeNil
                for (node in ast[0].children) {
                    if (node.tok.toString() == "apply") {
                        val parameters = ArrayList<String>()
                        val paramTypes = ArrayList<HimeType>()
                        for (i in 0 until ast[0].size()) {
                            himeAssertRuntime(
                                env.isType(
                                    ast[0][i].tok,
                                    env.getType("id")
                                )
                            ) { "${ast[0][i].tok} is not id." }
                            parameters.add(ast[0][i].tok.toString())
                            paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                        }
                        val asts = ArrayList<ASTNode>()
                        for (i in 1 until node.size())
                            asts.add(node[i].copy())
                        // 这里采用新环境的继承，因为let*可互相访问
                        newSymbol.put(
                            node[0].tok.toString(),
                            structureHimeFunction(env, parameters, paramTypes, asts, newSymbol.createChild())
                        )
                    } else {
                        var value = env.himeNil
                        for (e in node.children)
                            value = eval(env, e.copy(), newSymbol.createChild())
                        val type = cast<HimeTypeId>(node.tok.type).type
                        himeAssertRuntime(env.isType(value, type)) { "$value is not ${type.name}." }
                        newSymbol.put(node.tok.toString(), value)
                    }
                }
                for (i in 1 until ast.size())
                    result = eval(env, ast[i].copy(), newSymbol.createChild())
                return result
            })).toToken(env),
            // 建立新绑定
            "def" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                himeAssertRuntime(!symbol.table.containsKey(ast[0].tok.toString())) { "repeat binding ${ast[0].tok}." }
                // 如果是(def key value)
                if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                    var result = env.himeNil
                    for (i in 1 until ast.size())
                        result = eval(env, ast[i], symbol.createChild())
                    val type = cast<HimeTypeId>(ast[0].tok.type).type
                    himeAssertRuntime(env.isType(result, type)) { "$result is not ${type.name}." }
                    symbol.put(ast[0].tok.toString(), result)
                }
                // 如果是(def (function-name p*) e)
                else {
                    val parameters = ArrayList<String>()
                    val paramTypes = ArrayList<HimeType>()
                    for (i in 0 until ast[0].size()) {
                        himeAssertRuntime(
                            env.isType(
                                ast[0][i].tok,
                                env.getType("id")
                            )
                        ) { "${ast[0][i].tok} is not id." }
                        parameters.add(ast[0][i].tok.toString())
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                    }
                    val asts = ArrayList<ASTNode>()
                    // 将ast都复制一遍并存到asts中
                    for (i in 1 until ast.size())
                        asts.add(ast[i].copy())
                    symbol.put(
                        ast[0].tok.toString(),
                        structureHimeFunction(env, parameters, paramTypes, asts, symbol.createChild())
                    )
                }
                return env.himeNil
            })).toToken(env),
            // 建立新绑定(变长)
            "def-variadic" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                himeAssertRuntime(!symbol.table.containsKey(ast[0].tok.toString())) { "repeat binding ${ast[0].tok}." }
                himeAssertRuntime(ast[0].isNotEmpty() || ast[0].type != AstType.FUNCTION) { "format error." }
                val parameters = ArrayList<String>()
                val paramTypes = ArrayList<HimeType>()
                for (i in 0 until ast[0].size()) {
                    himeAssertRuntime(env.isType(ast[0][i].tok, env.getType("id"))) { "${ast[0][i].tok} is not id." }
                    parameters.add(ast[0][i].tok.toString())
                    if (i != ast[0].size() - 1)
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                }
                val asts = ArrayList<ASTNode>()
                // 将ast都复制一遍并存到asts中
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.put(
                    ast[0].tok.toString(),
                    variableHimeFunction(env, parameters, paramTypes, asts, symbol.createChild())
                )
                return env.himeNil
            })).toToken(env),
            // 解除绑定
            "undef" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
                himeAssertRuntime(symbol.contains(ast[0].tok.toString())) { "environment does not contain ${ast[0].tok} binding." }
                // 从环境中删除绑定
                symbol.remove(ast[0].tok.toString())
                return env.himeNil
            })).toToken(env),
            // 更改绑定
            "set!" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                himeAssertRuntime(symbol.contains(ast[0].tok.toString())) { "environment does not contain ${ast[0].tok} binding." }
                // 如果是(set key value)
                if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                    var result = env.himeNil
                    for (i in 1 until ast.size())
                        result = eval(env, ast[i], symbol.createChild())
                    val type = cast<HimeTypeId>(ast[0].tok.type).type
                    himeAssertRuntime(env.isType(result, type)) { "$result is not ${type.name}." }
                    symbol.set(ast[0].tok.toString(), result)
                } else {
                    // 如果是(set (function-name p*) e)
                    val parameters = ArrayList<String>()
                    val paramTypes = ArrayList<HimeType>()
                    for (i in 0 until ast[0].size()) {
                        himeAssertRuntime(
                            env.isType(
                                ast[0][i].tok,
                                env.getType("id")
                            )
                        ) { "${ast[0][i].tok} is not id." }
                        parameters.add(ast[0][i].tok.toString())
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                    }
                    val asts = ArrayList<ASTNode>()
                    // 将ast都复制一遍并存到asts中
                    for (i in 1 until ast.size())
                        asts.add(ast[i].copy())
                    symbol.set(ast[0].tok.toString(), structureHimeFunction(env, parameters, paramTypes, asts, symbol))
                }
                return env.himeNil
            })).toToken(env),
            "set-variadic!" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                himeAssertRuntime(symbol.contains(ast[0].tok.toString())) { "environment does not contain ${ast[0].tok} binding." }
                himeAssertRuntime(ast[0].isNotEmpty() || ast[0].type != AstType.FUNCTION) { "format error." }
                val parameters = ArrayList<String>()
                val paramTypes = ArrayList<HimeType>()
                for (i in 0 until ast[0].size()) {
                    himeAssertRuntime(env.isType(ast[0][i].tok, env.getType("id"))) { "${ast[0][i].tok} is not id." }
                    parameters.add(ast[0][i].tok.toString())
                    if (i != ast[0].size() - 1)
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                }
                val asts = ArrayList<ASTNode>()
                // 将ast都复制一遍并存到asts中
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.set(
                    ast[0].tok.toString(),
                    variableHimeFunction(env, parameters, paramTypes, asts, symbol.createChild())
                )
                return env.himeNil
            })).toToken(env),
            "lambda" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                val parameters = ArrayList<String>()
                val paramTypes = ArrayList<HimeType>()
                // 判断非(lambda () e)
                if (ast[0].tok != env.himeEmptyStream) {
                    himeAssertRuntime(env.isType(ast[0].tok, env.getType("id"))) { "${ast[0].tok} is not id." }
                    parameters.add(ast[0].tok.toString())
                    paramTypes.add(cast<HimeTypeId>(ast[0].tok.type).type)
                    for (i in 0 until ast[0].size()) {
                        himeAssertRuntime(
                            env.isType(
                                ast[0][i].tok,
                                env.getType("id")
                            )
                        ) { "${ast[0][i].tok} is not id." }
                        parameters.add(ast[0][i].tok.toString())
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                    }
                }
                val asts = ArrayList<ASTNode>()
                // 将ast都复制一遍并存到asts中
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                return structureHimeFunction(env, parameters, paramTypes, asts, symbol.createChild())
            })).toToken(env),
            "if" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                // 新建执行的新环境（继承）
                val newSymbol = symbol.createChild()
                // 执行condition
                val condition = eval(env, ast[0], newSymbol)
                himeAssertRuntime(env.isType(condition, env.getType("bool"))) { "$condition is not bool." }
                // 分支判断
                if (cast<Boolean>(condition.value))
                    return eval(env, ast[1].copy(), newSymbol)
                else if (ast.size() > 2)
                    return eval(env, ast[2].copy(), newSymbol)
                return env.himeNil
            })).toToken(env),
            "cond" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                // 新建执行的新环境（继承）
                val newSymbol = symbol.createChild()
                for (node in ast.children) {
                    // 如果碰到else，就直接执行返回
                    if (env.isType(node.tok, env.getType("id")) && node.tok.toString() == "else")
                        return eval(env, node[0].copy(), newSymbol)
                    else {
                        val result = eval(env, node[0].copy(), newSymbol)
                        himeAssertRuntime(env.isType(result, env.getType("bool"))) { "$result is not bool." }
                        if (cast<Boolean>(result.value)) {
                            var r = env.himeNil
                            for (index in 1 until node.size())
                                r = eval(env, node[index].copy(), newSymbol)
                            return r
                        }
                    }
                }
                return env.himeNil
            })).toToken(env),
            "switch" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
                val newSymbol = symbol.createChild()
                val op = eval(env, ast[0].copy(), newSymbol)
                for (index in 1 until ast.size()) {
                    val node = ast[index]
                    if (env.isType(node.tok, env.getType("id")) && node.tok.toString() == "else")
                        return eval(env, node.copy(), newSymbol)
                    else
                        if (node.tok == op) {
                            var r = env.himeNil
                            for (i in 0 until node.size())
                                r = eval(env, node[i].copy(), newSymbol)
                            return r
                        }
                }
                return env.himeNil
            })).toToken(env),
            // 执行多个组合式
            "begin" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                // 新建执行的新环境（继承）
                val newSymbol = symbol.createChild()
                var result = env.himeNil
                for (i in 0 until ast.size())
                    result = eval(env, ast[i].copy(), newSymbol)
                return result
            })).toToken(env),
            "while" to (HimeFunction(env, STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                // 新建执行的新环境（继承）
                val newSymbol = symbol.createChild()
                var result = env.himeNil
                // 执行condition
                var condition = eval(env, ast[0].copy(), newSymbol)
                himeAssertRuntime(env.isType(condition, env.getType("bool"))) { "$condition is not bool." }
                while (cast<Boolean>(condition.value)) {
                    for (i in 1 until ast.size())
                        result = eval(env, ast[i].copy(), newSymbol)
                    // 重新执行condition
                    condition = eval(env, ast[0].copy(), newSymbol)
                    himeAssertRuntime(env.isType(condition, env.getType("bool"))) { "$condition is not bool." }
                }
                return result
            })).toToken(env),
            "def-type" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                env.addType(HimeType(args[0].toString()), args.subList(1, args.size).map {
                    himeAssertRuntime(env.isType(it, env.getType("type"))) { "$it is not num." }
                    env.getType(it.toString())
                })
                return env.himeNil
            }, listOf(env.getType("string")), true)).toToken(env),
            "cast" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return Token(cast<HimeType>(args[1].value), args[0].value)
            }, listOf(env.getType("any"), env.getType("type")), true)).toToken(env),
            "->type" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return env.getType(args[0].toString()).toToken(env)
            }, listOf(env.getType("string")), false)).toToken(env),
            "apply" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val parameters = ArrayList<Token>()
                for (i in 1 until args.size)
                    parameters.add(args[i])
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = env.himeAstEmpty.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                return cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
            }, listOf(env.getType("function")), true)).toToken(env),
            "apply-list" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val parameters = cast<List<Token>>(args[1].value)
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = env.himeAstEmpty.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                return cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
            }, listOf(env.getType("function")), true)).toToken(env),
            "require" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val path = args[0].toString()
                // 导入内置的模块
                if (module.containsKey(path)) {
                    module[path]!!(env)
                    return env.himeNil
                }
                // 导入工作目录的模块
                val file = File(System.getProperty("user.dir") + "/" + path.replace(".", "/") + ".hime")
                if (file.exists())
                    for (node in parser(env, lexer(env, preprocessor(Files.readString(file.toPath())))))
                        eval(env, node, symbol)
                return env.himeNil
            }, 1)).toToken(env),
            "read-bit" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                return (symbol.io?.`in` ?: System.`in`).read().toToken(env)
            }, 0)).toToken(env),
            "read-line" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                return Scanner(symbol.io?.`in` ?: System.`in`).nextLine().toToken(env)
            }, 0)).toToken(env),
            "read" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                return Scanner(symbol.io?.`in` ?: System.`in`).next().toToken(env)
            }, 0)).toToken(env),
            "read-num" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                return Scanner(symbol.io?.`in` ?: System.`in`).nextBigInteger().toToken(env)
            }, 0)).toToken(env),
            "read-real" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                return Scanner(symbol.io?.`in` ?: System.`in`).nextBigDecimal().toToken(env)
            }, 0)).toToken(env),
            "read-bool" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                return Scanner(symbol.io?.`in` ?: System.`in`).nextBoolean().toToken(env)
            }, 0)).toToken(env),
            "println" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val builder = StringBuilder()
                for (token in args)
                    builder.append(token.toString())
                (symbol.io?.out ?: System.out).println(builder.toString())
                return env.himeNil
            })).toToken(env),
            "print" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val builder = StringBuilder()
                for (token in args)
                    builder.append(token.toString())
                (symbol.io?.out ?: System.out).print(builder.toString())
                return env.himeNil
            })).toToken(env),
            "newline" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                (symbol.io?.out ?: System.out).println()
                return env.himeNil
            }, 0)).toToken(env),
            "println-error" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val builder = StringBuilder()
                for (token in args)
                    builder.append(token.toString())
                (symbol.io?.err ?: System.err).println(builder.toString())
                return env.himeNil
            })).toToken(env),
            "print-error" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val builder = StringBuilder()
                for (token in args)
                    builder.append(token.toString())
                (symbol.io?.err ?: System.err).print(builder.toString())
                return env.himeNil
            })).toToken(env),
            "newline-error" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
                (symbol.io?.err ?: System.err).println()
                return env.himeNil
            }, 0)).toToken(env),
            "+" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var num = args[0]
                for (i in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[i], env.getType("num"))) { "${args[i]} is not num." }
                    num = env.himeAdd(num, args[i])
                }
                return num
            }, listOf(env.getType("op")), true)).toToken(env),
            "-" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var num = args[0]
                if (args.size == 1)
                    return env.himeSub(
                        num,
                        env.himeMult(num, BigInteger.TWO.toToken(env))
                    )
                for (i in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[i], env.getType("num"))) { "${args[i]} is not num." }
                    num = env.himeSub(num, args[i])
                }
                return num.toToken(env)
            }, listOf(env.getType("op")), true)).toToken(env),
            "*" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var num = args[0]
                for (i in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[i], env.getType("num"))) { "${args[i]} is not num." }
                    num = env.himeMult(num, args[i])
                }
                return num
            }, listOf(env.getType("op")), true)).toToken(env),
            "/" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var num = args[0]
                for (i in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[i], env.getType("num"))) { "${args[i]} is not num." }
                    num = env.himeDiv(num, args[i])
                }
                return num
            }, listOf(env.getType("op")), true)).toToken(env),
            "and" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                for (arg in args) {
                    himeAssertRuntime(env.isType(arg, env.getType("bool"))) { "$arg is not bool." }
                    if (!cast<Boolean>(arg.value))
                        return env.himeFalse
                }
                return env.himeTrue
            }, listOf(env.getType("bool")), true)).toToken(env),
            "or" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args) {
                    himeAssertRuntime(env.isType(arg, env.getType("bool"))) { "$arg is not bool." }
                    if (cast<Boolean>(arg.value))
                        return env.himeTrue
                }
                return env.himeFalse
            }, listOf(env.getType("bool")), true)).toToken(env),
            "not" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                himeAssertRuntime(env.isType(args[0], env.getType("bool"))) { "${args[0]} is not bool." }
                return if (cast<Boolean>(args[0].value)) env.himeFalse else env.himeTrue
            }, listOf(env.getType("bool")), false)).toToken(env),
            "=" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                for (i in args.indices) {
                    himeAssertRuntime(env.isType(args[i], env.getType("eq"))) { "${args[i]} is not eq." }
                    for (j in args.indices) {
                        himeAssertRuntime(env.isType(args[j], env.getType("eq"))) { "${args[j]} is not eq." }
                        if (i != j && !cast<Boolean>(env.himeEq(args[i], args[j])))
                            return env.himeFalse
                    }
                }
                return env.himeTrue
            }, listOf(env.getType("eq")), true)).toToken(env),
            "/=" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                for (i in args.indices) {
                    himeAssertRuntime(env.isType(args[i], env.getType("ord"))) { "${args[i]} is not eq." }
                    for (j in args.indices) {
                        himeAssertRuntime(env.isType(args[j], env.getType("ord"))) { "${args[j]} is not eq." }
                        if (i != j && cast<Boolean>(env.himeEq(args[i], args[j])))
                            return env.himeFalse
                    }
                }
                return env.himeTrue
            }, listOf(env.getType("eq")), true)).toToken(env),
            ">" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                var token = args[0]
                for (index in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[index], env.getType("ord"))) { "${args[1]} is not ord." }
                    if (env.himeLessOrEq(token, args[index]))
                        return env.himeFalse
                    token = args[index]
                }
                return env.himeTrue
            }, listOf(env.getType("ord")), true)).toToken(env),
            "<" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                var token = args[0]
                for (index in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[index], env.getType("ord"))) { "${args[1]} is not ord." }
                    if (env.himeGreaterOrEq(token, args[index]))
                        return env.himeFalse
                    token = args[index]
                }
                return env.himeTrue
            }, listOf(env.getType("ord")), true)).toToken(env),
            ">=" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                var token = args[0]
                for (index in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[index], env.getType("ord"))) { "${args[1]} is not ord." }
                    if (env.himeLess(token, args[index]))
                        return env.himeFalse
                    token = args[index]
                }
                return env.himeTrue
            }, listOf(env.getType("ord")), true)).toToken(env),
            "<=" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                var token = args[0]
                for (index in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[index], env.getType("ord"))) { "${args[1]} is not ord." }
                    if (env.himeGreater(token, args[index]))
                        return env.himeFalse
                    token = args[index]
                }
                return env.himeTrue
            }, listOf(env.getType("ord")), true)).toToken(env),
            "random" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(env.isType(args[0], env.getType("num"))) { "${args[0]} is not num." }
                if (args.size > 1)
                    himeAssertRuntime(env.isType(args[1], env.getType("num"))) { "${args[1]} is not num." }
                val start = if (args.size == 1) BigInteger.ZERO else BigInteger(args[0].toString())
                val end =
                    if (args.size == 1) BigInteger(args[0].toString()) else BigInteger(args[1].toString())
                val rand = Random()
                val scale = end.toString().length
                var generated = ""
                for (i in 0 until end.toString().length)
                    generated += rand.nextInt(10)
                val inputRangeStart = BigDecimal("0").setScale(scale, RoundingMode.FLOOR)
                val inputRangeEnd =
                    BigDecimal(String.format("%0" + end.toString().length + "d", 0).replace('0', '9')).setScale(
                        scale,
                        RoundingMode.FLOOR
                    )
                val outputRangeStart = BigDecimal(start).setScale(scale, RoundingMode.FLOOR)
                val outputRangeEnd = BigDecimal(end).add(BigDecimal("1"))
                    .setScale(scale, RoundingMode.FLOOR)
                val bd1 =
                    BigDecimal(BigInteger(generated)).setScale(scale, RoundingMode.FLOOR).subtract(inputRangeStart)
                val bd2 = inputRangeEnd.subtract(inputRangeStart)
                val bd3 = bd1.divide(bd2, RoundingMode.FLOOR)
                val bd4 = outputRangeEnd.subtract(outputRangeStart)
                val bd5 = bd3.multiply(bd4)
                val bd6 = bd5.add(outputRangeStart)
                var returnInteger = bd6.setScale(0, RoundingMode.FLOOR).toBigInteger()
                returnInteger =
                    if (returnInteger > end) end else returnInteger
                return returnInteger.toToken(env)
            }, listOf(env.getType("int")), true)).toToken(env),
            "cons" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return ArrayList(args).toToken(env)
            }, 2)).toToken(env),
            "car" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<List<Token>>(args[0].value)[0]
            }, listOf(env.getType("list")), false)).toToken(env),
            "cdr" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = cast<List<Token>>(args[0].value)
                val list = ArrayList<Token>()
                // 例如(cdr (list a b c d))将返回(list b c d)
                for (i in 1 until tokens.size)
                    list.add(tokens[i])
                if (list.size == 1)
                    return list[0].toToken(env)
                return list.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "list" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args.toToken(env)
            })).toToken(env),
            "head" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val list = cast<List<Token>>(args[0].value)
                if (list.isEmpty())
                    return env.himeNil
                return list[0]
            }, listOf(env.getType("list")), false)).toToken(env),
            "last" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = cast<List<Token>>(args[0].value)
                return tokens[tokens.size - 1]
            }, listOf(env.getType("list")), false)).toToken(env),
            "tail" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = cast<List<Token>>(args[0].value)
                val list = ArrayList<Token>()
                for (i in 1 until tokens.size)
                    list.add(tokens[i])
                if (list.size == 1)
                    return arrayListOf(list[0]).toToken(env)
                return list.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "init" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = cast<List<Token>>(args[0].value)
                val list = ArrayList<Token>()
                for (i in 0 until tokens.size - 1)
                    list.add(tokens[i])
                if (list.size == 1)
                    return arrayListOf(list[0]).toToken(env)
                return list.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "list-contains" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<List<Token>>(args[0].value).contains(args[1]).toToken(env)
            }, listOf(env.getType("list"), env.getType("any")), false)).toToken(env),
            "list-remove" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = ArrayList(cast<List<Token>>(args[0].value))
                tokens.removeAt(args[1].value.toString().toInt())
                return tokens.toToken(env)
            }, listOf(env.getType("list"), env.getType("int")), false)).toToken(env),
            "list-set" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val index = args[1].value.toString().toInt()
                val tokens = ArrayList(cast<List<Token>>(args[0].value))
                himeAssertRuntime(index < tokens.size) { "index error." }
                tokens[index] = args[2]
                return tokens.toToken(env)
            }, listOf(env.getType("list"), env.getType("int"), env.getType("any")), false)).toToken(env),
            "list-add" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = ArrayList(cast<List<Token>>(args[0].value))
                if (args.size > 2) {
                    himeAssertRuntime(env.isType(args[1], env.getType("int"))) { "${args[1]} is not int." }
                    tokens.add(args[1].value.toString().toInt(), args[2])
                } else
                    tokens.add(args[1])
                return tokens.toToken(env)
            }, listOf(env.getType("list"), env.getType("any")), true)).toToken(env),
            "list-remove!" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<MutableList<Token>>(args[0].value).removeAt(args[1].value.toString().toInt())
                    return args[0].toToken(env)
                },
                listOf(env.getType("list"), env.getType("int")),
                false
            )).toToken(env),
            "list-set!" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<MutableList<Token>>(args[0].value)[args[1].value.toString().toInt()] = args[2]
                    return args[0].toToken(env)
                },
                listOf(env.getType("list"), env.getType("int"), env.getType("any")),
                false
            )).toToken(env),
            "list-add!" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    val tokens = cast<MutableList<Token>>(args[0].value)
                    if (args.size > 2) {
                        himeAssertRuntime(env.isType(args[1], env.getType("int"))) { "${args[1]} is not int." }
                        tokens.add(args[1].value.toString().toInt(), args[2])
                    } else
                        tokens.add(args[1])
                    return args[0].toToken(env)
                },
                listOf(env.getType("list"), env.getType("any")),
                true
            )).toToken(env),
            "list-ref" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val index = args[1].value.toString().toInt()
                val tokens = cast<List<Token>>(args[0].value)
                himeAssertRuntime(index < tokens.size) { "index error." }
                return tokens[index]
            }, listOf(env.getType("list"), env.getType("int")), false)).toToken(env),
            "++" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var flag = false
                // 判断是否是List，还是string
                for (arg in args)
                    if (env.isType(arg, env.getType("list"))) {
                        flag = true
                        break
                    }
                return if (flag) {
                    val list = ArrayList<Token>()
                    for (arg in args) {
                        if (env.isType(arg, env.getType("list")))
                            list.addAll(cast<List<Token>>(arg.value))
                        else
                            list.add(arg)
                    }
                    list.toToken(env)
                } else {
                    val builder = StringBuilder()
                    for (arg in args)
                        builder.append(arg.toString())
                    builder.toString().toToken(env)
                }
            }, listOf(env.getType("any")), true)).toToken(env),
            "range" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val start = if (args.size >= 2) BigInteger(args[0].toString()) else BigInteger.ZERO
                val end =
                    if (args.size >= 2) BigInteger(args[1].toString()) else BigInteger(args[0].toString())
                // 每次增加的step
                val step = if (args.size >= 3) BigInteger(args[2].toString()) else BigInteger.ONE
                val size = end.subtract(start).divide(step)
                val list = ArrayList<Token>()
                var i = BigInteger.ZERO
                // index和size是否相等
                while (i.compareTo(size) != 1) {
                    list.add(start.add(i.multiply(step)).toToken(env))
                    i = i.add(BigInteger.ONE)
                }
                return Token(env.getType("list"), list)
            }, listOf(env.getType("any")), true)).toToken(env),
            // 获取长度，可以是字符串也可以是列表
            "length" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return if (env.isType(args[0], env.getType("list")))
                        cast<List<Token>>(args[0].value).size.toToken(env)
                    else
                        args[0].toString().length.toToken(env)
            }, listOf(env.getType("any")), false)).toToken(env),
            // 反转列表
            "reverse" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val result = ArrayList<Token>()
                val tokens = cast<MutableList<Token>>(args[0].value)
                for (i in tokens.size - 1 downTo 0)
                    result.add(tokens[i])
                tokens.clear()
                for (t in result)
                    tokens.add(t)
                return tokens.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            // 排序
            "sort" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                // 归并排序
                fun merge(a: Array<BigDecimal?>, low: Int, mid: Int, high: Int) {
                    val temp = arrayOfNulls<BigDecimal>(high - low + 1)
                    var i = low
                    var j = mid + 1
                    var k = 0
                    while (i <= mid && j <= high)
                        temp[k++] = if (a[i]!! < a[j]) a[i++] else a[j++]
                    while (i <= mid)
                        temp[k++] = a[i++]
                    while (j <= high)
                        temp[k++] = a[j++]
                    for (k2 in temp.indices)
                        a[k2 + low] = temp[k2]!!
                }

                fun mergeSort(a: Array<BigDecimal?>, low: Int, high: Int) {
                    val mid = (low + high) / 2
                    if (low < high) {
                        mergeSort(a, low, mid)
                        mergeSort(a, mid + 1, high)
                        merge(a, low, mid, high)
                    }
                }

                val tokens = cast<List<Token>>(args[0].value)
                val result = ArrayList<Token>()
                val list = arrayOfNulls<BigDecimal>(tokens.size)
                for (i in tokens.indices)
                    list[i] = BigDecimal(tokens[i].toString())
                mergeSort(list, 0, list.size - 1)
                for (e in list)
                    result.add(e!!.toToken(env))
                return result.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "curry" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                fun rsc(func: Token, n: Int, parameters: ArrayList<Token>): Token {
                    if (n == 0) {
                        // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                        val asts = env.himeAstEmpty.copy()
                        for (arg in parameters)
                            asts.add(ASTNode(arg))
                        return cast<HimeFunction>(func.value).call( asts, symbol.createChild())
                    }
                    return (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                        parameters.add(args[0])
                        return rsc(func, n - 1, parameters)
                    }, 1)).toToken(env)
                }
                return rsc(args[0], args[1].value.toString().toInt(), ArrayList())
            }, listOf(env.getType("function"), env.getType("int")), false)).toToken(env),
            "maybe" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val parameters = ArrayList<Token>()
                for (i in 1 until args.size) {
                    if (args[i] == env.himeNil)
                        return env.himeNil
                    parameters.add(args[i])
                }
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = env.himeAstEmpty.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                return cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())

            }, listOf(env.getType("function")), true)).toToken(env),
            "map" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val result = ArrayList<Token>()
                val tokens = cast<List<Token>>(args[1].value)
                for (i in tokens.indices) {
                    val parameters = ArrayList<Token>()
                    parameters.add(tokens[i])
                    // 例如对于(map f (list a b) (list c d))，则执行(f a c)等
                    for (j in 1 until args.size - 1) {
                        himeAssertRuntime(
                            env.isType(
                                args[j + 1],
                                env.getType("list")
                            )
                        ) { "${args[j + 1]} is not list." }
                        parameters.add(cast<List<Token>>(args[j + 1].value)[i])
                    }
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    for (arg in parameters)
                        asts.add(ASTNode(arg))
                    result.add(cast<HimeFunction>(args[0].value).call( asts, symbol.createChild()))
                }
                return result.toToken(env)
            }, listOf(env.getType("function"), env.getType("list")), true)).toToken(env),
            "foldr" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                var result = args[1]
                val tokens = cast<List<Token>>(args[2].value)
                for (i in tokens.size - 1 downTo 0) {
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    for (arg in arrayListOf(tokens[i], result))
                        asts.add(ASTNode(arg))
                    result = cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                }
                return result
            }, listOf(env.getType("function"), env.getType("any"), env.getType("list")), false)).toToken(env),
            "foldl" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                var result = args[1]
                val tokens = cast<List<Token>>(args[2].value)
                for (i in tokens.size - 1 downTo 0) {
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    for (arg in arrayListOf(result, tokens[i]))
                        asts.add(ASTNode(arg))
                    result = cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                }
                return result
            }, listOf(env.getType("function"), env.getType("any"), env.getType("list")), false)).toToken(env),
            "for-each" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val tokens = cast<List<Token>>(args[1].value)
                for (i in tokens.indices) {
                    val parameters = ArrayList<Token>()
                    parameters.add(tokens[i])
                    // 例如对于(map f (list a b) (list c d))，则执行(f a c)等
                    for (j in 1 until args.size - 1) {
                        himeAssertRuntime(
                            env.isType(
                                args[j + 1],
                                env.getType("list")
                            )
                        ) { "${args[j + 1]} is not list." }
                        parameters.add(cast<List<Token>>(args[j + 1].value)[i])
                    }
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    for (arg in parameters)
                        asts.add(ASTNode(arg))
                    cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                }
                return env.himeNil
            }, listOf(env.getType("function"), env.getType("list")), true)).toToken(env),
            "filter" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val result = ArrayList<Token>()
                val tokens = cast<List<Token>>(args[1].value)
                for (token in tokens) {
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    asts.add(ASTNode(token))
                    val op = cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                    himeAssertRuntime(env.isType(op, env.getType("bool"))) { "$op is not bool." }
                    if (cast<Boolean>(op.value))
                        result.add(token)
                }
                return result.toToken(env)
            }, listOf(env.getType("function"), env.getType("list")), false)).toToken(env),
            "sqrt" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.sqrt(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "sin" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.sin(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "sinh" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.sinh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "asin" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.asin(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "asinh" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.asinh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "cos" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.cos(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "cosh" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.cosh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "acos" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.acos(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "acosh" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.acosh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "tan" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.tan(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "tanh" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.tanh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "atan" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.atan(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "atanh" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.atanh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "atan2" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.atan2(
                    BigDecimal(args[0].toString()),
                    BigDecimal(args[1].toString()),
                    MathContext.DECIMAL64
                ).toToken(env)
            }, listOf(env.getType("num"), env.getType("num")), false)).toToken(env),
            "log" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.log(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "log10" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.log10(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "log2" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.log2(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "exp" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.exp(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken(env)
            }, listOf(env.getType("num")), false)).toToken(env),
            "pow" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimalMath.pow(
                    BigDecimal(args[0].toString()),
                    BigDecimal(args[1].toString()),
                    MathContext.DECIMAL64
                ).toToken(env)
            }, listOf(env.getType("num"), env.getType("num")), false)).toToken(env),
            "mod" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).mod(BigInteger(args[1].toString())).toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), false)).toToken(env),
            "max" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var max = BigDecimal(args[0].toString())
                for (i in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[i], env.getType("num"))) { "${args[i]} is not num." }
                    max = max.max(BigDecimal(args[i].toString()))
                }
                return max.toToken(env)
            }, listOf(env.getType("num")), true)).toToken(env),
            "min" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                var min = BigDecimal(args[0].toString())
                for (i in 1 until args.size) {
                    himeAssertRuntime(env.isType(args[i], env.getType("num"))) { "${args[i]} is not num." }
                    min = min.min(BigDecimal(args[i].toString()))
                }
                return min.toToken(env)
            }, listOf(env.getType("num")), true)).toToken(env),
            "abs" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimal(args[0].toString()).abs().toToken(env)
            }, 1)).toToken(env),
            "average" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                var num = BigDecimal.ZERO
                for (arg in args) {
                    himeAssertRuntime(env.isType(arg, env.getType("num"))) { "$arg is not num." }
                    num = num.add(BigDecimal(arg.value.toString()))
                }
                return num.divide(args.size.toBigDecimal()).toToken(env)
            }, listOf(env.getType("num")), true)).toToken(env),
            "floor" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(
                    BigDecimal(args[0].toString()).setScale(0, RoundingMode.FLOOR).toPlainString()
                ).toToken(env)
            }, 1)).toToken(env),
            "ceil" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(
                    BigDecimal(args[0].toString()).setScale(0, RoundingMode.CEILING).toPlainString()
                ).toToken(env)
            }, 1)).toToken(env),
            "gcd" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    himeAssertRuntime(env.isType(arg, env.getType("int"))) { "$arg is not int." }
                var temp = BigInteger(args[0].toString()).gcd(BigInteger(args[1].toString()))
                for (i in 2 until args.size)
                    temp = temp.gcd(BigInteger(args[i].toString()))
                return temp.toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), true)).toToken(env),
            // (lcm a b) = (/ (* a b) (gcd a b))
            "lcm" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                fun BigInteger.lcm(n: BigInteger): BigInteger = (this.multiply(n).abs()).divide(this.gcd(n))
                for (arg in args)
                    himeAssertRuntime(env.isType(arg, env.getType("num"))) { "$arg is not num." }
                var temp = BigInteger(args[0].toString()).lcm(BigInteger(args[1].toString()))
                for (i in 2 until args.size)
                    temp = temp.lcm(BigInteger(args[i].toString()))
                return temp.toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), true)).toToken(env),
            "->bool" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return if (args[0].toString() == "true") env.himeTrue else env.himeFalse
            }, 1)).toToken(env),
            "->string" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString().toToken(env)
            }, 1)).toToken(env),
            "->int" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).toToken(env)
            }, 1)).toToken(env),
            "->real" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigDecimal(args[0].toString()).toToken(env)
            }, 1)).toToken(env),
            "string-replace" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString().replace(args[1].toString(), args[2].toString()).toToken(env)
            }, 3)).toToken(env),
            "string-substring" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString()
                    .substring(args[1].value.toString().toInt(), args[2].value.toString().toInt())
                    .toToken(env)
            }, listOf(env.getType("string"), env.getType("int"), env.getType("int")), false)).toToken(env),
            "string-split" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString().split(args[1].toString()).toList().toToken(env)
            }, listOf(env.getType("string"), env.getType("string")), false)).toToken(env),
            "string-index" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString().indexOf(args[1].toString()).toToken(env)
            }, listOf(env.getType("string"), env.getType("string")), false)).toToken(env),
            "string-last-index" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.size > 1) { "not enough arguments." }
                return args[0].toString().lastIndexOf(args[1].toString()).toToken(env)
            }, listOf(env.getType("string"), env.getType("string")), false)).toToken(env),
            "string-format" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val newArgs = arrayOfNulls<Any>(args.size - 1)
                for (i in 1 until args.size)
                    newArgs[i - 1] = args[i].value
                return String.format(args[0].toString(), *newArgs).toToken(env)
            }, listOf(env.getType("string")), true)).toToken(env),
            "string->list" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
                val chars = args[0].toString().toCharArray()
                val list = ArrayList<Token>()
                for (c in chars)
                    list.add(c.toString().toToken(env))
                return list.toToken(env)
            }, listOf(env.getType("string")), false)).toToken(env),
            "list->string" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val builder = StringBuilder()
                val list = cast<List<Token>>(args[0].value)
                for (token in list)
                    builder.append(token.toString())
                return builder.toString().toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "string->bytes" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val builder = StringBuilder()
                for (token in args)
                    builder.append(token.toString())
                val list = ArrayList<Token>()
                val bytes = builder.toString().toByteArray()
                for (byte in bytes)
                    list.add(byte.toToken(env))
                return list.toToken(env)
            }, listOf(env.getType("string")), false)).toToken(env),
            "bytes->string" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val list = cast<List<Token>>(args[0].value)
                val bytes = ByteArray(list.size)
                for (index in list.indices) {
                    himeAssertRuntime(env.isType(list[index], env.getType("byte"))) { "${list[index]} is not byte." }
                    bytes[index] = cast<Byte>(list[index].value)
                }
                return String(bytes).toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "string->bits" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val s = args[0].toString()
                val result = ArrayList<Token>()
                for (c in s)
                    result.add(c.code.toToken(env))
                return result.toToken(env)
            }, listOf(env.getType("string")), false)).toToken(env),
            "bits->string" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val result = StringBuilder()
                val tokens = cast<List<Token>>(args[0].value)
                for (t in tokens) {
                    himeAssertRuntime(env.isType(t, env.getType("int"))) { "$t is not int." }
                    result.append(t.value.toString().toInt().toChar())
                }
                return result.toToken(env)
            }, listOf(env.getType("list")), false)).toToken(env),
            "bool?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("bool")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "string?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("string")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "int?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("int")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "real?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("real")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "list?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("list")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "byte?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("byte")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "function?" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    if (!env.isType(arg, env.getType("function")))
                        return env.himeFalse
                return env.himeTrue
            }, listOf(env.getType("any")), true)).toToken(env),
            "exit" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                exitProcess(args[0].value.toString().toInt())
            }, listOf(env.getType("int")), false)).toToken(env),
            "eval" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                val newSymbol = symbol.createChild()
                var result = env.himeNil
                for (node in args)
                    result = call(env, node.toString(), newSymbol)
                return result
            }, listOf(env.getType("string")), true)).toToken(env),
            "bit-and" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).and(BigInteger(args[1].toString())).toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), false)).toToken(env),
            "bit-or" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).or(BigInteger(args[1].toString())).toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), false)).toToken(env),
            "bit-xor" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).xor(BigInteger(args[1].toString())).toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), false)).toToken(env),
            "bit-left" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).shiftLeft(BigInteger(args[1].toString()).toInt()).toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), false)).toToken(env),
            "bit-right" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return BigInteger(args[0].toString()).shiftRight(BigInteger(args[1].toString()).toInt()).toToken(env)
            }, listOf(env.getType("int"), env.getType("int")), false)).toToken(env),
            "error" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                throw HimeRuntimeException(args[0].toString())
            }, listOf(env.getType("any")), false)).toToken(env)
        )
    )
}

fun initFile(env: Env) {
    env.symbols.table.putAll(
        mutableMapOf(
            "file-exists" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return File(args[0].toString()).exists().toToken(env)
            }, 1)).toToken(env),
            "file-list" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val list = ArrayList<Token>()
                val files = File(args[0].toString()).listFiles()
                for (file in files!!)
                    list.add(file.path.toToken(env))
                return list.toToken(env)
            }, 1)).toToken(env),
            "file-mkdirs" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    val file = File(args[0].toString())
                    if (!file.parentFile.exists())
                        !file.parentFile.mkdirs()
                    if (!file.exists())
                        file.createNewFile()
                    return env.himeNil
                },
                1
            )).toToken(env),
            "file-new" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    val file = File(args[0].toString())
                    if (!file.exists())
                        file.createNewFile()
                    return env.himeNil
                },
                1
            )).toToken(env),
            "file-read-string" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return Files.readString(Paths.get(args[0].toString())).toToken(env)
            }, 1)).toToken(env),
            "file-remove" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    File(args[0].toString()).delete()
                    return env.himeNil
                },
                1
            )).toToken(env),
            "file-write-string" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    val file = File(args[0].toString())
                    if (!file.parentFile.exists())
                        !file.parentFile.mkdirs()
                    if (!file.exists())
                        file.createNewFile()
                    Files.writeString(file.toPath(), args[1].toString())
                    return env.himeNil
                },
                2
            )).toToken(env),
            "file-read-bytes" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val list = ArrayList<Token>()
                val bytes = Files.readAllBytes(Paths.get(args[0].toString()))
                for (byte in bytes)
                    list.add(byte.toToken(env))
                return list.toToken(env)
            }, 1)).toToken(env),
            "file-write-bytes" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    val file = File(args[0].toString())
                    if (!file.parentFile.exists())
                        !file.parentFile.mkdirs()
                    if (!file.exists())
                        file.createNewFile()
                    val list = cast<List<Token>>(args[1].value)
                    val bytes = ByteArray(list.size)
                    for (index in list.indices) {
                        himeAssertRuntime(env.isType(list[index], env.getType("byte"))) { "${list[index]} is not byte." }
                        bytes[index] = cast<Byte>(list[index].value)
                    }
                    Files.write(file.toPath(), bytes)
                    return env.himeNil
                },
                listOf(env.getType("any"), env.getType("list")),
                false
            )).toToken(env)
        )
    )
}

fun initTime(env: Env) {
    env.symbols.table.putAll(
        mutableMapOf(
            "time" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
                return Date().time.toToken(env)
            }, 0)).toToken(env),
            "time-format" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(env.isType(args[1], env.getType("int"))) { "${args[1]} is not int." }
                return SimpleDateFormat(args[0].toString()).format(args[1].toString().toLong())
                    .toToken(env)
            }, 2)).toToken(env),
            "time-parse" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return SimpleDateFormat(args[0].toString()).parse(args[1].value.toString()).time.toToken(env)
            }, 2)).toToken(env)
        )
    )
}

fun initTable(env: Env) {
    env.symbols.table.putAll(
        mutableMapOf(
            "table" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
                return mapOf<Token, Token>().toToken(env)
            }, 0)).toToken(env),
            "table-put" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val table = HashMap(cast<Map<Token, Token>>(args[0].value))
                table[args[1]] = args[2]
                return table.toToken(env)
            }, listOf(env.getType("table"), env.getType("any"), env.getType("any")), false)).toToken(env),
            "table-get" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                himeAssertRuntime(args.size > 1) { "not enough arguments." }
                himeAssertRuntime(env.isType(args[0], env.getType("table"))) { "${args[0]} is not table." }
                val table = cast<Map<Token, Token>>(args[0].value)
                return table[args[1]] ?: env.himeNil
            }, listOf(env.getType("table"), env.getType("any")), false)).toToken(env),
            "table-remove" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val table = HashMap(cast<Map<Token, Token>>(args[0].value))
                table.remove(args[1])
                return table.toToken(env)
            }, listOf(env.getType("table"), env.getType("any")), false)).toToken(env),
            "table-keys" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    return cast<Map<Token, Token>>(args[0].value).keys.toList().toToken(env)
                },
                listOf(env.getType("table")),
                false
            )).toToken(env),
            "table-put!" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<MutableMap<Token, Token>>(args[0].value)[args[1]] = args[2]
                    return args[0].toToken(env)
                },
                listOf(env.getType("table"), env.getType("any"), env.getType("any")),
                false
            )).toToken(env),
            "table-remove!" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<MutableMap<Token, Token>>(args[0].value).remove(args[1])
                    return args[0].toToken(env)
                },
                listOf(env.getType("table"), env.getType("any")),
                false
            )).toToken(env)
        )
    )
}

fun initThread(env: Env) {
    env.symbols.table.putAll(
        mutableMapOf(
            "make-lock" to (HimeFunction(env, BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
                return ReentrantLock().toToken(env)
            }, 0)).toToken(env),
            "lock" to (HimeFunction(env, 
                BUILT_IN,
                @Synchronized
                fun(args: List<Token>, _: SymbolTable): Token {
                    for (arg in args)
                        cast<ReentrantLock>(arg.value).lock()
                    return env.himeNil
                }, listOf(env.getType("lock")), true
            )).toToken(env),
            "unlock" to (HimeFunction(env, 
                BUILT_IN,
                @Synchronized
                fun(args: List<Token>, _: SymbolTable): Token {
                    for (arg in args)
                        cast<ReentrantLock>(arg.value).unlock()
                    return env.himeNil
                }, listOf(env.getType("lock")), true
            )).toToken(env),
            "get-lock" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<ReentrantLock>(args[0].value).isLocked.toToken(env)
            }, listOf(env.getType("lock")), false)).toToken(env),
            "sleep" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                Thread.sleep(args[0].toString().toLong())
                return env.himeNil
            }, listOf(env.getType("int")), false)).toToken(env),
            "thread" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = env.himeAstEmpty.copy()
                    asts.add(ASTNode(Thread.currentThread().toToken(env)))
                    return if (args.size > 1)
                        Thread({
                            cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                        }, args[1].toString()).toToken(env)
                    else
                        Thread {
                            cast<HimeFunction>(args[0].value).call( asts, symbol.createChild())
                        }.toToken(env)
                },
                listOf(env.getType("function")),
                true
            )).toToken(env), //这种类重载函数的参数数量处理还比较棘手
            "thread-start" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<Thread>(args[0].value).start()
                    return env.himeNil
                }, listOf(env.getType("thread")), false
            )).toToken(env),
            "thread-current" to (HimeFunction(env, 
                BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
                    return Thread.currentThread().toToken(env)
                }, 0
            )).toToken(env),
            "thread-name" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<Thread>(args[0].value).name.toToken(env)
            }, listOf(env.getType("thread")), false)).toToken(env),
            "thread-set-daemon" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<Thread>(args[0].value).isDaemon = cast<Boolean>(args[1].value)
                    return env.himeNil
                }, listOf(env.getType("thread"), env.getType("bool")), false
            )).toToken(env),
            "thread-daemon" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<Thread>(args[0].value).isDaemon.toToken(env)
            }, listOf(env.getType("thread")), false)).toToken(env),
            "thread-interrupt" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<Thread>(args[0].value).interrupt()
                    return env.himeNil
                }, listOf(env.getType("thread")), false
            )).toToken(env),
            "thread-join" to (HimeFunction(env, 
                BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    cast<Thread>(args[0].value).join()
                    return env.himeNil
                }, listOf(env.getType("thread")), false
            )).toToken(env),
            "thread-alive" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<Thread>(args[0].value).isAlive.toToken(env)
            }, listOf(env.getType("thread")), false)).toToken(env),
            "thread-interrupted" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<Thread>(args[0].value).isInterrupted.toToken(env)
            }, listOf(env.getType("thread")), false)).toToken(env)
        )
    )
}

fun initRegex(env: Env) {
    env.symbols.table.putAll(
        mutableMapOf(
            "match" to (HimeFunction(env, BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return args[0].toString().matches(Regex(args[1].toString())).toToken(env)
            }, 2)).toToken(env)
        )
    )
}

val module = mutableMapOf(
    "util.file" to ::initFile,
    "util.time" to ::initTime,
    "util.table" to ::initTable,
    "util.thread" to ::initThread,
    "util.regex" to ::initRegex
)
