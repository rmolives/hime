package org.hime.parse

import java.util.*

/**
 *     抽象语法树
 *      (tok)
 *      /   \
 *  (child)(child)
 */
class AstNode {
    var tok: Token                      // 运算符
    var type = AstType.BASIC            // 抽象语法树类型
    var children: MutableList<AstNode>     // 运算对象

    /**
     * 建立新的抽象语法树
     * @param tok 运算符
     */
    constructor(tok: Token) {
        this.tok = tok
        children = LinkedList()
    }

    /**
     * 建立新的抽象语法树
     * @param tok   运算符
     * @param child 运算对象
     */
    constructor(tok: Token, child: MutableList<AstNode>) {
        this.tok = tok
        this.children = child
    }

    /**
     * 建立新的抽象语法树
     * @param tok  运算符
     * @param type 类型
     */
    constructor(tok: Token, type: AstType) {
        this.tok = tok
        this.type = type
        this.children = LinkedList()
    }

    /**
     * 复制抽象语法树
     * @return 复制的语法树
     */
    fun copy(): AstNode {
        val list: MutableList<AstNode> = LinkedList()
        for (ast in children)
            list.add(ast.copy())
        val newAst = AstNode(tok, list)
        newAst.type = type
        return newAst
    }

    /**
     * 添加新的运算对象
     * @param node 运算的抽象语法树
     */
    fun add(node: AstNode) {
        children.add(node)
    }

    /**
     * 获取第i个运算对象
     * @param i index
     * @return 运算对象
     */
    operator fun get(i: Int): AstNode {
        return children[i]
    }

    /**
     * 清空运算对象
     */
    fun clear() {
        children.clear()
    }

    /**
     * 获取运算对象的数量
     */
    fun size(): Int {
        return children.size
    }

    /**
     * 判断是否存在运算对象
     * @return 是否为空
     */
    fun isEmpty(): Boolean {
        return size() == 0
    }

    /**
     * 判断是否存在运算对象
     * @return 是否不为空
     */
    fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    /**
     * 覆盖toString
     * @return 抽象语法树的String
     */
    override fun toString(): String {
        val builder = StringBuilder()
        if (isEmpty())
            builder.append(tok.toString())
        else {
            builder.append("(")
            builder.append(tok.toString())
            for (ast in children)
                builder.append(" ").append(ast.toString())
            builder.append(")")
        }
        return builder.toString()
    }
}

enum class AstType {
    FUNCTION, BASIC
}