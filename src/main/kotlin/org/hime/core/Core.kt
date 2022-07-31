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

val core = SymbolTable(
    mutableMapOf(
        "true" to TRUE,
        "false" to FALSE,
        "nil" to NIL,
        "empty-stream" to EMPTY_STREAM,
        "def-symbol" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
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
                (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
                    if (ast.type == AstType.FUNCTION) {
                        var result = NIL
                        val newSymbol = symbol.createChild()
                        for (node in ast.children)
                            result = eval(ASTNode(eval(node, newSymbol)), newSymbol)
                        return result
                    }
                    val newAsts = ArrayList<ASTNode>()
                    for (node in asts) {
                        val newAst = node.copy()

                        // 递归替换宏
                        fun rsc(ast: ASTNode, id: String, value: ASTNode) {
                            if (isType(ast.tok, getType("id")) && ast.tok.toString() == id) {
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
                    var result = NIL
                    for (astNode in newAsts)
                        result = eval(astNode.copy(), newSymbol)
                    return result
                })).toToken()
            )
            return NIL
        })).toToken(),
        "cons-stream" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            // 对(cons-stream t1 t2)中的t1进行求值
            val t1 = eval(ast[0], symbol.createChild())
            val asts = ArrayList<ASTNode>()
            // 对t1后面的内容都作为begin添加到asts中
            for (i in 1 until ast.size())
                asts.add(ast[i].copy())
            // 建立过程，类似(delay t2*)
            return arrayListOf(
                t1,
                structureHimeFunction(arrayListOf(), arrayListOf(), asts, symbol.createChild())
            ).toToken()
        })).toToken(),
        "stream-car" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            // 因为(cons-stream t1 t2)的t1已经被求值，所以就直接返回
            return cast<List<Token>>(args[0].value)[0]
        }, listOf(getType("list")), false)).toToken(),
        "stream-cdr" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = cast<List<Token>>(args[0].value)
            val list = ArrayList<Token>()
            // 将stream-cdr传入的列表，除去第1个外的所有元素都应用force，即便(cons-stream t1 t2)只返回包含2个元素的列表
            for (i in 1 until tokens.size) {
                himeAssertRuntime(isType(tokens[i], getType("function"))) { "${tokens[i]} is not function." }
                list.add(cast<HimeFunction>(tokens[i].value).call(arrayListOf()))
            }
            // 如果列表中只存在一个元素，那么就返回这个元素
            if (list.size == 1)
                return list[0].toToken()
            // 否则返回整个列表
            return list.toToken()
        }, listOf(getType("list")), false)).toToken(),
        "stream-map" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            if (args[1] == EMPTY_STREAM)
                return EMPTY_STREAM
            himeAssertRuntime(isType(args[1], getType("list"))) { "${args[1]} is not list." }
            // 每个list只包含2个元素，一个已经求值，一个待求值，这里包含(stream-map function list*)的所有list
            var lists = ArrayList<List<Token>>()
            // 将所有list添加到lists中
            for (i in 1 until args.size)
                lists.add(cast<List<Token>>(args[i].value))
            val result = ArrayList<Token>()
            // 直到遇见EMPTY_STREAM才结束
            top@ while (true) {
                // 例如对于(stream-map f (stream-cons a b) (stream-cons c d))，则执行(f a c)等
                val parameters = ArrayList<Token>()
                // 将所有首项添加到parameters
                for (list in lists)
                    parameters.add(list[0])
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                // 将parameters按匹配的类型添加到函数中并执行
                result.add(cast<HimeFunction>(args[0].value).call(asts, symbol))
                val temp = ArrayList<List<Token>>()
                // 重新计算lists，并应用delay
                for (list in lists) {
                    val t = cast<HimeFunction>(list[1].value).call(arrayListOf())
                    if (t == EMPTY_STREAM)
                        break@top
                    temp.add(cast<List<Token>>(t.value))
                }
                lists = temp
            }
            return result.toToken()
        }, listOf(getType("list"), getType("any")), true)).toToken(),
        "stream-for-each" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            if (args[1] == EMPTY_STREAM)
                return EMPTY_STREAM
            himeAssertRuntime(isType(args[1], getType("list"))) { "${args[1]} is not list." }
            // 每个list只包含2个元素，一个已经求值，一个待求值，这里包含(stream-map function list*)的所有list
            var lists = ArrayList<List<Token>>()
            // 将所有list添加到lists中
            for (i in 1 until args.size)
                lists.add(cast<List<Token>>(args[i].value))
            // 直到遇见EMPTY_STREAM才结束
            top@ while (true) {
                // 例如对于(stream-map f (stream-cons a b) (stream-cons c d))，则执行(f a c)等
                val parameters = ArrayList<Token>()
                // 将所有首项添加到parameters
                for (list in lists)
                    parameters.add(list[0])
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                // 将parameters按匹配的类型添加到函数中并执行
                cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
                val temp = ArrayList<List<Token>>()
                // 重新计算lists，并应用delay
                for (list in lists) {
                    himeAssertRuntime(isType(list[1], getType("function"))) { "${list[1]} is not function." }
                    val t = cast<HimeFunction>(list[1].value).call(arrayListOf())
                    if (t == EMPTY_STREAM)
                        break@top
                    temp.add(cast<List<Token>>(t.value))
                }
                lists = temp
            }
            return NIL
        }, listOf(getType("list"), getType("any")), true)).toToken(),
        "stream-filter" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            if (args[1] == EMPTY_STREAM)
                return arrayListOf<Token>().toToken()
            himeAssertRuntime(isType(args[1], getType("list"))) { "${args[1]} is not list." }
            val result = ArrayList<Token>()
            var tokens = cast<List<Token>>(args[1].value)
            while (tokens[0].value != EMPTY_STREAM) {
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                asts.add(ASTNode(tokens[0]))
                val op = cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
                himeAssertRuntime(isType(op, getType("bool"))) { "$op is not bool." }
                if (cast<Boolean>(op.value))
                    result.add(tokens[0])
                val temp = cast<HimeFunction>(tokens[1].value).call(arrayListOf())
                if (temp == EMPTY_STREAM)
                    break
                tokens = cast<List<Token>>(temp.value)
            }
            return result.toToken()
        }, listOf(getType("function")), true)).toToken(),
        "stream-ref" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.size > 1) { "not enough arguments." }
            himeAssertRuntime(isType(args[0], getType("list"))) { "${args[0]} is not list." }
            himeAssertRuntime(isType(args[1], getType("int"))) { "${args[1]} is not int." }
            var temp = cast<List<Token>>(args[0].value)
            var index = args[1].value.toString().toInt()
            while ((index--) != 0) {
                himeAssertRuntime(isType(temp[1], getType("function"))) { "${temp[1]} is not function." }
                temp = cast<List<Token>>(cast<HimeFunction>(temp[1].value).call(arrayListOf()).value)
            }
            return temp[0]
        }, listOf(getType("list"), getType("any")), false)).toToken(),
        // (delay e) => (lambda () e)
        "delay" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
            val asts = ArrayList<ASTNode>()
            for (i in 0 until ast.size())
                asts.add(ast[i].copy())
            return structureHimeFunction(arrayListOf(), arrayListOf(), asts, symbol.createChild())
        })).toToken(),
        // (force d) => (d)
        "force" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var result = NIL
            for (token in args) {
                himeAssertRuntime(isType(token, getType("function"))) { "$token is not function." }
                result = cast<HimeFunction>(token.value).call(arrayListOf())
            }
            return result
        }, listOf(getType("any")), true)).toToken(),
        "type" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].type.toToken()
        }, listOf(getType("any")), false)).toToken(),
        // 局部变量绑定
        "let" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            var result = NIL
            for (node in ast[0].children) {
                if (node.tok.toString() == "apply") {
                    val parameters = ArrayList<String>()
                    val paramTypes = ArrayList<HimeType>()
                    for (i in 0 until ast[0].size()) {
                        himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
                        parameters.add(ast[0][i].tok.toString())
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                    }
                    val asts = ArrayList<ASTNode>()
                    for (i in 1 until node.size())
                        asts.add(node[i].copy())
                    // 这里采用原环境的继承，因为let不可互相访问
                    newSymbol.put(
                        node[0].tok.toString(),
                        structureHimeFunction(parameters, paramTypes, asts, symbol.createChild())
                    )
                } else {
                    var value = NIL
                    for (e in node.children)
                        value = eval(e.copy(), symbol.createChild())
                    val type = cast<HimeTypeId>(node.tok.type).type
                    himeAssertRuntime(isType(value, type)) { "$value is not ${type.name}." }
                    newSymbol.put(node.tok.toString(), value)
                }
            }
            for (i in 1 until ast.size())
                result = eval(ast[i].copy(), newSymbol.createChild())
            return result
        })).toToken(),
        "let*" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            var result = NIL
            for (node in ast[0].children) {
                if (node.tok.toString() == "apply") {
                    val parameters = ArrayList<String>()
                    val paramTypes = ArrayList<HimeType>()
                    for (i in 0 until ast[0].size()) {
                        himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
                        parameters.add(ast[0][i].tok.toString())
                        paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                    }
                    val asts = ArrayList<ASTNode>()
                    for (i in 1 until node.size())
                        asts.add(node[i].copy())
                    // 这里采用新环境的继承，因为let*可互相访问
                    newSymbol.put(
                        node[0].tok.toString(),
                        structureHimeFunction(parameters, paramTypes, asts, newSymbol.createChild())
                    )
                } else {
                    var value = NIL
                    for (e in node.children)
                        value = eval(e.copy(), newSymbol.createChild())
                    val type = cast<HimeTypeId>(node.tok.type).type
                    himeAssertRuntime(isType(value, type)) { "$value is not ${type.name}." }
                    newSymbol.put(node.tok.toString(), value)
                }
            }
            for (i in 1 until ast.size())
                result = eval(ast[i].copy(), newSymbol.createChild())
            return result
        })).toToken(),
        // 建立新绑定
        "def" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            himeAssertRuntime(!symbol.table.containsKey(ast[0].tok.toString())) { "repeat binding ${ast[0].tok}." }
            // 如果是(def key value)
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                var result = NIL
                for (i in 1 until ast.size())
                    result = eval(ast[i], symbol.createChild())
                val type = cast<HimeTypeId>(ast[0].tok.type).type
                himeAssertRuntime(isType(result, type)) { "$result is not ${type.name}." }
                symbol.put(ast[0].tok.toString(), result)
            }
            // 如果是(def (function-name p*) e)
            else {
                val parameters = ArrayList<String>()
                val paramTypes = ArrayList<HimeType>()
                for (i in 0 until ast[0].size()) {
                    himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
                    parameters.add(ast[0][i].tok.toString())
                    paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                }
                val asts = ArrayList<ASTNode>()
                // 将ast都复制一遍并存到asts中
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.put(
                    ast[0].tok.toString(),
                    structureHimeFunction(parameters, paramTypes, asts, symbol.createChild())
                )
            }
            return NIL
        })).toToken(),
        // 建立新绑定(变长)
        "def-variadic" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            himeAssertRuntime(!symbol.table.containsKey(ast[0].tok.toString())) { "repeat binding ${ast[0].tok}." }
            himeAssertRuntime(ast[0].isNotEmpty() || ast[0].type != AstType.FUNCTION) { "format error." }
            val parameters = ArrayList<String>()
            val paramTypes = ArrayList<HimeType>()
            for (i in 0 until ast[0].size()) {
                himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
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
                variableHimeFunction(parameters, paramTypes, asts, symbol.createChild())
            )
            return NIL
        })).toToken(),
        // 解除绑定
        "undef" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.isNotEmpty()) { "not enough arguments." }
            himeAssertRuntime(symbol.contains(ast[0].tok.toString())) { "environment does not contain ${ast[0].tok} binding." }
            // 从环境中删除绑定
            symbol.remove(ast[0].tok.toString())
            return NIL
        })).toToken(),
        // 更改绑定
        "set!" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            himeAssertRuntime(symbol.contains(ast[0].tok.toString())) { "environment does not contain ${ast[0].tok} binding." }
            // 如果是(set key value)
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                var result = NIL
                for (i in 1 until ast.size())
                    result = eval(ast[i], symbol.createChild())
                symbol.set(ast[0].tok.toString(), result)
            } else {
                // 如果是(set (function-name p*) e)
                val parameters = ArrayList<String>()
                val paramTypes = ArrayList<HimeType>()
                for (i in 0 until ast[0].size()) {
                    himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
                    parameters.add(ast[0][i].tok.toString())
                    paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                }
                val asts = ArrayList<ASTNode>()
                // 将ast都复制一遍并存到asts中
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.set(ast[0].tok.toString(), structureHimeFunction(parameters, paramTypes, asts, symbol))
            }
            return NIL
        })).toToken(),
        "set-variadic!" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            himeAssertRuntime(symbol.contains(ast[0].tok.toString())) { "environment does not contain ${ast[0].tok} binding." }
            himeAssertRuntime(ast[0].isNotEmpty() || ast[0].type != AstType.FUNCTION) { "format error." }
            val parameters = ArrayList<String>()
            val paramTypes = ArrayList<HimeType>()
            for (i in 0 until ast[0].size()) {
                himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
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
                variableHimeFunction(parameters, paramTypes, asts, symbol.createChild())
            )
            return NIL
        })).toToken(),
        "lambda" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            val parameters = ArrayList<String>()
            val paramTypes = ArrayList<HimeType>()
            // 判断非(lambda () e)
            if (ast[0].tok != EMPTY) {
                himeAssertRuntime(isType(ast[0].tok, getType("id"))) { "${ast[0].tok} is not id." }
                parameters.add(ast[0].tok.toString())
                paramTypes.add(cast<HimeTypeId>(ast[0].tok.type).type)
                for (i in 0 until ast[0].size()) {
                    himeAssertRuntime(isType(ast[0][i].tok, getType("id"))) { "${ast[0][i].tok} is not id." }
                    parameters.add(ast[0][i].tok.toString())
                    paramTypes.add(cast<HimeTypeId>(ast[0][i].tok.type).type)
                }
            }
            val asts = ArrayList<ASTNode>()
            // 将ast都复制一遍并存到asts中
            for (i in 1 until ast.size())
                asts.add(ast[i].copy())
            return structureHimeFunction(parameters, paramTypes, asts, symbol.createChild())
        })).toToken(),
        "if" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            // 执行condition
            val condition = eval(ast[0], newSymbol)
            himeAssertRuntime(isType(condition, getType("bool"))) { "$condition is not bool." }
            // 分支判断
            if (cast<Boolean>(condition.value))
                return eval(ast[1].copy(), newSymbol)
            else if (ast.size() > 2)
                return eval(ast[2].copy(), newSymbol)
            return NIL
        })).toToken(),
        "cond" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            for (node in ast.children) {
                // 如果碰到else，就直接执行返回
                if (isType(node.tok, getType("id")) && node.tok.toString() == "else")
                    return eval(node[0].copy(), newSymbol)
                else {
                    val result = eval(node[0].copy(), newSymbol)
                    himeAssertRuntime(isType(result, getType("bool"))) { "$result is not bool." }
                    if (cast<Boolean>(result.value)) {
                        var r = NIL
                        for (index in 1 until node.size())
                            r = eval(node[index].copy(), newSymbol)
                        return r
                    }
                }
            }
            return NIL
        })).toToken(),
        "switch" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            himeAssertRuntime(ast.size() > 1) { "not enough arguments." }
            val newSymbol = symbol.createChild()
            val op = eval(ast[0].copy(), newSymbol)
            for (index in 1 until ast.size()) {
                val node = ast[index]
                if (isType(node.tok, getType("id")) && node.tok.toString() == "else")
                    return eval(node.copy(), newSymbol)
                else
                    if (node.tok == op) {
                        var r = NIL
                        for (i in 0 until node.size())
                            r = eval(node[i].copy(), newSymbol)
                        return r
                    }
            }
            return NIL
        })).toToken(),
        // 执行多个组合式
        "begin" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            var result = NIL
            for (i in 0 until ast.size())
                result = eval(ast[i].copy(), newSymbol)
            return result
        })).toToken(),
        "while" to (HimeFunction(STATIC, fun(ast: ASTNode, symbol: SymbolTable): Token {
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            var result = NIL
            // 执行condition
            var condition = eval(ast[0].copy(), newSymbol)
            himeAssertRuntime(isType(condition, getType("bool"))) { "$condition is not bool." }
            while (cast<Boolean>(condition.value)) {
                for (i in 1 until ast.size())
                    result = eval(ast[i].copy(), newSymbol)
                // 重新执行condition
                condition = eval(ast[0].copy(), newSymbol)
                himeAssertRuntime(isType(condition, getType("bool"))) { "$condition is not bool." }
            }
            return result
        })).toToken(),
        "apply" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val parameters = ArrayList<Token>()
            for (i in 1 until args.size)
                parameters.add(args[i])
            // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
            val asts = ASTNode.AST_EMPTY.copy()
            for (arg in parameters)
                asts.add(ASTNode(arg))
            return cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
        }, listOf(getType("function")), true)).toToken(),
        "apply-list" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val parameters = cast<List<Token>>(args[1].value)
            // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
            val asts = ASTNode.AST_EMPTY.copy()
            for (arg in parameters)
                asts.add(ASTNode(arg))
            return cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
        }, listOf(getType("function")), true)).toToken(),
        "require" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val path = args[0].toString()
            // 导入内置的模块
            if (module.containsKey(path)) {
                for ((key, value) in module[path]!!.table)
                    symbol.put(key, value)
                return NIL
            }
            // 导入工作目录的模块
            val file = File(System.getProperty("user.dir") + "/" + path.replace(".", "/") + ".hime")
            if (file.exists())
                for (node in parser(lexer(preprocessor(Files.readString(file.toPath())))))
                    eval(node, symbol)
            return NIL
        }, 1)).toToken(),
        "read-bit" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            return (symbol.io?.`in` ?: System.`in`).read().toToken()
        }, 0)).toToken(),
        "read-line" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            return Scanner(symbol.io?.`in` ?: System.`in`).nextLine().toToken()
        }, 0)).toToken(),
        "read" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            return Scanner(symbol.io?.`in` ?: System.`in`).next().toToken()
        }, 0)).toToken(),
        "read-num" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            return Scanner(symbol.io?.`in` ?: System.`in`).nextBigInteger().toToken()
        }, 0)).toToken(),
        "read-real" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            return Scanner(symbol.io?.`in` ?: System.`in`).nextBigDecimal().toToken()
        }, 0)).toToken(),
        "read-bool" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            return Scanner(symbol.io?.`in` ?: System.`in`).nextBoolean().toToken()
        }, 0)).toToken(),
        "println" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            (symbol.io?.out ?: System.out).println(builder.toString())
            return NIL
        })).toToken(),
        "print" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            (symbol.io?.out ?: System.out).print(builder.toString())
            return NIL
        })).toToken(),
        "newline" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            (symbol.io?.out ?: System.out).println()
            return NIL
        }, 0)).toToken(),
        "println-error" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            (symbol.io?.err ?: System.err).println(builder.toString())
            return NIL
        })).toToken(),
        "print-error" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            (symbol.io?.err ?: System.err).print(builder.toString())
            return NIL
        })).toToken(),
        "newline-error" to (HimeFunction(BUILT_IN, fun(_: List<Token>, symbol: SymbolTable): Token {
            (symbol.io?.err ?: System.err).println()
            return NIL
        }, 0)).toToken(),
        "+" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var num = args[0]
            for (i in 1 until args.size) {
                himeAssertRuntime(isType(args[i], getType("num"))) { "${args[i]} is not num." }
                num = cast<HimeTypeNum>(num.type).add(num, args[i])
            }
            return num
        }, listOf(getType("num")), true)).toToken(),
        "-" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var num = args[0]
            if (args.size == 1)
                return cast<HimeTypeNum>(num.type).subtract(
                    num,
                    cast<HimeTypeNum>(num.type).multiply(num, BigInteger.TWO.toToken())
                )
            for (i in 1 until args.size) {
                himeAssertRuntime(isType(args[i], getType("num"))) { "${args[i]} is not num." }
                num = cast<HimeTypeNum>(num.type).subtract(num, args[i])
            }
            return num.toToken()
        }, listOf(getType("num")), true)).toToken(),
        "*" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var num = args[0]
            for (i in 1 until args.size) {
                himeAssertRuntime(isType(args[i], getType("num"))) { "${args[i]} is not num." }
                num = cast<HimeTypeNum>(num.type).multiply(num, args[i])
            }
            return num
        }, listOf(getType("num")), true)).toToken(),
        "/" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var num = args[0]
            for (i in 1 until args.size) {
                himeAssertRuntime(isType(args[i], getType("num"))) { "${args[i]} is not num." }
                num = cast<HimeTypeNum>(num.type).divide(num, args[i])
            }
            return num
        }, listOf(getType("num")), true)).toToken(),
        "and" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            for (arg in args) {
                himeAssertRuntime(isType(arg, getType("bool"))) { "$arg is not bool." }
                if (!cast<Boolean>(arg.value))
                    return FALSE
            }
            return TRUE
        }, listOf(getType("bool")), true)).toToken(),
        "or" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args) {
                himeAssertRuntime(isType(arg, getType("bool"))) { "$arg is not bool." }
                if (cast<Boolean>(arg.value))
                    return TRUE
            }
            return FALSE
        }, listOf(getType("bool")), true)).toToken(),
        "not" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            himeAssertRuntime(isType(args[0], getType("bool"))) { "${args[0]} is not bool." }
            return if (cast<Boolean>(args[0].value)) FALSE else TRUE
        }, listOf(getType("bool")), false)).toToken(),
        "=" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            for (i in args.indices) {
                himeAssertRuntime(isType(args[i], getType("eq"))) { "${args[i]} is not eq." }
                for (j in args.indices) {
                    himeAssertRuntime(isType(args[i], getType("eq"))) { "${args[j]} is not eq." }
                    if (i != j && !cast<Boolean>(
                            cast<Boolean>(
                                cast<HimeTypeEq>(args[i].type).eq(
                                    args[i],
                                    args[j]
                                ).value
                            )
                        )
                    )
                        return FALSE
                }
            }
            return TRUE
        }, listOf(getType("eq")), true)).toToken(),
        "/=" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            for (i in args.indices) {
                himeAssertRuntime(isType(args[i], getType("ord"))) { "${args[i]} is not eq." }
                for (j in args.indices) {
                    himeAssertRuntime(isType(args[i], getType("ord"))) { "${args[j]} is not eq." }
                    if (i != j && cast<Boolean>(
                            cast<Boolean>(
                                cast<HimeTypeEq>(args[i].type).eq(
                                    args[i],
                                    args[j]
                                ).value
                            )
                        )
                    )
                        return FALSE
                }
            }
            return TRUE
        }, listOf(getType("eq")), true)).toToken(),
        ">" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            var token = args[0]
            for (index in 1 until args.size) {
                himeAssertRuntime(isType(args[index], getType("ord"))) { "${args[1]} is not ord." }
                if (cast<Boolean>(cast<HimeTypeOrd>(token.type).lessOrEq(token, args[index]).value))
                    return FALSE
                token = args[index]
            }
            return TRUE
        }, listOf(getType("ord")), true)).toToken(),
        "<" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            var token = args[0]
            for (index in 1 until args.size) {
                himeAssertRuntime(isType(args[index], getType("ord"))) { "${args[1]} is not ord." }
                if (cast<Boolean>(cast<HimeTypeOrd>(token.type).greaterOrEq(token, args[index]).value))
                    return FALSE
                token = args[index]
            }
            return TRUE
        }, listOf(getType("ord")), true)).toToken(),
        ">=" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            var token = args[0]
            for (index in 1 until args.size) {
                himeAssertRuntime(isType(args[index], getType("ord"))) { "${args[1]} is not ord." }
                if (cast<Boolean>(cast<HimeTypeOrd>(token.type).less(token, args[index]).value))
                    return FALSE
                token = args[index]
            }
            return TRUE
        }, listOf(getType("ord")), true)).toToken(),
        "<=" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            var token = args[0]
            for (index in 1 until args.size) {
                himeAssertRuntime(isType(args[index], getType("ord"))) { "${args[1]} is not ord." }
                if (cast<Boolean>(cast<HimeTypeOrd>(token.type).greater(token, args[index]).value))
                    return FALSE
                token = args[index]
            }
            return TRUE
        }, listOf(getType("ord")), true)).toToken(),
        "random" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(isType(args[0], getType("num"))) { "${args[0]} is not num." }
            if (args.size > 1)
                himeAssertRuntime(isType(args[1], getType("num"))) { "${args[1]} is not num." }
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
            val bd1 = BigDecimal(BigInteger(generated)).setScale(scale, RoundingMode.FLOOR).subtract(inputRangeStart)
            val bd2 = inputRangeEnd.subtract(inputRangeStart)
            val bd3 = bd1.divide(bd2, RoundingMode.FLOOR)
            val bd4 = outputRangeEnd.subtract(outputRangeStart)
            val bd5 = bd3.multiply(bd4)
            val bd6 = bd5.add(outputRangeStart)
            var returnInteger = bd6.setScale(0, RoundingMode.FLOOR).toBigInteger()
            returnInteger =
                if (returnInteger > end) end else returnInteger
            return returnInteger.toToken()
        }, listOf(getType("int")), true)).toToken(),
        "cons" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return ArrayList(args).toToken()
        }, 2)).toToken(),
        "car" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<List<Token>>(args[0].value)[0]
        }, listOf(getType("list")), false)).toToken(),
        "cdr" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = cast<List<Token>>(args[0].value)
            val list = ArrayList<Token>()
            // 例如(cdr (list a b c d))将返回(list b c d)
            for (i in 1 until tokens.size)
                list.add(tokens[i])
            if (list.size == 1)
                return list[0].toToken()
            return list.toToken()
        }, listOf(getType("list")), false)).toToken(),
        "list" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args.toToken()
        })).toToken(),
        "head" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val list = cast<List<Token>>(args[0].value)
            if (list.isEmpty())
                return NIL
            return list[0]
        }, listOf(getType("list")), false)).toToken(),
        "last" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = cast<List<Token>>(args[0].value)
            return tokens[tokens.size - 1]
        }, listOf(getType("list")), false)).toToken(),
        "tail" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = cast<List<Token>>(args[0].value)
            val list = ArrayList<Token>()
            for (i in 1 until tokens.size)
                list.add(tokens[i])
            if (list.size == 1)
                return arrayListOf(list[0]).toToken()
            return list.toToken()
        }, listOf(getType("list")), false)).toToken(),
        "init" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = cast<List<Token>>(args[0].value)
            val list = ArrayList<Token>()
            for (i in 0 until tokens.size - 1)
                list.add(tokens[i])
            if (list.size == 1)
                return arrayListOf(list[0]).toToken()
            return list.toToken()
        }, listOf(getType("list")), false)).toToken(),
        "list-contains" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<List<Token>>(args[0].value).contains(args[1]).toToken()
        }, listOf(getType("list"), getType("any")), false)).toToken(),
        "list-remove" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = ArrayList(cast<List<Token>>(args[0].value))
            tokens.removeAt(args[1].value.toString().toInt())
            return tokens.toToken()
        }, listOf(getType("list"), getType("int")), false)).toToken(),
        "list-set" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val index = args[1].value.toString().toInt()
            val tokens = ArrayList(cast<List<Token>>(args[0].value))
            himeAssertRuntime(index < tokens.size) { "index error." }
            tokens[index] = args[2]
            return tokens.toToken()
        }, listOf(getType("list"), getType("int"), getType("any")), false)).toToken(),
        "list-add" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val tokens = ArrayList(cast<List<Token>>(args[0].value))
            if (args.size > 2) {
                himeAssertRuntime(isType(args[1], getType("int"))) { "${args[1]} is not int." }
                tokens.add(args[1].value.toString().toInt(), args[2])
            } else
                tokens.add(args[1])
            return tokens.toToken()
        }, listOf(getType("list"), getType("any")), true)).toToken(),
        "list-remove!" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<MutableList<Token>>(args[0].value).removeAt(args[1].value.toString().toInt())
                return args[0].toToken()
            },
            listOf(getType("list"), getType("int")),
            false
        )).toToken(),
        "list-set!" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<MutableList<Token>>(args[0].value)[args[1].value.toString().toInt()] = args[2]
                return args[0].toToken()
            },
            listOf(getType("list"), getType("int"), getType("any")),
            false
        )).toToken(),
        "list-add!" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val tokens = cast<MutableList<Token>>(args[0].value)
                if (args.size > 2) {
                    himeAssertRuntime(isType(args[1], getType("int"))) { "${args[1]} is not int." }
                    tokens.add(args[1].value.toString().toInt(), args[2])
                } else
                    tokens.add(args[1])
                return args[0].toToken()
            },
            listOf(getType("list"), getType("any")),
            true
        )).toToken(),
        "list-ref" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val index = args[1].value.toString().toInt()
            val tokens = cast<List<Token>>(args[0].value)
            himeAssertRuntime(index < tokens.size) { "index error." }
            return tokens[index]
        }, listOf(getType("list"), getType("int")), false)).toToken(),
        "++" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var flag = false
            // 判断是否是List，还是string
            for (arg in args)
                if (isType(arg, getType("list"))) {
                    flag = true
                    break
                }
            return if (flag) {
                val list = ArrayList<Token>()
                for (arg in args) {
                    if (isType(arg, getType("list")))
                        list.addAll(cast<List<Token>>(arg.value))
                    else
                        list.add(arg)
                }
                list.toToken()
            } else {
                val builder = StringBuilder()
                for (arg in args)
                    builder.append(arg.toString())
                builder.toString().toToken()
            }
        }, listOf(getType("any")), true)).toToken(),
        "range" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
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
                list.add(start.add(i.multiply(step)).toToken())
                i = i.add(BigInteger.ONE)
            }
            return Token(getType("list"), list)
        }, listOf(getType("any")), true)).toToken(),
        // 获取长度，可以是字符串也可以是列表
        "length" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return when (args[0].type) {
                is HimeTypeString -> cast<String>(args[0].value).length.toToken()
                is HimeTypeList -> cast<List<Token>>(args[0].value).size.toToken()
                else -> args[0].toString().length.toToken()
            }
        }, listOf(getType("any")), false)).toToken(),
        // 反转列表
        "reverse" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val result = ArrayList<Token>()
            val tokens = cast<MutableList<Token>>(args[0].value)
            for (i in tokens.size - 1 downTo 0)
                result.add(tokens[i])
            tokens.clear()
            for (t in result)
                tokens.add(t)
            return tokens.toToken()
        }, listOf(getType("list")), false)).toToken(),
        // 排序
        "sort" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
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
                result.add(e!!.toToken())
            return result.toToken()
        }, listOf(getType("list")), false)).toToken(),
        "curry" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            fun rsc(func: Token, n: Int, parameters: ArrayList<Token>): Token {
                if (n == 0) {
                    // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                    val asts = ASTNode.AST_EMPTY.copy()
                    for (arg in parameters)
                        asts.add(ASTNode(arg))
                    return cast<HimeFunction>(func.value).call(asts, symbol.createChild())
                }
                return (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                    parameters.add(args[0])
                    return rsc(func, n - 1, parameters)
                }, 1)).toToken()
            }
            return rsc(args[0], args[1].value.toString().toInt(), ArrayList())
        }, listOf(getType("function"), getType("int")), false)).toToken(),
        "maybe" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val parameters = ArrayList<Token>()
            for (i in 1 until args.size) {
                if (args[i] == NIL)
                    return NIL
                parameters.add(args[i])
            }
            // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
            val asts = ASTNode.AST_EMPTY.copy()
            for (arg in parameters)
                asts.add(ASTNode(arg))
            return cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())

        }, listOf(getType("function")), true)).toToken(),
        "map" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[1].value)
            for (i in tokens.indices) {
                val parameters = ArrayList<Token>()
                parameters.add(tokens[i])
                // 例如对于(map f (list a b) (list c d))，则执行(f a c)等
                for (j in 1 until args.size - 1) {
                    himeAssertRuntime(isType(args[j + 1], getType("list"))) { "${args[j + 1]} is not list." }
                    parameters.add(cast<List<Token>>(args[j + 1].value)[i])
                }
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                result.add(cast<HimeFunction>(args[0].value).call(asts, symbol.createChild()))
            }
            return result.toToken()
        }, listOf(getType("function"), getType("list")), true)).toToken(),
        "foldr" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            var result = args[1]
            val tokens = cast<List<Token>>(args[2].value)
            for (i in tokens.size - 1 downTo 0) {
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                for (arg in arrayListOf(tokens[i], result))
                    asts.add(ASTNode(arg))
                result = cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
            }
            return result
        }, listOf(getType("function"), getType("any"), getType("list")), false)).toToken(),
        "foldl" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            var result = args[1]
            val tokens = cast<List<Token>>(args[2].value)
            for (i in tokens.size - 1 downTo 0) {
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                for (arg in arrayListOf(result, tokens[i]))
                    asts.add(ASTNode(arg))
                result = cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
            }
            return result
        }, listOf(getType("function"), getType("any"), getType("list")), false)).toToken(),
        "for-each" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val tokens = cast<List<Token>>(args[1].value)
            for (i in tokens.indices) {
                val parameters = ArrayList<Token>()
                parameters.add(tokens[i])
                // 例如对于(map f (list a b) (list c d))，则执行(f a c)等
                for (j in 1 until args.size - 1) {
                    himeAssertRuntime(isType(args[j + 1], getType("list"))) { "${args[j + 1]} is not list." }
                    parameters.add(cast<List<Token>>(args[j + 1].value)[i])
                }
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                for (arg in parameters)
                    asts.add(ASTNode(arg))
                cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
            }
            return NIL
        }, listOf(getType("function"), getType("list")), true)).toToken(),
        "filter" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[1].value)
            for (token in tokens) {
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                asts.add(ASTNode(token))
                val op = cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
                himeAssertRuntime(isType(op, getType("bool"))) { "$op is not bool." }
                if (cast<Boolean>(op.value))
                    result.add(token)
            }
            return result.toToken()
        }, listOf(getType("function"), getType("list")), false)).toToken(),
        "sqrt" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.sqrt(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "sin" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.sin(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "sinh" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.sinh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "asin" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.asin(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "asinh" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.asinh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "cos" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.cos(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "cosh" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.cosh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "acos" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.acos(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "acosh" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.acosh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "tan" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.tan(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "tanh" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.tanh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "atan" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.atan(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "atanh" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.atanh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "atan2" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.atan2(
                BigDecimal(args[0].toString()),
                BigDecimal(args[1].toString()),
                MathContext.DECIMAL64
            ).toToken()
        }, listOf(getType("num"), getType("num")), false)).toToken(),
        "log" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.log(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "log10" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.log10(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "log2" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.log2(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "exp" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.exp(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }, listOf(getType("num")), false)).toToken(),
        "pow" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimalMath.pow(
                BigDecimal(args[0].toString()),
                BigDecimal(args[1].toString()),
                MathContext.DECIMAL64
            ).toToken()
        }, listOf(getType("num"), getType("num")), false)).toToken(),
        "mod" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).mod(BigInteger(args[1].toString())).toToken()
        }, listOf(getType("int"), getType("int")), false)).toToken(),
        "max" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var max = BigDecimal(args[0].toString())
            for (i in 1 until args.size) {
                himeAssertRuntime(isType(args[i], getType("num"))) { "${args[i]} is not num." }
                max = max.max(BigDecimal(args[i].toString()))
            }
            return max.toToken()
        }, listOf(getType("num")), true)).toToken(),
        "min" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            var min = BigDecimal(args[0].toString())
            for (i in 1 until args.size) {
                himeAssertRuntime(isType(args[i], getType("num"))) { "${args[i]} is not num." }
                min = min.min(BigDecimal(args[i].toString()))
            }
            return min.toToken()
        }, listOf(getType("num")), true)).toToken(),
        "abs" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimal(args[0].toString()).abs().toToken()
        }, 1)).toToken(),
        "average" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            var num = BigDecimal.ZERO
            for (arg in args) {
                himeAssertRuntime(isType(arg, getType("num"))) { "$arg is not num." }
                num = num.add(BigDecimal(arg.value.toString()))
            }
            return num.divide(args.size.toBigDecimal()).toToken()
        }, listOf(getType("num")), true)).toToken(),
        "floor" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(
                BigDecimal(args[0].toString()).setScale(0, RoundingMode.FLOOR).toPlainString()
            ).toToken()
        }, 1)).toToken(),
        "ceil" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(
                BigDecimal(args[0].toString()).setScale(0, RoundingMode.CEILING).toPlainString()
            ).toToken()
        }, 1)).toToken(),
        "gcd" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                himeAssertRuntime(isType(arg, getType("int"))) { "$arg is not int." }
            var temp = BigInteger(args[0].toString()).gcd(BigInteger(args[1].toString()))
            for (i in 2 until args.size)
                temp = temp.gcd(BigInteger(args[i].toString()))
            return temp.toToken()
        }, listOf(getType("int"), getType("int")), true)).toToken(),
        // (lcm a b) = (/ (* a b) (gcd a b))
        "lcm" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            fun BigInteger.lcm(n: BigInteger): BigInteger = (this.multiply(n).abs()).divide(this.gcd(n))
            for (arg in args)
                himeAssertRuntime(isType(arg, getType("num"))) { "$arg is not num." }
            var temp = BigInteger(args[0].toString()).lcm(BigInteger(args[1].toString()))
            for (i in 2 until args.size)
                temp = temp.lcm(BigInteger(args[i].toString()))
            return temp.toToken()
        }, listOf(getType("int"), getType("int")), true)).toToken(),
        "->bool" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return if (args[0].toString() == "true") TRUE else FALSE
        }, 1)).toToken(),
        "->string" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].toString().toToken()
        }, 1)).toToken(),
        "->int" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).toToken()
        }, 1)).toToken(),
        "->real" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigDecimal(args[0].toString()).toToken()
        }, 1)).toToken(),
        "string-replace" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].toString().replace(args[1].toString(), args[2].toString()).toToken()
        }, 3)).toToken(),
        "string-substring" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].toString()
                .substring(args[1].value.toString().toInt(), args[2].value.toString().toInt())
                .toToken()
        }, listOf(getType("string"), getType("int"), getType("int")), false)).toToken(),
        "string-split" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].toString().split(args[1].toString()).toList().toToken()
        }, listOf(getType("string"), getType("string")), false)).toToken(),
        "string-index" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].toString().indexOf(args[1].toString()).toToken()
        }, listOf(getType("string"), getType("string")), false)).toToken(),
        "string-last-index" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.size > 1) { "not enough arguments." }
            return args[0].toString().lastIndexOf(args[1].toString()).toToken()
        }, listOf(getType("string"), getType("string")), false)).toToken(),
        "string-format" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val newArgs = arrayOfNulls<Any>(args.size - 1)
            for (i in 1 until args.size)
                newArgs[i - 1] = args[i].value
            return String.format(args[0].toString(), *newArgs).toToken()
        }, listOf(getType("string")), true)).toToken(),
        "string->list" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.isNotEmpty()) { "not enough arguments." }
            val chars = args[0].toString().toCharArray()
            val list = ArrayList<Token>()
            for (c in chars)
                list.add(c.toString().toToken())
            return list.toToken()
        }, listOf(getType("string")), false)).toToken(),
        "list->string" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            val list = cast<List<Token>>(args[0].value)
            for (token in list)
                builder.append(token.toString())
            return builder.toString().toToken()
        }, listOf(getType("list")), false)).toToken(),
        "string->bytes" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            val list = ArrayList<Token>()
            val bytes = builder.toString().toByteArray()
            for (byte in bytes)
                list.add(byte.toToken())
            return list.toToken()
        }, listOf(getType("string")), false)).toToken(),
        "bytes->string" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val list = cast<List<Token>>(args[0].value)
            val bytes = ByteArray(list.size)
            for (index in list.indices) {
                himeAssertRuntime(isType(list[index], getType("byte"))) { "${list[index]} is not byte." }
                bytes[index] = cast<Byte>(list[index].value)
            }
            return String(bytes).toToken()
        }, listOf(getType("list")), false)).toToken(),
        "string->bits" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val s = args[0].toString()
            val result = ArrayList<Token>()
            for (c in s)
                result.add(c.code.toToken())
            return result.toToken()
        }, listOf(getType("string")), false)).toToken(),
        "bits->string" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val result = StringBuilder()
            val tokens = cast<List<Token>>(args[0].value)
            for (t in tokens) {
                himeAssertRuntime(isType(t, getType("int"))) { "$t is not int." }
                result.append(t.value.toString().toInt().toChar())
            }
            return result.toToken()
        }, listOf(getType("list")), false)).toToken(),
        "bool?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("bool")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "string?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("string")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "int?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("int")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "real?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("real")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "list?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("list")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "byte?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("byte")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "function?" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            for (arg in args)
                if (!isType(arg, getType("function")))
                    return FALSE
            return TRUE
        }, listOf(getType("any")), true)).toToken(),
        "exit" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            exitProcess(args[0].value.toString().toInt())
        }, listOf(getType("int")), false)).toToken(),
        "eval" to (HimeFunction(BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
            val newSymbol = symbol.createChild()
            var result = NIL
            for (node in args)
                result = call(node.toString(), newSymbol)
            return result
        }, listOf(getType("string")), true)).toToken(),
        "bit-and" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).and(BigInteger(args[1].toString())).toToken()
        }, listOf(getType("int"), getType("int")), false)).toToken(),
        "bit-or" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).or(BigInteger(args[1].toString())).toToken()
        }, listOf(getType("int"), getType("int")), false)).toToken(),
        "bit-xor" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).xor(BigInteger(args[1].toString())).toToken()
        }, listOf(getType("int"), getType("int")), false)).toToken(),
        "bit-left" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).shiftLeft(BigInteger(args[1].toString()).toInt()).toToken()
        }, listOf(getType("int"), getType("int")), false)).toToken(),
        "bit-right" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(args[0].toString()).shiftRight(BigInteger(args[1].toString()).toInt()).toToken()
        }, listOf(getType("int"), getType("int")), false)).toToken(),
        "error" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            throw HimeRuntimeException(args[0].toString())
        }, listOf(getType("any")), false)).toToken(),
    ), null, null
)

