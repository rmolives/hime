package org.hime.parse

import org.hime.FLOAT_MAX
import org.hime.INT_MAX
import org.hime.toToken
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

val ENV: Map<String, Token> = mapOf(
    "true" to TRUE,
    "false" to FALSE,
    "nil" to NIL
)

fun lexer(code: String): List<List<Token>> {
    val result = ArrayList<List<Token>>()
    val expressions = ArrayList<String>()
    var index = 0
    while (index < code.length) {
        var flag = 0
        val builder = StringBuilder()
        while (code[index] != '(')
            ++index
        do {
            if (code[index] == '\"') {
                builder.append("\"")
                val value = StringBuilder()
                var skip = false
                while (true) {
                    ++index
                    if (index < code.length - 1 && (code[index + 1] == '\\' || code[index + 1] == '\"') && code[index] == '\\') {
                        if (skip) {
                            skip = false
                            value.append("\\")
                        } else
                            if (index < code.length - 1 && code[index + 1] != 'n' && code[index + 1] != 't')
                                value.append("\\")
                            else
                                skip = true
                        continue
                    } else if (index >= code.length - 1 || code[index] == '\"') {
                        if (skip) {
                            skip = false
                            value.append("\"")
                            continue
                        } else
                            break
                    }
                    value.append(code[index])
                }
                builder.append(value.append("\"").toString())
                ++index
                continue
            }
            if (code[index] == '(')
                ++flag
            else if (code[index] == ')')
                --flag
            builder.append(code[index++])
        } while (flag > 0)
        expressions.add(builder.toString())
    }
    for (expressionIndex in expressions.indices) {
        val tokens = ArrayList<Token>()
        result.add(tokens)
        val expression = expressions[expressionIndex]
        index = -1
        while (++index < expression.length) {
            when (expression[index]) {
                '(' -> {
                    tokens.add(LB)
                    continue
                }
                ')' -> {
                    tokens.add(RB)
                    continue
                }
                ' ' -> continue
            }
            var negative = false
            if (expression[index] == '-' && index < expression.length - 1 && Character.isDigit(expression[index + 1])) {
                negative = true
                ++index
            }
            if (Character.isDigit(expression[index])) {
                var v = BigInteger.ZERO
                while (true) {
                    if (index >= expression.length - 1 || !Character.isDigit(expression[index])) {
                        --index
                        break
                    }
                    v = v.multiply(BigInteger.valueOf(10))
                        .add(BigInteger.valueOf((expression[index].digitToIntOrNull() ?: -1).toLong()))
                    ++index
                }
                if (expression[index + 1] != '.') {
                    tokens.add(
                        if (v <= INT_MAX) {
                            val n = v.toInt()
                            Token(Type.NUM, if (negative) -n else n)
                        } else
                            Token(Type.BIG_NUM, if (negative) v.subtract(v.multiply(BigInteger.TWO)) else v)
                    )
                    continue
                }
                var x = BigDecimal(v.toString())
                var d = BigDecimal.valueOf(10)
                ++index
                while (true) {
                    ++index
                    if (index >= expression.length - 1 || !Character.isDigit(expression[index])) {
                        --index
                        break
                    }
                    x = x.add(
                        BigDecimal.valueOf((expression[index].digitToIntOrNull() ?: -1).toLong())
                            .divide(d, MathContext.DECIMAL64)
                    )
                    d = d.multiply(BigDecimal.valueOf(10))
                }
                tokens.add(
                    if (x <= FLOAT_MAX) {
                        val n = x.toFloat()
                        Token(Type.REAL, if (negative) -n else n)
                    } else
                        Token(Type.BIG_REAL, if (negative) x.subtract(x.multiply(BigDecimal.valueOf(2))) else x)
                )
                continue
            }
            if (expression[index] == '\"') {
                val builder = StringBuilder()
                var skip = false
                while (true) {
                    ++index
                    if (index < expression.length - 1
                        && (expression[index + 1] == '\\' || expression[index + 1] == '\"')
                        && expression[index] == '\\'
                    ) {
                        if (skip) {
                            skip = false
                            builder.append("\\")
                        } else
                            if (index < expression.length - 1 && expression[index + 1] != 'n' && expression[index + 1] != 't')
                                builder.append("\\")
                            else
                                skip = true
                        continue
                    } else if (index >= expression.length - 1 || expression[index] == '\"') {
                        if (skip) {
                            skip = false
                            builder.append("\"")
                            continue
                        } else
                            break
                    }
                    builder.append(expression[index])
                }
                tokens.add(builder.toString().toToken())
                continue
            }
            if (expression[index] != ' ' && expression[index] != '(' && expression[index] != ')') {
                val builder = StringBuilder()
                while (true) {
                    if (index >= expression.length - 1 || expression[index] == ' ' || expression[index] == ')') {
                        --index
                        break
                    }
                    builder.append(expression[index])
                    ++index
                }
                val s = builder.toString()
                tokens.add(if (ENV.containsKey(s)) ENV[s]!! else Token(Type.ID, builder.toString()))
                continue
            }
        }
    }
    return result
}
