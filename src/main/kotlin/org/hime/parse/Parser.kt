package org.hime.parse

import org.hime.lang.Env
import org.hime.lang.himeAssertParser
import java.util.*

/**
 * 语法分析器
 * @param lexer lexer返回的内容
 * @return      一系列抽象语法树
 */
fun parser(env: Env, lexer: List<List<Token>>): List<ASTNode> {
    val asts: MutableList<ASTNode> = ArrayList()
    val stack = ArrayDeque<ASTNode>()
    var state = -1
    var temp: ASTNode
    for (line in lexer) {
        val tokens = line.toMutableList()
        var index = 0
        while (index < tokens.size) {
            // State 1: 考虑开头
            if (state == 1) {
                // 如果组合式为()
                if (tokens[index] == env.himeRb) {
                    temp = env.himeAstEmpty
                    // 如果peek失败，则会导致运行时错误
                    himeAssertParser(stack.peek() != null) { "peek eq null." }
                    stack.push(temp)
                    state = -1
                    ++index
                    continue
                }
                // 如果运算符为组合式，则使用apply进行替换
                if (tokens[index] == env.himeLb)
                    tokens.add(index, Token(env.getType("id"), "apply"))
                temp = ASTNode(tokens[index])
                stack.push(temp)
                asts.add(temp)
                state = -1
                // State 2: 非开头
            } else if (state == 2) {
                // 如果组合式为()
                if (tokens[index] == env.himeRb) {
                    temp = env.himeAstEmpty
                    // 如果peek失败，则会导致运行时错误
                    himeAssertParser(stack.peek() != null) { "peek eq null." }
                    stack.peek().add(temp)
                    state = -1
                    ++index
                    continue
                }
                // 如果运算符为组合式，则使用apply进行替换
                if (tokens[index] == env.himeLb)
                    tokens.add(index, Token(env.getType("id"), "apply"))
                temp = ASTNode(tokens[index])
                himeAssertParser(stack.peek() != null) { "peek eq null." }
                stack.peek().add(temp)
                stack.push(temp)
                state = -1
            } else if (tokens[index] == env.himeLb)
            // 根据堆栈是否为空切换状态
                state = if (stack.isEmpty()) 1 else 2
            else if (tokens[index] == env.himeRb) {
                // 考虑类似类似(def (<function-name>) <body*>)一类的情况
                if (index >= 2 && tokens[index - 2] == env.himeLb) {
                    himeAssertParser(stack.peek() != null) { "peek eq null." }
                    stack.peek().type = AstType.FUNCTION
                }
                himeAssertParser(stack.isNotEmpty()) { "stack is empty." }
                stack.pop()
            } else {
                himeAssertParser(stack.isNotEmpty()) { "stack is empty." }
                stack.peek().add(ASTNode(tokens[index]))
            }
            ++index
        }
    }
    return asts
}