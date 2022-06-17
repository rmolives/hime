package org.hime.core

import ch.obermuhlner.math.big.BigDecimalMath
import org.hime.cast
import org.hime.exceptions.HimeModuleException
import org.hime.parse.*
import org.hime.parse.Type.*
import org.hime.simplification
import org.hime.toToken
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
            if (ast[0].isEmpty() && ast[0].type != AstType.FUNCTION) {
                if (ast[1].isNotEmpty())
                    ast[1].tok = call(ast[1], symbolTable.createChild())
                symbolTable.put(cast<String>(ast[0].tok.value), ast[1].tok)
            } else {
                val parameters = ArrayList<String>()
                for (i in 0 until ast[0].size()) {
                    assert(ast[0][i].tok.type == ID)
                    parameters.add(cast<String>(ast[0][i].tok.value))
                }
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                assert(ast[0].tok.type == ID)
                symbolTable.put(cast<String>(ast[0].tok.value), structureHimeFunction(parameters, asts, symbolTable))
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
                    ast[1].tok = call(ast[1], symbolTable.createChild())
                symbolTable.set(cast<String>(ast[0].tok.value), ast[1].tok)
            } else {
                val parameters = ArrayList<String>()
                for (i in 0 until ast[0].size()) {
                    assert(ast[0][i].tok.type == ID)
                    parameters.add(cast<String>(ast[0][i].tok.value))
                }
                val asts = ArrayList<ASTNode>()
                for (i in 1 until ast.size())
                    asts.add(ast[i].copy())
                assert(ast[0].tok.type == ID)
                symbolTable.set(cast<String>(ast[0].tok.value), structureHimeFunction(parameters, asts, symbolTable))
            }
            return NIL
        }),
        "lambda" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 1)
            val parameters = ArrayList<String>()
            if (ast[0].tok.type != EMPTY) {
                assert(ast[0].tok.type == ID)
                parameters.add(cast<String>(ast[0].tok.value))
                for (i in 0 until ast[0].size()) {
                    assert(ast[0][i].tok.type == ID)
                    parameters.add(cast<String>(ast[0][i].tok.value))
                }
            }
            val asts = ArrayList<ASTNode>()
            for (i in 1 until ast.size())
                asts.add(ast[i].copy())
            return structureHimeFunction(parameters, asts, symbolTable)
        }),
        "if" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 1)
            val newSymbolTable = symbolTable.createChild()
            val condition = call(ast[0], newSymbolTable)
            assert(condition.type == BOOL)
            if (cast<Boolean>(condition.value))
                return call(ast[1].copy(), newSymbolTable)
            else if (ast.size() > 2)
                return call(ast[2].copy(), newSymbolTable)
            return NIL
        }),
        "cond" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            val functionSymbolTable = symbolTable.createChild()
            for (astNode in ast.child) {
                if (astNode.tok.type == ID && cast<String>(astNode.tok.value) == "else")
                    return call(astNode[0].copy(), functionSymbolTable)
                else {
                    val result = call(astNode[0].copy(), functionSymbolTable)
                    assert(result.type == BOOL)
                    if (cast<Boolean>(result.value))
                        return call(astNode[1].copy(), functionSymbolTable)
                }
            }
            return NIL
        }),
        "begin" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            val newSymbolTable = symbolTable.createChild()
            var result = NIL
            for (i in 0 until ast.size())
                result = call(ast[i].copy(), newSymbolTable)
            return result
        }),
        "while" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            val newSymbolTable = symbolTable.createChild()
            var result = NIL
            var bool = call(ast[0].copy(), symbolTable)
            assert(bool.type == BOOL)
            while (cast<Boolean>(bool.value)) {
                for (i in 1 until ast.size())
                    result = call(ast[i].copy(), newSymbolTable)
                bool = call(ast[0].copy(), symbolTable)
                assert(bool.type == BOOL)
            }
            return result
        }),
        "apply" to Token(STATIC_FUNCTION, fun(ast: ASTNode, symbolTable: SymbolTable): Token {
            assert(ast.size() > 0)
            val newAst = ast.copy()
            val op = call(newAst[0], symbolTable)
            val backups = ArrayList<ASTNode>()
            for (i in 1 until newAst.size())
                backups.add(newAst[i])
            newAst.tok = op
            newAst.clear()
            for (i in backups.indices)
                newAst.add(backups[i])
            return call(newAst, symbolTable)
        }),
        "require" to Token(FUNCTION, fun(parameters: List<Token>, symbolTable: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val path = parameters[0].toString()
            if (module.containsKey(path)) {
                for ((key, value) in module[path]!!.table)
                    symbolTable.put(key, value)
                return NIL
            }
            val file = File(System.getProperty("user.dir") + "/" + path.replace(".", "/") + ".hime")
            if (file.exists())
                for (node in parser(lexer(format(Files.readString(file.toPath())))))
                    call(node, symbolTable)
            else
                throw HimeModuleException("Module $path does not exist!!!")
            return NIL
        }),
        "read-line" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val input = if (parameters.isNotEmpty()) {
                assert(parameters[0].type == IO_INPUT)
                cast<InputStream>(parameters[0].value)
            } else
                System.`in`
            return Scanner(input).nextLine().toToken()
        }),
        "read" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val input = if (parameters.isNotEmpty()) {
                assert(parameters[0].type == IO_INPUT)
                cast<InputStream>(parameters[0].value)
            } else
                System.`in`
            return Scanner(input).next().toToken()
        }),
        "read-num" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val input = if (parameters.isNotEmpty()) {
                assert(parameters[0].type == IO_INPUT)
                cast<InputStream>(parameters[0].value)
            } else
                System.`in`
            return Scanner(input).nextBigInteger().toToken()
        }),
        "read-real" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val input = if (parameters.isNotEmpty()) {
                assert(parameters[0].type == IO_INPUT)
                cast<InputStream>(parameters[0].value)
            } else
                System.`in`
            return Scanner(input).nextBigDecimal().toToken()
        }),
        "read-bool" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val input = if (parameters.isNotEmpty()) {
                assert(parameters[0].type == IO_INPUT)
                cast<InputStream>(parameters[0].value)
            } else
                System.`in`
            return Scanner(input).nextBoolean().toToken()
        }),
        "write" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == IO_OUT)
            assert(parameters[1].type == LIST)
            val list = cast<List<Token>>(parameters[1].value)
            val data = ArrayList<Byte>()
            for (token in list) {
                assert(token.type == BYTE)
                data.add(cast<Byte>(token.value))
            }
            val output = cast<OutputStream>(parameters[0].value)
            output.write(data.toByteArray())
            return NIL
        }),
        "close" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == IO_OUT || parameters[0].type == IO_INPUT)
            if (parameters[0].type == IO_OUT)
                cast<OutputStream>(parameters[0].value).close()
            else
                cast<InputStream>(parameters[0].value).close()
            return NIL
        }),
        "flush" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == IO_OUT)
            cast<OutputStream>(parameters[0].value).flush()
            return NIL
        }),
        "system-out" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return System.out.toToken()
        }),
        "system-input" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return System.`in`.toToken()
        }),
        "println" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in parameters)
                builder.append(token.toString())
            println(builder.toString())
            return NIL
        }),
        "print" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in parameters)
                builder.append(token.toString())
            print(builder.toString())
            return NIL
        }),
        "new-line" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            println()
            return NIL
        }),
        "+" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            return if (parameters.isNotEmpty()) {
                var num = BigDecimal.ZERO
                for (parameter in parameters) {
                    assert(parameter.type == NUM || parameter.type == REAL)
                    num = num.add(BigDecimal(parameter.toString()))
                }
                if (!num.simplification().toPlainString().contains("."))
                    BigInteger(num.simplification().toPlainString()).toToken()
                else
                    num.toToken()
            } else
                Token(NUM, BigInteger.ZERO)
        }),
        "-" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            return if (parameters.isNotEmpty()) {
                var num = BigDecimal(parameters[0].toString())
                for (i in 1 until parameters.size) {
                    assert(parameters[i].type == NUM || parameters[i].type == REAL)
                    num = num.subtract(BigDecimal(parameters[i].toString()))
                }
                if (!num.simplification().toPlainString().contains("."))
                    BigInteger(num.simplification().toPlainString()).toToken()
                else
                    num.toToken()
            } else
                Token(NUM, BigInteger.ZERO)
        }),
        "*" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            return if (parameters.isNotEmpty()) {
                var num = BigDecimal.ONE
                for (parameter in parameters) {
                    assert(parameter.type == NUM || parameter.type == REAL)
                    num = num.multiply(BigDecimal(parameter.toString()))
                }
                if (!num.simplification().toPlainString().contains("."))
                    BigInteger(num.simplification().toPlainString()).toToken()
                else
                    num.toToken()
            } else
                Token(NUM, BigInteger.ZERO)
        }),
        "/" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            return if (parameters.isNotEmpty()) {
                var num = BigDecimal(parameters[0].toString())
                for (i in 1 until parameters.size) {
                    assert(parameters[i].type == NUM || parameters[i].type == REAL)
                    num = num.divide(BigDecimal(parameters[i].toString()))
                }
                if (!num.simplification().toPlainString().contains("."))
                    BigInteger(num.simplification().toPlainString()).toToken()
                else
                    num.toToken()
            } else
                Token(NUM, BigInteger.ZERO)
        }),
        "and" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters) {
                assert(parameter.type == BOOL)
                if (!cast<Boolean>(parameter.value))
                    return FALSE
            }
            return TRUE
        }),
        "or" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters) {
                assert(parameter.type == BOOL)
                if (cast<Boolean>(parameter.value))
                    return TRUE
            }
            return FALSE
        }),
        "not" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == BOOL)
            return if (cast<Boolean>(parameters[0].value)) FALSE else TRUE
        }),
        "=" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return if (parameters[0].toString() == parameters[1].toString()) TRUE else FALSE
        }),
        "/=" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return if (parameters[0].toString() != parameters[1].toString()) TRUE else FALSE
        }),
        ">" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return if (BigDecimal(parameters[0].toString()) > BigDecimal(parameters[1].toString())) TRUE else FALSE
        }),
        "<" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return if (BigDecimal(parameters[0].toString()) < BigDecimal(parameters[1].toString())) TRUE else FALSE
        }),
        ">=" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return if (BigDecimal(parameters[0].toString()) >= BigDecimal(parameters[1].toString())) TRUE else FALSE
        }),
        "<=" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return if (BigDecimal(parameters[0].toString()) <= BigDecimal(parameters[1].toString())) TRUE else FALSE
        }),
        "random" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM)
            if (parameters.size >= 2)
                assert(parameters[1].type == NUM)
            val start = if (parameters.size == 1) BigInteger.ZERO else cast<BigInteger>(parameters[0].value)
            val end =
                if (parameters.size == 1) cast<BigInteger>(parameters[0].value) else cast<BigInteger>(parameters[1].value)
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
        "list" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            return ArrayList(parameters).toToken()
        }),
        "head" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            return cast<List<Token>>(parameters[0].value)[0]
        }),
        "last" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            val tokens = cast<List<Token>>(parameters[0].value)
            return tokens[tokens.size - 1]
        }),
        "tail" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            val tokens = cast<List<Token>>(parameters[0].value)
            val list = ArrayList<Token>()
            for (i in 1 until tokens.size)
                list.add(tokens[i])
            return list.toToken()
        }),
        "init" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            val tokens = cast<List<Token>>(parameters[0].value)
            val list = ArrayList<Token>()
            for (i in 0 until tokens.size - 1)
                list.add(tokens[i])
            return list.toToken()
        }),
        "list-set" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 2)
            assert(parameters[0].type == LIST)
            assert(parameters[1].type == NUM)
            val index = cast<BigInteger>(parameters[1].value)
            assert(index < BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            val tokens = ArrayList(cast<List<Token>>(parameters[0].value))
            assert(index.intValueExact() < tokens.size)
            tokens[index.intValueExact()] = parameters[2]
            return tokens.toToken()
        }),
        "list-add" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 2)
            assert(parameters[0].type == LIST)
            assert(parameters[1].type == NUM)
            val index = cast<BigInteger>(parameters[1].value)
            assert(index < BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            val tokens = ArrayList(cast<List<Token>>(parameters[0].value))
            assert(index.intValueExact() < tokens.size)
            tokens.add(index.intValueExact(), parameters[2])
            return tokens.toToken()
        }),
        "list-get" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == LIST)
            assert(parameters[1].type == NUM)
            val index = cast<BigInteger>(parameters[1].value)
            assert(index < BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            val tokens = cast<List<Token>>(parameters[0].value)
            assert(index.intValueExact() < tokens.size)
            return tokens[index.intValueExact()]
        }),
        "++" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return if (parameters[0].type == LIST) {
                val list = ArrayList<Token>()
                for (parameter in parameters) {
                    if (parameter.type == LIST)
                        list.addAll(cast<List<Token>>(parameter.value))
                    else
                        list.add(parameter)
                }
                list.toToken()
            } else {
                val builder = StringBuilder()
                for (parameter in parameters)
                    builder.append(parameter.toString())
                builder.toString().toToken()
            }
        }),
        "range" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val start = if (parameters.size >= 2) cast<BigInteger>(parameters[0].value) else BigInteger.ZERO
            val end =
                if (parameters.size >= 2) cast<BigInteger>(parameters[1].value) else cast<BigInteger>(parameters[0].value)
            val step = if (parameters.size >= 3) cast<BigInteger>(parameters[2].value) else BigInteger.ONE
            val size = end.subtract(start).divide(step)
            val list = ArrayList<Token>()
            var i = BigInteger.ZERO
            while (i.compareTo(size) != 1) {
                list.add(start.add(i.multiply(step)).toToken())
                i = i.add(BigInteger.ONE)
            }
            return Token(LIST, list)
        }),
        "length" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return when (parameters[0].type) {
                STR -> cast<String>(parameters[0].value).length.toLong().toToken()
                LIST -> cast<List<Token>>(parameters[0].value).size.toLong().toToken()
                else -> parameters[0].toString().length.toLong().toToken()
            }
        }),
        "reverse" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(parameters[0].value)
            for (i in tokens.size - 1 downTo 0)
                result.add(tokens[i])
            return result.toToken()
        }),
        "sort" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            fun merge(a: Array<BigDecimal?>, low: Int, mid: Int, high: Int) {
                val temp = arrayOfNulls<BigDecimal>(high - low + 1)
                var i = low
                var j = mid + 1
                var k = 0
                while (i <= mid && j <= high)
                    temp[k++] = if (a[i]!! > a[j]) a[i++] else a[j++]
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
            val tokens = cast<List<Token>>(parameters[0].value)
            val list = arrayOfNulls<BigDecimal>(tokens.size)
            for (i in tokens.indices) {
                list[i] = BigDecimal(tokens[i].toString())
            }
            mergeSort(list, 0, list.size - 1)
            for (i in list.indices) {
                val s = list[i]?.simplification().toString()
                if (s.contains("."))
                    result[i] = list[i]!!.toToken()
                else
                    result[i] = BigInteger(list[i]!!.simplification().toPlainString()).toToken()
            }
            return result.toToken()
        }),
        "map" to Token(FUNCTION, fun(parameters: List<Token>, symbolTable: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == FUNCTION || parameters[0].type == HIME_FUNCTION)
            assert(parameters[1].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(parameters[1].value)
            for (i in tokens.indices) {
                val functionParameters = ArrayList<Token>()
                functionParameters.add(tokens[i])
                for (j in 1 until parameters.size - 1)
                    functionParameters.add(cast<List<Token>>(parameters[j + 1].value)[i])
                if (parameters[0].type == FUNCTION)
                    result.add(cast<Hime_Function>(parameters[0].value)(functionParameters, symbolTable.createChild()))
                else if (parameters[0].type == HIME_FUNCTION)
                    result.add(cast<Hime_HimeFunction>(parameters[0].value)(functionParameters))
            }
            return result.toToken()
        }),
        "filter" to Token(FUNCTION, fun(parameters: List<Token>, symbolTable: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == FUNCTION || parameters[0].type == HIME_FUNCTION)
            assert(parameters[1].type == LIST)
            val result = ArrayList<Token>()
            val tokens = cast<List<Token>>(parameters[1].value)
            for (token in tokens) {
                val op = when (parameters[0].type) {
                    FUNCTION -> cast<Hime_Function>(parameters[0].value)(arrayListOf(token), symbolTable.createChild())
                    HIME_FUNCTION -> cast<Hime_HimeFunction>(parameters[0].value)(arrayListOf(token))
                    else -> NIL
                }
                assert(op.type == BOOL)
                if (cast<Boolean>(op.value))
                    result.add(token)
            }
            return result.toToken()
        }),
        "sqrt" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.sqrt(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "sin" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.sin(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "sinh" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.sinh(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "asin" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.asin(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "asinh" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.asinh(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "cos" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.cos(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "cosh" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.cosh(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "acos" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.acos(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "acosh" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.acosh(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "tan" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.tan(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "tanh" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.tanh(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "atan" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.atan(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "atanh" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.atanh(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "atan2" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            assert(parameters[1].type == NUM || parameters[1].type == REAL)
            val num = BigDecimalMath.atan2(
                BigDecimal(parameters[0].toString()),
                BigDecimal(parameters[1].toString()),
                MathContext.DECIMAL64
            )
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "log" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.log(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "log10" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.log10(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "log2" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.log2(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "exp" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            val num = BigDecimalMath.exp(BigDecimal(parameters[0].toString()), MathContext.DECIMAL64)
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "pow" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            assert(parameters[1].type == NUM || parameters[1].type == REAL)
            val num = BigDecimalMath.pow(
                BigDecimal(parameters[0].toString()),
                BigDecimal(parameters[1].toString()),
                MathContext.DECIMAL64
            )
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "root" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == NUM || parameters[0].type == REAL)
            assert(parameters[1].type == NUM || parameters[1].type == REAL)
            val num = BigDecimalMath.root(
                BigDecimal(parameters[0].toString()),
                BigDecimal(parameters[1].toString()),
                MathContext.DECIMAL64
            )
            return if (!num.simplification().toPlainString().contains("."))
                BigInteger(num.simplification().toPlainString()).toToken()
            else
                num.toToken()
        }),
        "mod" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == NUM && parameters[1].type == NUM)
            return cast<BigInteger>(parameters[0].value).mod(cast<BigInteger>(parameters[1].value)).toToken()
        }),
        "max" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            var max = BigDecimal(parameters[0].toString())
            for (i in 1 until parameters.size)
                max = max.max(BigDecimal(parameters[i].toString()))
            return max.toToken()
        }),
        "min" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            var min = BigDecimal(parameters[0].toString())
            for (i in 1 until parameters.size)
                min = min.min(BigDecimal(parameters[i].toString()))
            return min.toToken()
        }),
        "average" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            var num = BigDecimal.ZERO
            for (parameter in parameters)
                num = num.add(BigDecimal(parameter.value.toString()))
            return num.divide(parameters.size.toBigDecimal()).toToken()
        }),
        "floor" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return BigInteger(
                BigDecimal(parameters[0].toString()).setScale(0, RoundingMode.FLOOR).toPlainString()
            ).toToken()
        }),
        "ceil" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return BigInteger(
                BigDecimal(parameters[0].toString()).setScale(0, RoundingMode.CEILING).toPlainString()
            ).toToken()
        }),
        "gcd" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            for (parameter in parameters)
                assert(parameter.type == NUM)
            var temp = cast<BigInteger>(parameters[0].value).gcd(cast<BigInteger>(parameters[1].value))
            for (i in 2 until parameters.size)
                temp = temp.gcd(cast<BigInteger>(parameters[i].value))
            return temp.toToken()
        }),
        "lcm" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            fun BigInteger.lcm(n: BigInteger): BigInteger = (this.multiply(n).abs()).divide(this.gcd(n))
            assert(parameters.size > 1)
            for (parameter in parameters)
                assert(parameter.type == NUM)
            var temp = cast<BigInteger>(parameters[0].value).lcm(cast<BigInteger>(parameters[1].value))
            for (i in 2 until parameters.size)
                temp = temp.lcm(cast<BigInteger>(parameters[i].value))
            return temp.toToken()
        }),
        "->string" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return parameters[0].toString().toToken()
        }),
        "->num" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return BigInteger(parameters[0].toString()).toToken()
        }),
        "->real" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return BigDecimal(parameters[0].toString()).toToken()
        }),
        "->bytes" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            val builder = StringBuilder()
            for (token in parameters)
                builder.append(token.toString())
            val list = ArrayList<Token>()
            val bytes = builder.toString().toByteArray()
            for (byte in bytes)
                list.add(byte.toToken())
            return list.toToken()
        }),
        "string-replace" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 2)
            return parameters[0].toString().replace(Regex(parameters[1].toString()), parameters[2].toString()).toToken()
        }),
        "string-substring" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 2)
            assert(parameters[1].type == NUM)
            assert(parameters[2].type == NUM)
            return parameters[0].toString()
                .substring(cast<BigInteger>(parameters[1].value).toInt(), cast<BigInteger>(parameters[2].value).toInt())
                .toToken()
        }),
        "string-split" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            val result = ArrayList<Token>()
            val list = parameters[0].toString().split(parameters[1].toString())
            for (s in list)
                result.add(s.toToken())
            return result.toToken()
        }),
        "string-index" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return parameters[0].toString().indexOf(parameters[1].toString()).toLong().toToken()
        }),
        "string-last-index" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            return parameters[0].toString().lastIndexOf(parameters[1].toString()).toLong().toToken()
        }),
        "string-format" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            val args = arrayOfNulls<Any>(parameters.size - 1)
            for (i in 1 until parameters.size)
                args[i - 1] = parameters[i].value
            return String.format(parameters[0].toString(), *args).toToken()
        }),
        "string->list" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val chars = parameters[0].toString().toCharArray()
            val list = ArrayList<Token>()
            for (c in chars)
                list.add(c.toString().toToken())
            return list.toToken()
        }),
        "list->string" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            val builder = StringBuilder()
            val list = cast<List<Token>>(parameters[0].value)
            for (token in list)
                builder.append(token.value)
            return builder.toString().toToken()
        }),
        "bytes->string" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == LIST)
            val list = cast<List<Token>>(parameters[0].value)
            val bytes = ByteArray(list.size)
            for (index in list.indices)
                bytes[index] = cast<Byte>(list[index].value)
            return String(bytes).toToken()
        }),
        "string?" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters)
                if (parameter.type != STR)
                    return FALSE
            return TRUE
        }),
        "num?" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters)
                if (parameter.type != NUM)
                    return FALSE
            return TRUE
        }),
        "real?" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters)
                if (parameter.type != REAL)
                    return FALSE
            return TRUE
        }),
        "list?" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters)
                if (parameter.type != LIST)
                    return FALSE
            return TRUE
        }),
        "byte?" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters)
                if (parameter.type != BYTE)
                    return FALSE
            return TRUE
        }),
        "function?" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            for (parameter in parameters)
                if (parameter.type != FUNCTION && parameter.type != STATIC_FUNCTION && parameter.type != HIME_FUNCTION)
                    return FALSE
            return TRUE
        }),
        "file-input" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return Files.newInputStream(Paths.get(parameters[0].toString())).toToken()
        }),
        "file-out" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return Files.newOutputStream(Paths.get(parameters[0].toString())).toToken()
        }),
        "file-exists" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return File(parameters[0].toString()).exists().toToken()
        }),
        "file-list" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
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
            assert(parameters.isNotEmpty())
            return listAllFile(File(parameters[0].toString()))
        }),
        "file-mkdirs" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val file = File(parameters[0].toString())
            if (!file.parentFile.exists())
                !file.parentFile.mkdirs()
            if (!file.exists())
                file.createNewFile()
            return NIL
        }),
        "file-new-file" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val file = File(parameters[0].toString())
            if (!file.exists())
                file.createNewFile()
            return NIL
        }),
        "file-read-string" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            return Files.readString(Paths.get(parameters[0].toString())).toToken()
        }),
        "file-write-string" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            val file = File(parameters[0].toString())
            if (!file.parentFile.exists())
                !file.parentFile.mkdirs()
            if (!file.exists())
                file.createNewFile()
            Files.writeString(file.toPath(), parameters[1].toString())
            return NIL
        }),
        "file-read-byte" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val list = ArrayList<Token>()
            val bytes = Files.readAllBytes(Paths.get(parameters[0].toString()))
            for (byte in bytes)
                list.add(byte.toToken())
            return list.toToken()
        }),
        "file-write-byte" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[1].type == LIST)
            val file = File(parameters[0].toString())
            if (!file.parentFile.exists())
                !file.parentFile.mkdirs()
            if (!file.exists())
                file.createNewFile()
            val list = cast<List<Token>>(parameters[1].value)
            val bytes = ByteArray(list.size)
            for (index in list.indices)
                bytes[index] = cast<Byte>(list[index].value)
            Files.write(file.toPath(), bytes)
            return NIL
        }),
        "exit" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            assert(parameters[0].type == NUM)
            exitProcess(cast<BigInteger>(parameters[0].value).toInt())
        }),
        "time" to Token(FUNCTION, fun(_: List<Token>, _: SymbolTable): Token {
            return Date().time.toToken()
        }),
        "time-format" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == STR)
            assert(parameters[1].type == NUM)
            return SimpleDateFormat(cast<String>(parameters[0].value)).format(cast<BigInteger>(parameters[1].value).toLong())
                .toToken()
        }),
        "time-parse" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == STR)
            assert(parameters[1].type == STR)
            return SimpleDateFormat(cast<String>(parameters[0].value)).parse(cast<String>(parameters[1].value)).time.toToken()
        }),
        "extern" to Token(FUNCTION, fun(parameters: List<Token>, symbolTable: SymbolTable): Token {
            assert(parameters.size > 1)
            assert(parameters[0].type == STR)
            assert(parameters[1].type == STR)
            val clazz = parameters[0].toString()
            val name = parameters[1].toString()
            val method = Class.forName(clazz).declaredMethods
                .firstOrNull { Modifier.isStatic(it.modifiers) && it.name == name }
                ?: throw UnsatisfiedLinkError("Method $name not found for class $clazz.")
            symbolTable.put(name, Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
                val args = arrayOfNulls<Any>(parameters.size)
                for (i in parameters.indices)
                    args[i] = parameters[i].value
                return method.invoke(null, *args).toToken()
            }))
            return NIL
        }),
        "eval" to Token(FUNCTION, fun(parameters: List<Token>, symbolTable: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val asts = parser(lexer(format(parameters[0].toString())))
            var result = NIL
            for (ast in asts)
                result = call(ast, symbolTable)
            return result
        })
    ), null
)

val hash = SymbolTable(
    mutableMapOf(
        "sha256" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(parameters[0].toString().toByteArray())
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
        "sha512" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            assert(parameters.isNotEmpty())
            val messageDigest = MessageDigest.getInstance("SHA-512")
            messageDigest.update(parameters[0].toString().toByteArray())
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
        "md5" to Token(FUNCTION, fun(parameters: List<Token>, _: SymbolTable): Token {
            return BigInteger(
                1, MessageDigest.getInstance("MD5")
                    .digest(parameters[0].toString().toByteArray())
            ).toString(16)
                .padStart(32, '0').toToken()
        })
    ), null
)

val module = mutableMapOf("hime.hash" to hash)
