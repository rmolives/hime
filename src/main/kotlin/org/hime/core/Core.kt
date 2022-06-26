package org.hime.core

import ch.obermuhlner.math.big.BigDecimalMath
import org.hime.*
import org.hime.draw.Coordinate
import org.hime.draw.Draw
import org.hime.parse.*
import org.hime.parse.Type.*
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.image.BufferedImage
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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

val core = SymbolTable(
    mutableMapOf(
        "def" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 1)
            assert(ast[0].tok.type == ID)
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION)
                symbolTable.put(cast<String>(ast[0].tok.value), eval(ast[1], symbolTable.createChild()))
            else {
                val args = ArrayList<String>()
                for (i in 0 until ast[0].size()) {
                    assert(ast[0][i].tok.type == ID)
                    args.add(cast<String>(ast[0][i].tok.value))
                }
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                assert(ast[0].tok.type == ID)
                symbolTable.put(cast<String>(ast[0].tok.value), structureHimeFunction(args, asts, symbolTable))
            }
            return NIL
        }),
        "undef" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 0)
            assert(ast[0].tok.type == ID)
            assert(symbolTable.contains(cast<String>(ast[0].tok.value)))
            symbolTable.remove(cast<String>(ast[0].tok.value))
            return NIL
        }),
        "set" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 1)
            assert(ast[0].tok.type == ID)
            assert(symbolTable.contains(cast<String>(ast[0].tok.value)))
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                if (ast[1].isNotEmpty())
                    ast[1].tok = eval(ast[1], symbolTable.createChild())
                symbolTable.set(cast<String>(ast[0].tok.value), ast[1].tok)
            } else {
                val args = ArrayList<String>()
                for (i in 0 until ast[0].size()) {
                    assert(ast[0][i].tok.type == ID)
                    args.add(cast<String>(ast[0][i].tok.value))
                }
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                assert(ast[0].tok.type == ID)
                symbolTable.set(cast<String>(ast[0].tok.value), structureHimeFunction(args, asts, symbolTable))
            }
            return NIL
        }),
        "lambda" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 1)
            val args = ArrayList<String>()
            if (ast[0].tok.type != EMPTY) {
                assert(ast[0].tok.type == ID)
                args.add(cast<String>(ast[0].tok.value))
                for (i in 0 until ast[0].size()) {
                    assert(ast[0][i].tok.type == ID)
                    args.add(cast<String>(ast[0][i].tok.value))
                }
            }
            val asts = ArrayList<ASTNode>()
            for (i in 1 until ast.size())
                asts.add(ast[i].copy())
            return structureHimeFunction(args, asts, symbolTable)
        }),
        "if" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 1)
            val newSymbolTable = symbolTable.createChild()
            val condition = eval(ast[0], newSymbolTable)
            assert(condition.type == BOOL)
            if (cast<Boolean>(condition.value))
                return eval(ast[1].copy(), newSymbolTable)
            else if (ast.size() > 2)
                return eval(ast[2].copy(), newSymbolTable)
            return NIL
        }),
        "cond" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            val functionSymbolTable = symbolTable.createChild()
            for (astNode in ast.child) {
                if (astNode.tok.type == ID && cast<String>(astNode.tok.value) == "else")
                    return eval(astNode[0].copy(), functionSymbolTable)
                else {
                    val result = eval(astNode[0].copy(), functionSymbolTable)
                    assert(result.type == BOOL)
                    if (cast<Boolean>(result.value))
                        return eval(astNode[1].copy(), functionSymbolTable)
                }
            }
            return NIL
        }),
        "begin" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            val newSymbolTable = symbolTable.createChild()
            var result = NIL
            for (i in 0 until ast.size())
                result = eval(ast[i].copy(), newSymbolTable)
            return result
        }),
        "while" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            val newSymbolTable = symbolTable.createChild()
            var result = NIL
            var bool = eval(ast[0].copy(), symbolTable)
            assert(bool.type == BOOL)
            while (cast<Boolean>(bool.value)) {
                for (i in 1 until ast.size())
                    result = eval(ast[i].copy(), newSymbolTable)
                bool = eval(ast[0].copy(), symbolTable)
                assert(bool.type == BOOL)
            }
            return result
        }),
        "apply" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 0)
            val newAst = ast.copy()
            val op = eval(newAst[0], symbolTable)
            val backups = ArrayList<ASTNode>()
            for (i in 1 until newAst.size())
                backups.add(newAst[i])
            newAst.tok = op
            newAst.clear()
            for (i in backups.indices)
                newAst.add(backups[i])
            return eval(newAst, symbolTable)
        }),
        "require" to Token(FUNCTION, fun(args: List<Token>, symbolTable: SymbolTable): Token {
            assert(args.isNotEmpty())
            val path = args[0].toString()
            if (module.containsKey(path)) {
                for ((key, value) in module[path]!!.table)
                    symbolTable.put(key, value)
                return NIL
            }
            val file = File(System.getProperty("user.dir") + "/" + path.replace(".", "/") + ".hime")
            if (file.exists()) {
                for (node in parser(lexer(preprocessor(Files.readString(file.toPath())))))
                    eval(node, symbolTable)
                return NIL
            }
            val builtURI = symbolTable.javaClass.classLoader.getResource("module/"+ path.replace(".", "/") + ".hime")
            if (builtURI != null) {
            val built = File(builtURI.toURI())
            if (built.exists())
                for (node in parser(lexer(preprocessor(Files.readString(built.toPath())))))
                    eval(node, symbolTable)
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
        "new-line" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
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
        "map" to Token(FUNCTION, fun(args: List<Token>, symbolTable: SymbolTable): Token {
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
                    result.add(cast<Hime_Function>(args[0].value)(functionargs, symbolTable.createChild()))
                else if (args[0].type == HIME_FUNCTION)
                    result.add(cast<Hime_HimeFunction>(args[0].value)(functionargs))
            }
            return result.toToken()
        }),
        "for-each" to Token(FUNCTION, fun(args: List<Token>, symbolTable: SymbolTable): Token {
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
                    cast<Hime_Function>(args[0].value)(functionargs, symbolTable.createChild())
                else if (args[0].type == HIME_FUNCTION)
                    cast<Hime_HimeFunction>(args[0].value)(functionargs)
            }
            return NIL
        }),
        "filter" to Token(FUNCTION, fun(args: List<Token>, symbolTable: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == FUNCTION || args[0].type == HIME_FUNCTION)
            assert(args[1].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(args[1].value)
            for (token in tokens) {
                val op = when (args[0].type) {
                    FUNCTION -> cast<Hime_Function>(args[0].value)(arrayListOf(token), symbolTable.createChild())
                    HIME_FUNCTION -> cast<Hime_HimeFunction>(args[0].value)(arrayListOf(token))
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

val hash = SymbolTable(
    mutableMapOf(
        "sha256" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(args[0].toString().toByteArray())
            val byteBuffer = messageDigest.digest()
            val strHexString = StringBuilder()
            for (b in byteBuffer) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1)
                    strHexString.append('0')
                strHexString.append(hex)
            }
            return strHexString.toString().toToken()
        }),
        "sha512" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            val messageDigest = MessageDigest.getInstance("SHA-512")
            messageDigest.update(args[0].toString().toByteArray())
            val byteBuffer = messageDigest.digest()
            val strHexString = StringBuilder()
            for (b in byteBuffer) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1)
                    strHexString.append('0')
                strHexString.append(hex)
            }
            return strHexString.toString().toToken()
        }),
        "md5" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            return BigInteger(
                1, MessageDigest.getInstance("MD5")
                    .digest(args[0].toString().toByteArray())
            ).toString(16)
                .padStart(32, '0').toToken()
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
        "file-new-file" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
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
        })
    ), null
)

