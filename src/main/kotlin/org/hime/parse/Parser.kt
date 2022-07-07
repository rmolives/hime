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
            // State 1: the statement is independent.
            if (state == 1) {
                if (tokens[index].type == Type.RB) {
                    // New Tree
                    temp = ASTNode.EMPTY
                    // If push fails, cause a runtime error.
                    assert(stack.peek() != null)
                    stack.push(temp)
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
            // State 2: the return value of statement form the outer one's args, so it is qualified for the AST
            } else if (state == 2) {
                if (tokens[index].type == Type.RB) {
                    // New Tree
                    temp = ASTNode.EMPTY
                    // If push fails, cause a runtime error.
                    assert(stack.peek() != null)
                    // Be enrolled in the AST.
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
            // Why is it that considering whether stack is empty is critical?
                state = if (stack.isEmpty()) 1 else 2
            else if (tokens[index].type == Type.RB) {
                // Functions
                if (index >= 2 && tokens[index - 2].type == Type.LB) {
                    assert(stack.peek() != null)
                    stack.peek().type = AstType.FUNCTION
                }
                // Pop the whole AST.
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