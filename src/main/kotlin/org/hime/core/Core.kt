package org.hime.core

import ch.obermuhlner.math.big.BigDecimalMath
import org.hime.*
import org.hime.parse.*
import org.hime.parse.Type.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

val core = SymbolTable(
    mutableMapOf(
        "let" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.isNotEmpty())
            val newSymbol = symbol.createChild()
            var result = NIL
            for (node in ast[0].child) {
                if (node.tok.toString() == "apply") {
                    val parameters = ArrayList<String>()
                    for (i in 0 until node[0].size()) {
                        assert(node[0][i].tok.type == ID)
                        parameters.add(node[0][i].tok.toString())
                    }
                    val asts = ArrayList<ASTNode>()
                    for (i in 1 until node.size())
                        asts.add(node[i].copy())
                    newSymbol.put(node[0].tok.toString(), structureHimeFunction(parameters, asts, symbol.createChild()))
                } else {
                    var value = NIL
                    for (e in node.child)
                        value = eval(e.copy(), symbol.createChild())
                    newSymbol.put(node.tok.toString(), value)
                }
            }
            for (i in 1 until ast.size())
                result = eval(ast[i].copy(), newSymbol.createChild())
            return result
        }),
        "let*" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.isNotEmpty())
            val newSymbol = symbol.createChild()
            var result = NIL
            for (node in ast[0].child) {
                if (node.tok.toString() == "apply") {
                    val parameters = ArrayList<String>()
                    for (i in 0 until node[0].size())
                        parameters.add(node[0][i].tok.toString())
                    val asts = ArrayList<ASTNode>()
                    for (i in 1 until node.size())
                        asts.add(node[i].copy())
                    newSymbol.put(node[0].tok.toString(), structureHimeFunction(parameters, asts, newSymbol))
                } else {
                    var value = NIL
                    for (e in node.child)
                        value = eval(e.copy(), newSymbol.createChild())
                    newSymbol.put(node.tok.toString(), value)
                }
            }
            for (i in 1 until ast.size())
                result = eval(ast[i].copy(), newSymbol.createChild())
            return result
        }),
        "static" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 1)
            assert(symbol.father != null)
            if (!symbol.father!!.table.containsKey(ast[0].tok.toString())) {
                if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION)
                    symbol.father?.put(cast<String>(ast[0].tok.value), eval(ast[1], symbol.createChild()))
                else {
                    val parameters = ArrayList<String>()
                    for (i in 0 until ast[0].size())
                        parameters.add(ast[0][i].tok.toString())
                    val asts = ArrayList<ASTNode>()
                    for (i in 1 until ast.size())
                        asts.add(ast[i].copy())
                    symbol.father?.put(ast[0].tok.toString(), structureHimeFunction(parameters, asts, symbol.createChild()))
                }
            }
            return NIL
        }),
        "def" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 1)
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION)
                symbol.put(ast[0].tok.toString(), eval(ast[1], symbol.createChild()))
            else {
                val parameters = ArrayList<String>()
                for (i in 0 until ast[0].size())
                    parameters.add(ast[0][i].tok.toString())
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.put(cast<String>(ast[0].tok.value), structureHimeFunction(parameters, asts, symbol.createChild()))
            }
            return NIL
        }),
        "undef" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 0)
            assert(ast[0].tok.type == ID)
            assert(symbol.contains(ast[0].tok.value.toString()))
            symbol.remove(cast<String>(ast[0].tok.value))
            return NIL
        }),
        "set" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 1)
            assert(symbol.contains(cast<String>(ast[0].tok.value)))
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                if (ast[1].isNotEmpty())
                    ast[1].tok = eval(ast[1], symbol.createChild())
                symbol.set(ast[0].tok.toString(), ast[1].tok)
            } else {
                val args = ArrayList<String>()
                for (i in 0 until ast[0].size())
                    args.add(ast[0][i].tok.toString())
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                symbol.set(ast[0].tok.toString(), structureHimeFunction(args, asts, symbol))
            }
            return NIL
        }),
        "lambda" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 1)
            val parameters = ArrayList<String>()
            if (ast[0].tok.type != EMPTY) {
                parameters.add(ast[0].tok.toString())
                for (i in 0 until ast[0].size())
                    parameters.add(ast[0][i].tok.toString())
            }
            val asts = ArrayList<ASTNode>()
            for (i in 1 until ast.size())
                asts.add(ast[i].copy())
            return structureHimeFunction(parameters, asts, symbol.createChild())
        }),
        "if" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 1)
            val newSymbol = symbol.createChild()
            val condition = eval(ast[0], newSymbol)
            assert(condition.type == BOOL)
            if (cast<Boolean>(condition.value))
                return eval(ast[1].copy(), newSymbol)
            else if (ast.size() > 2)
                return eval(ast[2].copy(), newSymbol)
            return NIL
        }),
        "cond" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            val newSymbol = symbol.createChild()
            for (node in ast.child) {
                if (node.tok.type == ID && cast<String>(node.tok.value) == "else")
                    return eval(node[0].copy(), newSymbol)
                else {
                    val result = eval(node[0].copy(), newSymbol)
                    assert(result.type == BOOL)
                    if (cast<Boolean>(result.value))
                        return eval(node[1].copy(), newSymbol)
                }
            }
            return NIL
        }),
        "begin" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            val newSymbolTable = symbol.createChild()
            var result = NIL
            for (i in 0 until ast.size())
                result = eval(ast[i].copy(), newSymbolTable)
            return result
        }),
        "while" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            val newSymbolTable = symbol.createChild()
            var result = NIL
            var bool = eval(ast[0].copy(), newSymbolTable)
            assert(bool.type == BOOL)
            while (cast<Boolean>(bool.value)) {
                for (i in 1 until ast.size())
                    result = eval(ast[i].copy(), newSymbolTable)
                bool = eval(ast[0].copy(), newSymbolTable)
                assert(bool.type == BOOL)
            }
            return result
        }),
        "apply" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbol: SymbolTable): Token {
            assert(ast.size() > 0)
            val newAst = ASTNode(eval(ast[0], symbol.createChild()))
            for (i in 1 until ast.size())
                newAst.add(ast[i])
            return eval(newAst, symbol.createChild())
        }),
        "require" to Token(FUNCTION, fun(args: List<Token>, symbol: SymbolTable): Token {
            assert(args.isNotEmpty())
            val path = args[0].toString()
            if (module.containsKey(path)) {
                for ((key, value) in module[path]!!.table)
                    symbol.put(key, value)
                return NIL
            }
            val file = File(System.getProperty("user.dir") + "/" + path.replace(".", "/") + ".hime")
            if (file.exists()) {
                for (node in parser(lexer(preprocessor(Files.readString(file.toPath())))))
                    eval(node, symbol)
                return NIL
            }
            val builtURI = symbol.javaClass.classLoader.getResource("module/"+ path.replace(".", "/") + ".hime")
            if (builtURI != null) {
            val built = File(builtURI.toURI())
            if (built.exists())
                for (node in parser(lexer(preprocessor(Files.readString(built.toPath())))))
                    eval(node, symbol)
            }
            return NIL
        }),
        "read-line" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Scanner(System.`in`).nextLine().toToken()
        }),
        "read" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Scanner(System.`in`).next().toToken()
        }),
        "read-num" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Scanner(System.`in`).nextBigInteger().toToken()
        }),
        "read-real" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Scanner(System.`in`).nextBigDecimal().toToken()
        }),
        "read-bool" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Scanner(System.`in`).nextBoolean().toToken()
        }),
        "read-line" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val input = if (args.isNotEmpty()) {
                assert(args[0].type == IO_INPUT)
                cast<InputStream>(args[0].value)
            } else
                System.`in`
            return Scanner(input).nextLine().toToken()
        }),
        "read" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val input = if (args.isNotEmpty()) {
                assert(args[0].type == IO_INPUT)
                cast<InputStream>(args[0].value)
            } else
                System.`in`
            return Scanner(input).next().toToken()
        }),
        "read-num" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val input = if (args.isNotEmpty()) {
                assert(args[0].type == IO_INPUT)
                cast<InputStream>(args[0].value)
            } else
                System.`in`
            return Scanner(input).nextBigInteger().toToken()
        }),
        "read-real" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val input = if (args.isNotEmpty()) {
                assert(args[0].type == IO_INPUT)
                cast<InputStream>(args[0].value)
            } else
                System.`in`
            return Scanner(input).nextBigDecimal().toToken()
        }),
        "read-bool" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val input = if (args.isNotEmpty()) {
                assert(args[0].type == IO_INPUT)
                cast<InputStream>(args[0].value)
            } else
                System.`in`
            return Scanner(input).nextBoolean().toToken()
        }),
        "write" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == IO_OUT)
            assert(args[1].type == LIST)
            val list = cast<List<Token>>(args[1].value)
            val data = ArrayList<Byte>()
            for (token in list) {
                assert(token.type == BYTE)
                data.add(cast<Byte>(token.value))
            }
            val output = cast<OutputStream>(args[0].value)
            output.write(data.toByteArray())
            return NIL
        }),
        "close" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == IO_OUT || args[0].type == IO_INPUT)
            if (args[0].type == IO_OUT)
                cast<OutputStream>(args[0].value).close()
            else
                cast<InputStream>(args[0].value).close()
            return NIL
        }),
        "flush" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == IO_OUT)
            cast<OutputStream>(args[0].value).flush()
            return NIL
        }),
        "println" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            println(builder.toString())
            return NIL
        }),
        "print" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            print(builder.toString())
            return NIL
        }),
        "newline" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            println()
            return NIL
        }),
        "+" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            var num = BigDecimal.ZERO
            for (parameter in args) {
                assert(parameter.isNum())
                num = num.add(BigDecimal(parameter.toString()))
            }
            return num.toToken()
        }),
        "-" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            var num = BigDecimal(args[0].toString())
            if (args.size == 1)
                return BigDecimal.ZERO.subtract(num).toToken()
            for (i in 1 until args.size) {
                assert(args[i].isNum())
                num = num.subtract(BigDecimal(args[i].toString()))
            }
            return num.toToken()
        }),
        "*" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            var num = BigDecimal.ONE
            for (parameter in args) {
                assert(parameter.isNum())
                num = num.multiply(BigDecimal(parameter.toString()))
            }
            return num.toToken()
        }),
        "/" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            var num = BigDecimal(args[0].toString())
            for (i in 1 until args.size) {
                assert(args[i].isNum())
                num = num.divide(BigDecimal(args[i].toString()), MathContext.DECIMAL64)
            }
            return num.toToken()
        }),
        "and" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args) {
                assert(parameter.type == BOOL)
                if (!cast<Boolean>(parameter.value))
                    return FALSE
            }
            return TRUE
        }),
        "or" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args) {
                assert(parameter.type == BOOL)
                if (cast<Boolean>(parameter.value))
                    return TRUE
            }
            return FALSE
        }),
        "not" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == BOOL)
            return if (cast<Boolean>(args[0].value)) FALSE else TRUE
        }),
        "=" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return if (args[0].toString() == args[1].toString()) TRUE else FALSE
        }),
        "/=" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return if (args[0].toString() != args[1].toString()) TRUE else FALSE
        }),
        ">" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return if (BigDecimal(args[0].toString()) > BigDecimal(args[1].toString())) TRUE else FALSE
        }),
        "<" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return if (BigDecimal(args[0].toString()) < BigDecimal(args[1].toString())) TRUE else FALSE
        }),
        ">=" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return if (BigDecimal(args[0].toString()) >= BigDecimal(args[1].toString())) TRUE else FALSE
        }),
        "<=" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return if (BigDecimal(args[0].toString()) <= BigDecimal(args[1].toString())) TRUE else FALSE
        }),
        "random" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == NUM)
            if (args.size >= 2)
                assert(args[1].type == NUM)
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
        }),
        "cons" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size == 2)
            return ArrayList(args).toToken()
        }),
        "car" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            return cast<List<Token>>(args[0].value)[0]
        }),
        "cdr" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val tokens = cast<List<Token>>(args[0].value)
            val list = ArrayList<Token>()
            for (i in 1 until tokens.size)
                list.add(tokens[i])
            if (list.size == 1)
                return list[0]
            return list.toToken()
        }),
        "list" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            return ArrayList(args).toToken()
        }),
        "head" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            return cast<List<Token>>(args[0].value)[0]
        }),
        "last" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val tokens = cast<List<Token>>(args[0].value)
            return tokens[tokens.size - 1]
        }),
        "tail" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val tokens = cast<List<Token>>(args[0].value)
            val list = ArrayList<Token>()
            for (i in 1 until tokens.size)
                list.add(tokens[i])
            if (list.size == 1)
                return list[0]
            return list.toToken()
        }),
        "init" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val tokens = cast<List<Token>>(args[0].value)
            if (tokens.size == 1)
                return tokens[0]
            val list = ArrayList<Token>()
            for (i in 0 until tokens.size - 1)
                list.add(tokens[i])
            if (list.size == 1)
                return list[0]
            return list.toToken()
        }),
        "list-set" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 2)
            assert(args[0].type == LIST)
            assert(args[1].type == NUM)
            val index = cast<Int>(args[1].value)
            val tokens = ArrayList(cast<List<Token>>(args[0].value))
            assert(index < tokens.size)
            tokens[index] = args[2]
            return tokens.toToken()
        }),
        "list-add" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == LIST)
            val tokens = ArrayList(cast<List<Token>>(args[0].value))
            if (args.size > 2) {
                assert(args[1].type == NUM)
                val index = cast<Int>(args[1].value)
                assert(index < tokens.size)
                tokens.add(index, args[2])
            } else
                tokens.add(args[1])
            return tokens.toToken()
        }),
        "list-get" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == LIST)
            assert(args[1].type == NUM)
            val index = cast<Int>(args[1].value)
            val tokens = cast<List<Token>>(args[0].value)
            assert(index < tokens.size)
            return tokens[index]
        }),
        "++" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return if (args[0].type == LIST) {
                val list = ArrayList<Token>()
                for (parameter in args) {
                    if (parameter.type == LIST)
                        list.addAll(cast<List<Token>>(parameter.value))
                    else
                        list.add(parameter)
                }
                list.toToken()
            } else {
                val builder = StringBuilder()
                for (parameter in args)
                    builder.append(parameter.toString())
                builder.toString().toToken()
            }
        }),
        "range" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val start = if (args.size >= 2) BigInteger(args[0].toString()) else BigInteger.ZERO
            val end =
                if (args.size >= 2) BigInteger(args[1].toString()) else BigInteger(args[0].toString())
            val step = if (args.size >= 3) BigInteger(args[2].toString()) else BigInteger.ONE
            val size = end.subtract(start).divide(step)
            val list = ArrayList<Token>()
            var i = BigInteger.ZERO
            while (i.compareTo(size) != 1) {
                list.add(start.add(i.multiply(step)).toToken())
                i = i.add(BigInteger.ONE)
            }
            return Token(LIST, list)
        }),
        "length" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return when (args[0].type) {
                STR -> cast<String>(args[0].value).length.toToken()
                LIST -> cast<List<Token>>(args[0].value).size.toToken()
                else -> args[0].toString().length.toToken()
            }
        }),
        "reverse" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[0].value)
            for (i in tokens.size - 1 downTo 0)
                result.add(tokens[i])
            return result.toToken()
        }),
        "sort" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
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

            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[0].value)
            val list = arrayOfNulls<BigDecimal>(tokens.size)
            for (i in tokens.indices)
                list[i] = BigDecimal(tokens[i].toString())
            mergeSort(list, 0, list.size - 1)
            for (e in list)
                result.add(e!!.toToken())
            return result.toToken()
        }),
        "map" to Token(FUNCTION, fun(args: List<Token>, symbol: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == FUNCTION || args[0].type == HIME_FUNCTION)
            assert(args[1].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[1].value)
            for (i in tokens.indices) {
                val functionargs = ArrayList<Token>()
                functionargs.add(tokens[i])
                for (j in 1 until args.size - 1)
                    functionargs.add(cast<List<Token>>(args[j + 1].value)[i])
                if (args[0].type == FUNCTION)
                    result.add(cast<Hime_Function>(args[0].value)(functionargs, symbol.createChild()))
                else if (args[0].type == HIME_FUNCTION)
                    result.add(cast<Hime_HimeFunction>(cast<Hime_HimeFunctionPair>(args[0].value).second)(functionargs))
            }
            return result.toToken()
        }),
        "for-each" to Token(FUNCTION, fun(args: List<Token>, symbol: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == FUNCTION || args[0].type == HIME_FUNCTION)
            assert(args[1].type == LIST)
            val tokens = cast<List<Token>>(args[1].value)
            for (i in tokens.indices) {
                val functionargs = ArrayList<Token>()
                functionargs.add(tokens[i])
                for (j in 1 until args.size - 1)
                    functionargs.add(cast<List<Token>>(args[j + 1].value)[i])
                if (args[0].type == FUNCTION)
                    cast<Hime_Function>(args[0].value)(functionargs, symbol.createChild())
                else if (args[0].type == HIME_FUNCTION)
                    cast<Hime_HimeFunction>(cast<Hime_HimeFunctionPair>(args[0].value).second)(functionargs)
            }
            return NIL
        }),
        "filter" to Token(FUNCTION, fun(args: List<Token>, symbol: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == FUNCTION || args[0].type == HIME_FUNCTION)
            assert(args[1].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[1].value)
            for (token in tokens) {
                val op = when (args[0].type) {
                    FUNCTION -> cast<Hime_Function>(args[0].value)(arrayListOf(token), symbol.createChild())
                    HIME_FUNCTION -> cast<Hime_HimeFunction>(cast<Hime_HimeFunctionPair>(args[0].value).second)(arrayListOf(token))
                    else -> NIL
                }
                assert(op.type == BOOL)
                if (cast<Boolean>(op.value))
                    result.add(token)
            }
            return result.toToken()
        }),
        "sqrt" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.sqrt(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "sin" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.sin(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "sinh" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.sinh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "asin" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.asin(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "asinh" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.asinh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "cos" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.cos(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "cosh" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.cosh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "acos" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.acos(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "acosh" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.acosh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "tan" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.tan(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "tanh" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.tanh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "atan" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.atan(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "atanh" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.atanh(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "atan2" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].isNum())
            assert(args[1].isNum())
            return BigDecimalMath.atan2(
                BigDecimal(args[0].toString()),
                BigDecimal(args[1].toString()),
                MathContext.DECIMAL64
            ).toToken()
        }),
        "log" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.log(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "log10" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.log10(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "log2" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.log2(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "exp" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].isNum())
            return BigDecimalMath.exp(BigDecimal(args[0].toString()), MathContext.DECIMAL64).toToken()
        }),
        "pow" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].isNum())
            assert(args[1].isNum())
            return BigDecimalMath.pow(
                BigDecimal(args[0].toString()),
                BigDecimal(args[1].toString()),
                MathContext.DECIMAL64
            ).toToken()
        }),
        "mod" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].isNum())
            assert(args[1].isNum())
            return BigInteger(args[0].toString()).mod(BigInteger(args[1].toString())).toToken()
        }),
        "max" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            var max = BigDecimal(args[0].toString())
            for (i in 1 until args.size)
                max = max.max(BigDecimal(args[i].toString()))
            return max.toToken()
        }),
        "min" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            var min = BigDecimal(args[0].toString())
            for (i in 1 until args.size)
                min = min.min(BigDecimal(args[i].toString()))
            return min.toToken()
        }),
        "abs" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return BigDecimal(args[0].toString()).abs().toToken()
        }),
        "average" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            var num = BigDecimal.ZERO
            for (parameter in args)
                num = num.add(BigDecimal(parameter.value.toString()))
            return num.divide(args.size.toBigDecimal()).toToken()
        }),
        "floor" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return BigInteger(
                BigDecimal(args[0].toString()).setScale(0, RoundingMode.FLOOR).toPlainString()
            ).toToken()
        }),
        "ceil" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return BigInteger(
                BigDecimal(args[0].toString()).setScale(0, RoundingMode.CEILING).toPlainString()
            ).toToken()
        }),
        "gcd" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            for (parameter in args)
                assert(parameter.type == NUM || parameter.type == BIG_NUM)
            var temp = BigInteger(args[0].toString()).gcd(BigInteger(args[1].toString()))
            for (i in 2 until args.size)
                temp = temp.gcd(BigInteger(args[i].toString()))
            return temp.toToken()
        }),
        "lcm" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            fun BigInteger.lcm(n: BigInteger): BigInteger = (this.multiply(n).abs()).divide(this.gcd(n))
            assert(args.size > 1)
            for (parameter in args)
                assert(parameter.type == NUM || parameter.type == BIG_NUM)
            var temp = BigInteger(args[0].toString()).lcm(BigInteger(args[1].toString()))
            for (i in 2 until args.size)
                temp = temp.lcm(BigInteger(args[i].toString()))
            return temp.toToken()
        }),
        "->string" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return args[0].toString().toToken()
        }),
        "->num" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return BigInteger(args[0].toString()).toToken()
        }),
        "->real" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return BigDecimal(args[0].toString()).toToken()
        }),
        "string-replace" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 2)
            return args[0].toString().replace(args[1].toString(), args[2].toString()).toToken()
        }),
        "string-substring" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 2)
            assert(args[1].type == NUM)
            assert(args[2].type == NUM)
            return args[0].toString()
                .substring(cast<Int>(args[1].value), cast<Int>(args[2].value))
                .toToken()
        }),
        "string-split" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            val result = ArrayList<Token>()
            val list = args[0].toString().split(args[1].toString())
            for (s in list)
                result.add(s.toToken())
            return result.toToken()
        }),
        "string-index" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return args[0].toString().indexOf(args[1].toString()).toToken()
        }),
        "string-last-index" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            return args[0].toString().lastIndexOf(args[1].toString()).toToken()
        }),
        "string-format" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            val newArgs = arrayOfNulls<Any>(args.size - 1)
            for (i in 1 until args.size)
                newArgs[i - 1] = args[i].value
            return String.format(args[0].toString(), *newArgs).toToken()
        }),
        "string->list" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val chars = args[0].toString().toCharArray()
            val list = ArrayList<Token>()
            for (c in chars)
                list.add(c.toString().toToken())
            return list.toToken()
        }),
        "list->string" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val builder = StringBuilder()
            val list = cast<List<Token>>(args[0].value)
            for (token in list)
                builder.append(token.toString())
            return builder.toString().toToken()
        }),
        "string->bytes" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in args)
                builder.append(token.toString())
            val list = ArrayList<Token>()
            val bytes = builder.toString().toByteArray()
            for (byte in bytes)
                list.add(byte.toToken())
            return list.toToken()
        }),
        "bytes->string" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == LIST)
            val list = cast<List<Token>>(args[0].value)
            val bytes = ByteArray(list.size)
            for (index in list.indices)
                bytes[index] = cast<Byte>(list[index].value)
            return String(bytes).toToken()
        }),
        "bool?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != BOOL)
                    return FALSE
            return TRUE
        }),
        "string?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != STR)
                    return FALSE
            return TRUE
        }),
        "num?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != NUM && parameter.type != BIG_NUM)
                    return FALSE
            return TRUE
        }),
        "real?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != REAL && parameter.type != BIG_REAL)
                    return FALSE
            return TRUE
        }),
        "list?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != LIST)
                    return FALSE
            return TRUE
        }),
        "byte?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != BYTE)
                    return FALSE
            return TRUE
        }),
        "function?" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            for (parameter in args)
                if (parameter.type != FUNCTION && parameter.type != STATIC_FUNCTION && parameter.type != HIME_FUNCTION)
                    return FALSE
            return TRUE
        }),
        "exit" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == NUM)
            exitProcess(cast<Int>(args[0].value))
        }),
        "time" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Date().time.toToken()
        }),
        "time-format" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == STR)
            assert(args[1].type == NUM || args[1].type == BIG_NUM)
            return SimpleDateFormat(cast<String>(args[0].value)).format(BigInteger(args[1].toString()).toLong())
                .toToken()
        }),
        "time-parse" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == STR)
            assert(args[1].type == STR)
            return SimpleDateFormat(cast<String>(args[0].value)).parse(cast<String>(args[1].value)).time.toToken()
        }),
        "extern" to Token(FUNCTION, fun(args: List<Token>, symbolTable: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == STR)
            assert(args[1].type == STR)
            val clazz = args[0].toString()
            val name = args[1].toString()
            val method = Class.forName(clazz).declaredMethods
                .firstOrNull { Modifier.isStatic(it.modifiers) && it.name == name }
                ?: throw UnsatisfiedLinkError("Method $name not found for class $clazz.")
            symbolTable.put(name, Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
                val newArgs = arrayOfNulls<Any>(args.size)
                for (i in args.indices)
                    newArgs[i] = args[i].value
                return method.invoke(null, *newArgs).toToken()
            }))
            return NIL
        })
    ), null
)

