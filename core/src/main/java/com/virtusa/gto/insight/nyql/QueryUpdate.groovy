package com.virtusa.gto.insight.nyql

import com.virtusa.gto.insight.nyql.utils.QReturnType

/**
 * @author Isuru Weerarathna
 */
class QueryUpdate extends Query {

    Table _joiningTable = null
    Assign _assigns = null

    QueryUpdate(QContext contextParam) {
        super(contextParam)
    }

    def TARGET() {
        return sourceTbl
    }

    def TARGET(Table table) {
        sourceTbl = table
        return this
    }

    def JOIN(Table startTable, closure) {
        JoinClosure joinClosure = new JoinClosure(_ctx, startTable)

        def code = closure.rehydrate(joinClosure, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        _joiningTable = joinClosure.activeTable
        return this
    }

    def SET(closure) {
        Assign ass = new Assign(_ctx, this)

        def code = closure.rehydrate(ass, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        _assigns = ass
        return this
    }

    def RETURN_KEYS() {
        returnType = QReturnType.KEYS
        return this
    }

}
