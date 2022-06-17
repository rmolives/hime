package org.hime.core

import org.hime.parse.Token

class SymbolTable(
    var table: MutableMap<String, Token>,
    private var father: SymbolTable?
) {
    fun remove(key: String) {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        data.table.remove(key)
    }

    fun set(key: String, value: Token) {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        data.table[key] = value
    }

    fun put(key: String, value: Token) {
        table[key] = value
    }

    fun contains(s: String): Boolean {
        var data = this
        while (data.father != null && !data.table.containsKey(s))
            data = data.father!!
        return data.table.containsKey(s)
    }

    fun get(s: String): Token {
        var data = this
        while (data.father != null && !data.table.containsKey(s))
            data = data.father!!
        return data.table[s]!!
    }

    fun createChild(): SymbolTable {
        return SymbolTable(HashMap(), this)
    }
}