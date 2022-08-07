package org.hime.parse

import org.hime.lang.Env
import org.hime.lang.HimeTypeId
import org.hime.toToken
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/**
 * 分割表达式
 * @param code  代码
 * @return      expressions
 */
fun splitCode(code: String): List<String> {
    val expressions = ArrayList<String>()
    var index = 0
    while (index < code.length) {
        var flag = 0
        val builder = StringBuilder()
        // 跳过前面的空白字符
        while (code[index] != '(')
            ++index
        // 分割代码，例如(+ 2 3)(+ 4 5)分成(+ 2 3)和(+ 4 5)
        do {
            if (code[index] == '\"') {
                builder.append("\"")
                val value = StringBuilder()
                var skip = false
                while (true) {
                    ++index
                    // 考虑边界条件
                    if (index < code.length - 1 && code[index] == '\\') {
                        if (skip) {
                            skip = false
                            value.append("\\\\")
                            // 当遇到反斜杠时，我们将跳过以下引用
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
            // 一旦进入一对括号，flag将递增
            if (code[index] == '(')
                ++flag
            // 一旦从一对括号中取出，flag将递减
            else if (code[index] == ')')
                --flag
            builder.append(code[index++])
        } while (flag > 0)
        expressions.add(builder.toString())
    }
    return expressions
}

/**
 * 词法分析器
 * @param env           环境
 * @param expression    代码
 * @return              tokens
 */
fun lexer(env: Env, expression: String): MutableList<Token> {
    // 返回的内容
    val tokens = ArrayList<Token>()
    // 考虑边界条件
    var index = -1
    // 开始扫描...
    while (++index < expression.length) {
        when (expression[index]) {
            '(' -> {
                tokens.add(env.himeLb)
                continue
            }

            ')' -> {
                tokens.add(env.himeRb)
                continue
            }
            // 跳过空格
            ' ' -> continue
        }
        var negative = false
        // 处理负数
        if (expression[index] == '-' && index < expression.length - 1 && Character.isDigit(expression[index + 1])) {
            negative = true
            ++index
        }
        // 处理小数.
        if (expression[index].isDigit()) {
            var v = BigInteger.ZERO
            while (true) {
                if (index >= expression.length - 1 || !expression[index].isDigit()) {
                    --index
                    break
                }
                v = v.multiply(BigInteger.valueOf(10))
                    .add(BigInteger.valueOf((expression[index].digitToIntOrNull() ?: -1).toLong()))
                ++index
            }
            // 处理浮点数
            if (expression[index + 1] != '.') {
                tokens.add((if (negative) v.subtract(v.multiply(BigInteger.TWO)) else v).toToken(env))
                continue
            }
            var x = BigDecimal(v.toString())
            var d = BigDecimal.valueOf(10)
            ++index
            while (true) {
                ++index
                if (index >= expression.length - 1 || !expression[index].isDigit()) {
                    --index
                    break
                }
                x = x.add(
                    BigDecimal.valueOf((expression[index].digitToIntOrNull() ?: -1).toLong())
                        .divide(d, MathContext.DECIMAL64)
                )
                d = d.multiply(BigDecimal.valueOf(10))
            }
            tokens.add((if (negative) x.subtract(x.multiply(BigDecimal.valueOf(2))) else x).toToken(env))
            continue
        }
        // 处理字符串
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
                    // 替换特殊值
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
            tokens.add(builder.toString().toToken(env))
            continue
        }
        // 处理其他字符
        if (expression[index] != ' ' && expression[index] != '(' && expression[index] != ')') {
            val builder = StringBuilder()
            // 其他字符通常形成一个ID
            while (true) {
                if (index >= expression.length - 1 || expression[index] == ' ' || expression[index] == ')') {
                    --index
                    break
                }
                builder.append(expression[index])
                ++index
            }
            val s = builder.toString()
            if (s.contains(":")) {
                val inOf = s.indexOf(":")
                tokens.add(Token(HimeTypeId(env, env.getType(s.substring(inOf + 1))), s.substring(0, inOf)))
            } else
                tokens.add(Token(HimeTypeId(env), builder.toString()))
            continue
        }
    }
    return tokens
}
