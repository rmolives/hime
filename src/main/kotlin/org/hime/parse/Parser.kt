package org.hime.parse

import java.util.*

/**
 * 语法分析器
 * @param lexer lexer返回的内容
 * @return      一系列抽象语法树
 */
fun parser(lexer: List<List<Token>>): List<ASTNode> {
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
                if (tokens[index].type == Type.RB) {
                    temp = ASTNode.EMPTY
                    // 如果peek失败，则会导致运行时错误
                    assert(stack.peek() != null)
                    stack.push(temp)
                    state = -1
                    ++index
                    continue
                }
                if (tokens[index].type == Type.LB || tokens[index].type == Type.ID) {
                    // 如果运算符为组合式，则使用apply进行替换
                    if (tokens[index].type == Type.LB)
                        tokens.add(index, Token(Type.ID,  "apply"))
                    temp = ASTNode(tokens[index])
                    stack.push(temp)
                    asts.add(temp)
                    state = -1
                }
                // State 2: 非开头
            } else if (state == 2) {
                // 如果组合式为()
                if (tokens[index].type == Type.RB) {
                    temp = ASTNode.EMPTY
                    // 如果peek失败，则会导致运行时错误
                    assert(stack.peek() != null)
                    stack.peek().add(temp)
                    state = -1
                    ++index
                    continue
                }
                if (tokens[index].type == Type.LB || tokens[index].type == Type.ID) {
                    // 如果运算符为组合式，则使用apply进行替换
                    if (tokens[index].type == Type.LB)
                        tokens.add(index, Token(Type.ID,  "apply"))
                    temp = ASTNode(tokens[index])
                    assert(stack.peek() != null)
                    stack.peek().add(temp)
                    stack.push(temp)
                    state = -1
                }
            } else if (tokens[index].type == Type.LB)
            // 根据堆栈是否为空切换状态
                state = if (stack.isEmpty()) 1 else 2
            else if (tokens[index].type == Type.RB) {
                // 考虑类似类似(def (<function-name>) <body*>)一类的情况
                if (index >= 2 && tokens[index - 2].type == Type.LB) {
                    assert(stack.peek() != null)
                    stack.peek().type = AstType.FUNCTION
                }
                assert(stack.isNotEmpty())
                stack.pop()
            } else {
                assert(stack.isNotEmpty())
                stack.peek().add(ASTNode(tokens[index]))
            }
            ++index
        }
    }
    return asts
}