val file = SymbolTable(
    mutableMapOf(
        "file-exists" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return File(args[0].toString()).exists().toToken()
        }, 1)).toToken(),
        "file-list" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val list = ArrayList<Token>()
            val files = File(args[0].toString()).listFiles()
            for (file in files!!)
                list.add(file.path.toToken())
            return list.toToken()
        }, 1)).toToken(),
        "file-mkdirs" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val file = File(args[0].toString())
                if (!file.parentFile.exists())
                    !file.parentFile.mkdirs()
                if (!file.exists())
                    file.createNewFile()
                return NIL
            },
            1
        )).toToken(),
        "file-new" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val file = File(args[0].toString())
                if (!file.exists())
                    file.createNewFile()
                return NIL
            },
            1
        )).toToken(),
        "file-read-string" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return Files.readString(Paths.get(args[0].toString())).toToken()
        }, 1)).toToken(),
        "file-remove" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                File(args[0].toString()).delete()
                return NIL
            },
            1
        )).toToken(),
        "file-write-string" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val file = File(args[0].toString())
                if (!file.parentFile.exists())
                    !file.parentFile.mkdirs()
                if (!file.exists())
                    file.createNewFile()
                Files.writeString(file.toPath(), args[1].toString())
                return NIL
            },
            2
        )).toToken(),
        "file-read-bytes" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val list = ArrayList<Token>()
            val bytes = Files.readAllBytes(Paths.get(args[0].toString()))
            for (byte in bytes)
                list.add(byte.toToken())
            return list.toToken()
        }, 1)).toToken(),
        "file-write-bytes" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                val file = File(args[0].toString())
                if (!file.parentFile.exists())
                    !file.parentFile.mkdirs()
                if (!file.exists())
                    file.createNewFile()
                val list = cast<List<Token>>(args[1].value)
                val bytes = ByteArray(list.size)
                for (index in list.indices) {
                    himeAssertRuntime(isType(list[index], getType("byte"))) { "${list[index]} is not byte." }
                    bytes[index] = cast<Byte>(list[index].value)
                }
                Files.write(file.toPath(), bytes)
                return NIL
            },
            listOf(getType("any"), getType("list")),
            false
        )).toToken()
    ), null, null
)

