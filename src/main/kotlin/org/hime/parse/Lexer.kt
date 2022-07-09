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
    // Store tokens from every single expression.
    val result = ArrayList<List<Token>>()
    // Store every single expression.
    val expressions = ArrayList<String>()
    var index = 0
    // Split the whole code into multiple expressions.
    while (index < code.length) {
        var flag = 0
        val builder = StringBuilder()
        // Skip preceding blank characters.
        while (code[index] != '(')
            ++index
        do {
            // Processing literal strings.
            if (code[index] == '\"') {
                builder.append("\"")
                val value = StringBuilder()
                var skip = false
                while (true) {
                    ++index
                    // Consider border conditions.
                    if (index < code.length - 1 && code[index] == '\\') {
                        if (skip) {
                            skip = false
                            value.append("\\\\")
                        // When encountering a backslash, we shall skip the following quote.
                        } else
                            skip = true
                        continue
                    } else if (index >= code.length - 1 || code[index] == '\"') {
                        if (skip) {
                            skip = false
                            value.append("\\\"")
                            continue
                        } else
                            break
                    } else if (skip) {
                        value.append("\\${code[index]}")
                        skip = false
                        continue
                    }
                    value.append(code[index])
                }
                builder.append(value.append("\"").toString())
                ++index
                continue
            }
            // Once getting into a pair of parentheses, flag will increment.
            if (code[index] == '(')
                ++flag
            // Once getting out of a pair of parentheses, flag will decrement.
            else if (code[index] == ')')
                --flag
            builder.append(code[index++])
        } while (flag > 0)
        expressions.add(builder.toString())
    }
    // Analyse any one of the expressions.
    for (expressionIndex in expressions.indices) {
        // Store the tokens from current expression.
        val tokens = ArrayList<Token>()
        result.add(tokens)
        val expression = expressions[expressionIndex]
        // Perfectly consider the border conditions.
        index = -1
        // Commence scanning...
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
                // Skip blanks
                ' ' -> continue
            }
            var negative = false
            // Processing negative literal numbers.
            if (expression[index] == '-' && index < expression.length - 1 && Character.isDigit(expression[index + 1])) {
                negative = true
                ++index
            }
            // Processing decimals.
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
                // Process floating-point numbers.
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
            // Process string
            if (expression[index] == '\"') {
                val builder = StringBuilder()
                var skip = false
                while (true) {
                    ++index
                    if (index < expression.length - 1 && expression[index] == '\\') {
                        if (skip) {
                            skip = false
                            builder.append("\\")
                        } else
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
                    if (skip) {
                        skip = false
                        builder.append(
                            when (expression[index]) {
                                'n' -> "\n"
                                'r' -> "\r"
                                't' -> "\t"
                                'b' -> "\b"
                                else -> expression[index]
                            }
                        )
                    } else
                        builder.append(expression[index])
                }
                tokens.add(builder.toString().toToken())
                continue
            }
            // Process other characters
            if (expression[index] != ' ' && expression[index] != '(' && expression[index] != ')') {
                val builder = StringBuilder()
                // Other characters often form a single ID.
                while (true) {
                    if (index >= expression.length - 1 || expression[index] == ' ' || expression[index] == ')') {
                        --index
                        break
                    }
                    builder.append(expression[index])
                    ++index
                }
                // Get the ID.
                val s = builder.toString()
                // If the ID is true, false or nil, add the corresponding tokens(consider null condition), else add it as normal ID.
                tokens.add(if (ENV.containsKey(s)) ENV[s]!! else Token(Type.ID, builder.toString()))
                continue
            }
        }
    }
    return result
}