val draw = SymbolTable(
    mutableMapOf(
        "draw" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 2)
            assert(args[0].type == STR)
            assert(args[1].type == NUM)
            assert(args[2].type == NUM)
            return Token(DRAW, Draw(
                cast<String>(args[0].value),
                cast<Int>(args[1].value),
                cast<Int>(args[2].value))
            )
        }),
        // (draw-color draw r g b)
        "draw-color" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 3)
            assert(args[0].type == DRAW)
            assert(args[1].type == NUM)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            (cast<Draw>(args[0].value)).graphics.color =
                Color(
                    cast<Int>(args[1].value),
                    cast<Int>(args[2].value),
                    cast<Int>(args[3].value)
                )
            return args[0]
        }),
        // (coordinate x y)
        "coordinate" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == NUM)
            assert(args[1].type == NUM)
            return Token(COORDINATE, Coordinate(cast<Int>(args[0].value), cast<Int>(args[1].value)))
        }),
        // (draw-clear draw)
        // (draw-clear draw (x y) width height)
        "draw-clear" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.isNotEmpty())
            assert(args[0].type == DRAW)
            val draw = cast<Draw>(args[0].value)
            if (args.size > 3) {
                val coordinate = cast<Coordinate>(args[1].value)
                val width = cast<Int>(args[2].value)
                val height = cast<Int>(args[3].value)
                draw.graphics.clearRect(coordinate.x, coordinate.y, width, height)
                draw.update()
                return args[0]
            }
            draw.image = BufferedImage(draw.frame.width, draw.frame.height, BufferedImage.TYPE_INT_RGB)
            (draw.image.graphics as Graphics2D).setRenderingHint(RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON)
            draw.graphics = draw.image.createGraphics()
            draw.image = draw.graphics.deviceConfiguration.createCompatibleImage(draw.frame.width, draw.frame.height, Transparency.TRANSLUCENT)
            draw.graphics.dispose()
            draw.graphics = draw.image.createGraphics()
            draw.graphics.color = Color.BLACK
            return args[0]
        }),
        // (draw-polygon draw [(x y)])
        "draw-polygon" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == DRAW)
            assert(args[1].type == LIST)
            val draw = cast<Draw>(args[0].value)
            val p = cast<List<Token>>(args[1].value)
            val xa = arrayListOf<Int>()
            val ya = arrayListOf<Int>()
            for (c in p) {
                assert(c.type == COORDINATE)
                val coordinate = cast<Coordinate>(c.value)
                xa.add(coordinate.x)
                ya.add(coordinate.y)
            }
            draw.graphics.drawPolygon(xa.toIntArray(), ya.toIntArray(), xa.size.coerceAtMost(ya.size))
            draw.update()
            return args[0]
        }),
        // (fill-polygon draw [(x y)])
        "fill-polygon" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 1)
            assert(args[0].type == DRAW)
            assert(args[1].type == LIST)
            val draw = cast<Draw>(args[0].value)
            val p = cast<List<Token>>(args[1].value)
            val xa = arrayListOf<Int>()
            val ya = arrayListOf<Int>()
            for (c in p) {
                assert(c.type == COORDINATE)
                val coordinate = cast<Coordinate>(c.value)
                xa.add(coordinate.x)
                ya.add(coordinate.y)
            }
            draw.graphics.fillPolygon(xa.toIntArray(), ya.toIntArray(), xa.size.coerceAtMost(ya.size))
            draw.update()
            return args[0]
        }),
        // (draw-line draw (x1 y1) (x2 y2))
        "draw-line" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 2)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == COORDINATE)
            val draw = cast<Draw>(args[0].value)
            val c1 = cast<Coordinate>(args[1].value)
            val c2 = cast<Coordinate>(args[2].value)
            draw.graphics.drawLine(c1.x, c1.y, c2.x, c2.y)
            draw.update()
            return args[0]
        }),
        // (draw-rect draw (x y) width height)
        "draw-rect" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 3)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            draw.graphics.drawRect(coordinate.x, coordinate.y, width, height)
            draw.update()
            return args[0]
        }),
        // (fill-rect draw (x y) width height)
        "fill-rect" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 3)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            draw.graphics.fillRect(coordinate.x, coordinate.y, width, height)
            draw.update()
            return args[0]
        }),
        // (draw-oval draw (x y) width height)
        "draw-oval" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 3)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            draw.graphics.drawOval(coordinate.x, coordinate.y, width, height)
            draw.update()
            return args[0]
        }),
        // (fill-oval draw (x y) width height)
        "fill-oval" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 3)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            draw.graphics.fillOval(coordinate.x, coordinate.y, width, height)
            draw.update()
            return args[0]
        }),
        // (draw-round-rect draw (x y) width height arcWidth arcHeight)
        "draw-round-rect" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 5)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            assert(args[4].type == NUM)
            assert(args[5].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            val arcWidth = cast<Int>(args[4].value)
            val arcHeight = cast<Int>(args[5].value)
            draw.graphics.drawRoundRect(coordinate.x, coordinate.y, width, height, arcWidth, arcHeight)
            draw.update()
            return args[0]
        }),
        // (fill-round-rect draw (x y) width height arcWidth arcHeight)
        "fill-round-rect" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 5)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            assert(args[4].type == NUM)
            assert(args[5].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            val arcWidth = cast<Int>(args[4].value)
            val arcHeight = cast<Int>(args[5].value)
            draw.graphics.fillRoundRect(coordinate.x, coordinate.y, width, height, arcWidth, arcHeight)
            draw.update()
            return args[0]
        }),
        // (draw-arc draw (x y) width height arcWidth arcHeight)
        "draw-arc" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 5)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            assert(args[4].type == NUM)
            assert(args[5].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            val arcWidth = cast<Int>(args[4].value)
            val arcHeight = cast<Int>(args[5].value)
            draw.graphics.drawArc(coordinate.x, coordinate.y, width, height, arcWidth, arcHeight)
            draw.update()
            return args[0]
        }),
        // (fill-arc draw (x y) width height arcWidth arcHeight)
        "fill-arc" to Token(FUNCTION, fun(args: List<Token>, _: SymbolTable): Token {
            assert(args.size > 5)
            assert(args[0].type == DRAW)
            assert(args[1].type == COORDINATE)
            assert(args[2].type == NUM)
            assert(args[3].type == NUM)
            assert(args[4].type == NUM)
            assert(args[5].type == NUM)
            val draw = cast<Draw>(args[0].value)
            val coordinate = cast<Coordinate>(args[1].value)
            val width = cast<Int>(args[2].value)
            val height = cast<Int>(args[3].value)
            val arcWidth = cast<Int>(args[4].value)
            val arcHeight = cast<Int>(args[5].value)
            draw.graphics.fillArc(coordinate.x, coordinate.y, width, height, arcWidth, arcHeight)
            draw.update()
            return args[0]
        })
    ), null
)

val module = mutableMapOf(
    "util.hash" to hash,
    "util.file" to file,
    "graphics.draw" to draw
)
