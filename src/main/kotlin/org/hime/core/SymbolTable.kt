package org.hime.core

import org.hime.parse.Token

/**
 * @param table  一系列绑定
 * @param father 所属的父SymbolTable
 */
class SymbolTable(
    var table: MutableMap<String, Token>,
    var father: SymbolTable?
) {
    /**
     * 删除绑定
     * @param key binding key
     */
    fun remove(key: String) {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        data.table.remove(key)
    }

    /**
     * 更改绑定
     * @param key   binding key
     * @param value binding value
     */
    fun set(key: String, value: Token) {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        data.table[key] = value
    }

    /**
     * 建立绑定
     * @param key   binding key
     * @param value binding value
     */
    fun put(key: String, value: Token) {
        table[key] = value
    }

    /**
     * 判断是否包含key
     * @param key binding key
     */
    fun contains(key: String): Boolean {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        return data.table.containsKey(key)
    }

    /**
     * 获取key对应的值
     * @param key binding key
     * @return    binding value
     */
    fun get(key: String): Token {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        return data.table[key]!!
    }

    /**
     * 建立子SymbolTable
     * @return child
     */
    fun createChild(): SymbolTable {
        return SymbolTable(HashMap(), this)
    }
}