val time = SymbolTable(
    mutableMapOf(
        "time" to (HimeFunction(BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
            return Date().time.toToken()
        }, 0)).toToken(),
        "time-format" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(isType(args[1], getType("int"))) { "${args[1]} is not int." }
            return SimpleDateFormat(args[0].toString()).format(args[1].toString().toLong())
                .toToken()
        }, 2)).toToken(),
        "time-parse" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return SimpleDateFormat(args[0].toString()).parse(args[1].value.toString()).time.toToken()
        }, 2)).toToken()
    ), null, null
)

val table = SymbolTable(
    mutableMapOf(
        "table" to (HimeFunction(BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
            return mapOf<Token, Token>().toToken()
        }, 0)).toToken(),
        "table-put" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val table = HashMap(cast<Map<Token, Token>>(args[0].value))
            table[args[1]] = args[2]
            return table.toToken()
        }, listOf(getType("table"), getType("any"), getType("any")), false)).toToken(),
        "table-get" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            himeAssertRuntime(args.size > 1) { "not enough arguments." }
            himeAssertRuntime(isType(args[0], getType("table"))) { "${args[0]} is not table." }
            val table = cast<Map<Token, Token>>(args[0].value)
            return table[args[1]] ?: NIL
        }, listOf(getType("table"), getType("any")), false)).toToken(),
        "table-remove" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            val table = HashMap(cast<Map<Token, Token>>(args[0].value))
            table.remove(args[1])
            return table.toToken()
        }, listOf(getType("table"), getType("any")), false)).toToken(),
        "table-keys" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                return cast<Map<Token, Token>>(args[0].value).keys.toList().toToken()
            },
            listOf(getType("table")),
            false
        )).toToken(),
        "table-put!" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<MutableMap<Token, Token>>(args[0].value)[args[1]] = args[2]
                return args[0].toToken()
            },
            listOf(getType("table"), getType("any"), getType("any")),
            false
        )).toToken(),
        "table-remove!" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<MutableMap<Token, Token>>(args[0].value).remove(args[1])
                return args[0].toToken()
            },
            listOf(getType("table"), getType("any")),
            false
        )).toToken()
    ), null, null
)