val file = SymbolTable(
    mutableMapOf(
        "file-input" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return Files.newInputStream(Paths.get(args[0].toString())).toToken()
        }),
        "file-out" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return Files.newOutputStream(Paths.get(args[0].toString())).toToken()
        }),
        "file-exists" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return File(args[0].toString()).exists().toToken()
        }),
        "file-list" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            fun listAllFile(f: File): Token {
                val list = ArrayList<Token>()
                val files = f.listFiles()
                for (file in files!!) {
                    if (file.isDirectory)
                        list.add(listAllFile(file))
                    else
                        list.add(file.path.toToken())
                }
                return list.toToken()
            }
            assert(args.isNotEmpty())
            return listAllFile(File(args[0].toString()))
        }),
        "file-mkdirs" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val file = File(args[0].toString())
            if (!file.parentFile.exists())
                !file.parentFile.mkdirs()
            if (!file.exists())
                file.createNewFile()
            return NIL
        }),
        "file-new" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val file = File(args[0].toString())
            if (!file.exists())
                file.createNewFile()
            return NIL
        }),
        "file-read-string" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            return Files.readString(Paths.get(args[0].toString())).toToken()
        }),
        "file-remove" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            File(args[0].toString()).delete()
            return NIL
        }),
        "file-write-string" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            val file = File(args[0].toString())
            if (!file.parentFile.exists())
                !file.parentFile.mkdirs()
            if (!file.exists())
                file.createNewFile()
            Files.writeString(file.toPath(), args[1].toString())
            return NIL
        }),
        "file-read-bytes" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val list = ArrayList<Token>()
            val bytes = Files.readAllBytes(Paths.get(args[0].toString()))
            for (byte in bytes)
                list.add(byte.toToken())
            return list.toToken()
        }),
        "file-write-bytes" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[1].type == LIST)
            val file = File(args[0].toString())
            if (!file.parentFile.exists())
                !file.parentFile.mkdirs()
            if (!file.exists())
                file.createNewFile()
            val list = cast<List<Token>>(args[1].value)
            val bytes = ByteArray(list.size)
            for (index in list.indices) {
                assert(list[index].type == BYTE)
                bytes[index] = cast<Byte>(list[index].value)
            }
            Files.write(file.toPath(), bytes)
            return NIL
        }),
        "eval" to Token(FUNCTION, fun(args: List<Token>, symbol: SymbolTable): Token {
            assert(args.isNotEmpty())
            val newSymbol = symbol.createChild()
            var result = NIL
            for (node in args)
                result = call(node.toString(), newSymbol)
            return result
        })
    ), null
)

val bit = SymbolTable(
    mutableMapOf(
        "&" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            return NIL
        })
    ), null
)

val module = mutableMapOf(
    "util.file" to file,
    "util.bit" to bit
)
