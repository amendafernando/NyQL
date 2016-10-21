package com.virtusa.gto.insight.nyql

import com.virtusa.gto.insight.nyql.exceptions.NySyntaxException
import com.virtusa.gto.insight.nyql.model.QScript
import com.virtusa.gto.insight.nyql.model.units.AParam
import com.virtusa.gto.insight.nyql.model.units.ParamList
import com.virtusa.gto.insight.nyql.traits.DataTypeTraits
import com.virtusa.gto.insight.nyql.traits.FunctionTraits
import com.virtusa.gto.insight.nyql.traits.ScriptTraits
import com.virtusa.gto.insight.nyql.utils.Constants
import com.virtusa.gto.insight.nyql.utils.QUtils
import com.virtusa.gto.insight.nyql.utils.QueryType

/**
 * @author IWEERARATHNA
 */
abstract class AbstractClause implements FunctionTraits, DataTypeTraits, ScriptTraits {

    QContext _ctx

    protected AbstractClause(QContext contextParam) {
        _ctx = contextParam
        if (this instanceof Query) {
            _ctx.ownQuery = this
        }
    }

    def $IMPORT(String scriptId) {
        QScript script = _ctx.ownerSession.scriptRepo.parse(scriptId, _ctx.ownerSession)
        def proxy = script.proxy
        if (proxy.queryType == QueryType.PART) {
            Query q = proxy.qObject as Query
            _ctx.mergeFrom(q._ctx)
            return proxy.rawObject
        } else {
            return script.proxy
        }
        //throw new NySyntaxException("You can only import a query part having a Table reference!")
    }

    AParam PARAM(String name, AParam.ParamScope scope=null, String mappingName=null) {
        return _ctx.addParam(QUtils.createParam(name, scope, mappingName))
    }

    AParam PARAMLIST(String name) {
        return _ctx.addParam(new ParamList(__name: name))
    }

    def TABLE(String tblName) {
        if (_ctx.tables.containsKey(tblName)) {
            return _ctx.tables.get(tblName)
        } else {
            Table table = new Table(__name: tblName, _ctx: _ctx)
            _ctx.tables.put(tblName, table)
            return table
        }
    }

    def OTHER() {
        new TableProxy()
    }

    def TABLE(QResultProxy resultProxy) {
        Table table = new Table(__name: String.valueOf(System.currentTimeMillis()), _ctx: _ctx, __resultOf: resultProxy)
        _ctx.tables.putIfAbsent(table.__name, table)
        return table
    }

    def TABLE(QScript qScript) {
        return TABLE(qScript.proxy)
    }

    def COLUMN(String colName) {
        if (_ctx.columns.containsKey(colName)) {
            return _ctx.columns.get(colName)
        } else {
            def column = _ctx.getTheOnlyTable()?.COLUMN(colName)
            if (column != null) {
                return column
            }

            throw new NySyntaxException(QUtils.generateErrStr(
                    "No column is found by name '$colName'!",
                    'If you are specifying column name directly then make sure it has an alias!'
            ))
        }
    }

    def CASE(@DelegatesTo(value = Case, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        Case aCase = new Case(_ctx: _ctx, _ownerQ: this)

        def code = closure.rehydrate(aCase, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return aCase
    }

    def IFNULL(Column column, Object val) {
        Case aCase = CASE {
            WHEN { ISNULL(column) }
            THEN { val }
            ELSE { column }
        }
        aCase.setCaseType(Case.CaseType.IFNULL)
        return aCase
    }

    def IFNOTNULL(Column column, Object val) {
        return CASE {
            WHEN { NOTNULL(column) }
            THEN { val }
            ELSE { column }
        }
    }

    def EXISTS(QResultProxy innerQuery) {
        return new FunctionColumn(_ctx: _ctx, _setOfCols: true, _func: 'exists', _columns: [innerQuery])
    }

    def propertyMissing(String name) {
        if (name == Constants.DSL_SESSION_WORD) {
            return _ctx.ownerSession.sessionVariables
        }

        Column col = _ctx.getColumnIfExist(name)
        if (col != null) {
            return col
        }

        if (_ctx.tables.containsKey(name)) {
            return _ctx.tables[name]
        } else if (Character.isUpperCase(name.charAt(0))) {
            // table name
            Table table = new Table(__name: name, _ctx: _ctx)
            _ctx.tables.put(name, table)
            return table
        } else {
            def column = _ctx.getTheOnlyTable()?.COLUMN(name)
            if (column != null) {
                return column
            }
            throw new NySyntaxException(QUtils.generateErrStr(
                    "No table by name '$name' found!",
                    'You cannot refer to a column without mentioning its table or alias.',
                    'Or, did you misspelled the table name?'
            ))
        }
    }

    def methodMissing(String name, def args) {
        if (name == '$IMPORT') {
            return this.invokeMethod(name, args)
        } else if (_ctx.tables.containsKey(name)) {
            return _ctx.tables[name]
        }

        try {
            return _ctx.translator.invokeMethod(name, args)
        } catch (Exception ignored) {
            throw new NySyntaxException("Unsupported function for $_ctx.translatorName is found! (Function: '$name')")
        }
    }
}