val thread = SymbolTable(
    mutableMapOf(
        "make-lock" to (HimeFunction(BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
            return ReentrantLock().toToken()
        }, 0)).toToken(),
        "lock" to (HimeFunction(
            BUILT_IN,
            @Synchronized
            fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    cast<ReentrantLock>(arg.value).lock()
                return NIL
            }, listOf(getType("lock")), true
        )).toToken(),
        "unlock" to (HimeFunction(
            BUILT_IN,
            @Synchronized
            fun(args: List<Token>, _: SymbolTable): Token {
                for (arg in args)
                    cast<ReentrantLock>(arg.value).unlock()
                return NIL
            }, listOf(getType("lock")), true
        )).toToken(),
        "get-lock" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<ReentrantLock>(args[0].value).isLocked.toToken()
        }, listOf(getType("lock")), false)).toToken(),
        "sleep" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            Thread.sleep(args[0].toString().toLong())
            return NIL
        }, listOf(getType("int")), false)).toToken(),
        "thread" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, symbol: SymbolTable): Token {
                // 为了能够（简便的）调用HimeFunction，将参数放到一个ast树中
                val asts = ASTNode.AST_EMPTY.copy()
                asts.add(ASTNode(Thread.currentThread().toToken()))
                return if (args.size > 1)
                    Thread({
                        cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
                    }, args[1].toString()).toToken()
                else
                    Thread {
                        cast<HimeFunction>(args[0].value).call(asts, symbol.createChild())
                    }.toToken()
            },
            listOf(getType("function")),
            true
        )).toToken(), //这种类重载函数的参数数量处理还比较棘手
        "thread-start" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<Thread>(args[0].value).start()
                return NIL
            }, listOf(getType("thread")), false
        )).toToken(),
        "thread-current" to (HimeFunction(
            BUILT_IN, fun(_: List<Token>, _: SymbolTable): Token {
                return Thread.currentThread().toToken()
            }, 0
        )).toToken(),
        "thread-name" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<Thread>(args[0].value).name.toToken()
        }, listOf(getType("thread")), false)).toToken(),
        "thread-set-daemon" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<Thread>(args[0].value).isDaemon = cast<Boolean>(args[1].value)
                return NIL
            }, listOf(getType("thread"), getType("bool")), false
        )).toToken(),
        "thread-daemon" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<Thread>(args[0].value).isDaemon.toToken()
        }, listOf(getType("thread")), false)).toToken(),
        "thread-interrupt" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<Thread>(args[0].value).interrupt()
                return NIL
            }, listOf(getType("thread")), false
        )).toToken(),
        "thread-join" to (HimeFunction(
            BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
                cast<Thread>(args[0].value).join()
                return NIL
            }, listOf(getType("thread")), false
        )).toToken(),
        "thread-alive" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<Thread>(args[0].value).isAlive.toToken()
        }, listOf(getType("thread")), false)).toToken(),
        "thread-interrupted" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return cast<Thread>(args[0].value).isInterrupted.toToken()
        }, listOf(getType("thread")), false)).toToken()
    ), null, null
)

val regex = SymbolTable(
    mutableMapOf(
        "match" to (HimeFunction(BUILT_IN, fun(args: List<Token>, _: SymbolTable): Token {
            return args[0].toString().matches(Regex(args[1].toString())).toToken()
        }, 2)).toToken()
    ), null, null
)

val module = mutableMapOf(
    "util.file" to file,
    "util.time" to time,
    "util.table" to table,
    "util.thread" to thread,
    "util.regex" to regex
)
