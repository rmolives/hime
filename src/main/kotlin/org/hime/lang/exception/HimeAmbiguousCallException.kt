package org.hime.lang.exception

import org.hime.lang.Env
import org.hime.lang.HimeFunction
import org.hime.parse.Token

class HimeAmbiguousCallException(val env: Env, val candidates: List<HimeFunction>, val args: List<Token>):
    HimeRuntimeException("Ambiguous call.") {
    fun formatted(funcName: String): HimeRuntimeException {
        val argSeparator = " "
        val tab = "    "
        val variadicParams = "..."

        var res = "Ambiguous call on overloaded function '($funcName$argSeparator"
        for (arg in args) {
            res += arg.type.toString()
            res += argSeparator
        }
        res = res.removeSuffix(argSeparator)
        res += ")'.\n"

        res += "candidates:\n"
        for (candidate in candidates) {
            res += "$tab($funcName$argSeparator"
            for (type in candidate.paramTypes) {
                res += "$type$argSeparator"
            }
            if (candidate.variadic) {
                res += "${candidate.varType}$variadicParams"
            }
            res = res.removeSuffix(argSeparator)
            res += ")\n"
        }
        res = res.removeSuffix("\n")
        return HimeRuntimeException(res)
    }
}