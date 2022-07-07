package org.hime.parse

import java.util.LinkedList

class ASTNode {
    var tok: Token
    var type = AstType.BASIC
    var child: MutableList<ASTNode>

    constructor(tok: Token) {
        this.tok = tok
        child = LinkedList()
    }

    constructor(tok: Token, prev: MutableList<ASTNode>) {
        this.tok = tok
        this.child = prev
    }

    fun copy(): ASTNode {
        val list: MutableList<ASTNode> = LinkedList()
        for (ast in child)
            list.add(ast.copy())
        val newAst = ASTNode(tok, list)
        newAst.type = type
        return newAst
    }

    fun add(node: ASTNode) {
        child.add(node)
    }

    operator fun get(i: Int): ASTNode {
        return child[i]
    }

    fun clear() {
        child.clear()
    }

    fun size(): Int {
        return child.size
    }

    fun isEmpty(): Boolean {
        return size() == 0
    }

    fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        if (isEmpty())
            builder.append(tok.toString())
        else {
            builder.append("(")
            builder.append(tok.toString())
            for (ast in child)
                builder.append(" ").append(ast.toString())
            builder.append(")")
        }
        return builder.toString()
    }

    // It resembles static variable.
    companion object {
        val EMPTY = ASTNode(Token(Type.EMPTY, "empty"))
    }
}

enum class AstType {
    FUNCTION, BASIC
}