package org.hime.parse

import java.util.*

fun parser(lexer: List<List<Token>>): List<ASTNode> {
    val asts: MutableList<ASTNode> = ArrayList()
    val stack = ArrayDeque<ASTNode>()
    var state = -1
    var temp: ASTNode
    for (line in lexer) {
        val tokens = line.toMutableList()
        var index = 0
        while (index < tokens.size) {
            if (state == 1) {
                if (tokens[index].type == Type.RB) {
                    temp = ASTNode.EMPTY
                    assert(stack.peek() != null)
                    stack.peek().add(temp)
                    state = -1
                    ++index
                    continue
                }
                if (tokens[index].type == Type.LB || tokens[index].type == Type.ID) {
                    if (tokens[index].type == Type.LB)
                        tokens.add(index, Token(Type.ID,  "apply"))
                    temp = ASTNode(tokens[index])
                    stack.push(temp)
                    asts.add(temp)
                    state = -1
                }
            } else if (state == 2) {
                if (tokens[index].type == Type.RB) {
                    temp = ASTNode.EMPTY
                    assert(stack.peek() != null)
                    stack.peek().add(temp)
                    state = -1
                    ++index
                    continue
                }
                if (tokens[index].type == Type.LB || tokens[index].type == Type.ID) {
                    if (tokens[index].type == Type.LB)
                        tokens.add(index, Token(Type.ID,  "apply"))
                    temp = ASTNode(tokens[index])
                    assert(stack.peek() != null)
                    stack.peek().add(temp)
                    stack.push(temp)
                    state = -1
                }
            } else if (tokens[index].type == Type.LB)
                state = if (stack.isEmpty()) 1 else 2
            else if (tokens[index].type == Type.RB) {
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