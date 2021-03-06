package com.virtusa.gto.nyql.db;

import com.virtusa.gto.nyql.Assign;
import com.virtusa.gto.nyql.CTE;
import com.virtusa.gto.nyql.Column;
import com.virtusa.gto.nyql.FunctionColumn;
import com.virtusa.gto.nyql.Join;
import com.virtusa.gto.nyql.QContextType;
import com.virtusa.gto.nyql.QResultProxy;
import com.virtusa.gto.nyql.Query;
import com.virtusa.gto.nyql.QueryInsert;
import com.virtusa.gto.nyql.QueryPart;
import com.virtusa.gto.nyql.QuerySelect;
import com.virtusa.gto.nyql.QueryTruncate;
import com.virtusa.gto.nyql.Table;
import com.virtusa.gto.nyql.TableAll;
import com.virtusa.gto.nyql.Where;
import com.virtusa.gto.nyql.WithClosure;
import com.virtusa.gto.nyql.exceptions.NyException;
import com.virtusa.gto.nyql.model.JoinType;
import com.virtusa.gto.nyql.model.ValueTable;
import com.virtusa.gto.nyql.model.DbInfo;
import com.virtusa.gto.nyql.model.units.AParam;
import com.virtusa.gto.nyql.utils.QOperator;
import com.virtusa.gto.nyql.utils.QUtils;
import com.virtusa.gto.nyql.utils.QueryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author IWEERARATHNA
 */
public abstract class AbstractSQLTranslator implements QTranslator {

    private static final String EMPTY = "";

    private final TranslatorOptions translatorOptions;
    private final Collection<String> keywords;

    private static final String NL = "\n";
    private static final String _AS_ = " AS ";
    private static final String COMMA = ", ";

    protected AbstractSQLTranslator() {
        translatorOptions = TranslatorOptions.empty();
        keywords = translatorOptions.getKeywords();
    }

    protected AbstractSQLTranslator(TranslatorOptions theOptions) {
        if (theOptions != null) {
            translatorOptions = theOptions;
        } else {
            translatorOptions = TranslatorOptions.empty();
        }
        keywords = translatorOptions.getKeywords();
    }

    protected TranslatorOptions getTranslatorOptions() {
        return translatorOptions;
    }

    protected boolean isUnresolvedVersion(DbInfo dbInfo) {
        return dbInfo == null || dbInfo == DbInfo.UNRESOLVED;
    }

    protected abstract String getQuoteChar();

    protected String convertToAlias(String alias, String qChar) {
        return (keywords.contains(alias.toUpperCase(Locale.getDefault()))
                ? QUtils.quote(alias, qChar)
                : QUtils.quoteIfWS(alias, qChar));
    }

    protected String tableSchema(Table table, String qChar) {
        if (table.get__schema() != null) {
            return QUtils.quote(table.get__schema(), qChar) + ".";
        } else {
            return EMPTY;
        }
    }

    protected String tableAlias(Table table, String qChar) {
        if (table.__aliasDefined()) {
            return convertToAlias(table.get__alias(), qChar);
        } else {
            return EMPTY;
        }
    }

    protected String tableAliasAs(Table table, String qChar) {
        if (table.__aliasDefined()) {
            return _AS_ + convertToAlias(table.get__alias(), qChar);
        } else {
            return EMPTY;
        }
    }

    protected String columnAlias(Column column, String qChar) {
        if (column.__aliasDefined()) {
            return convertToAlias(column.get__alias(), qChar);
        } else {
            return EMPTY;
        }
    }

    protected String columnAliasAs(Column column, String qChar) {
        if (column.__aliasDefined()) {
            return _AS_ + convertToAlias(column.get__alias(), qChar);
        } else {
            return EMPTY;
        }
    }

    /**
     * Returns correct table name for the given table name, if there is a name mapping for it.
     *
     * Usually this method will be used by Oracle implementation.
     *
     * @param tblName table name.
     * @return mapping table name.
     */
    protected final String tableName(String tblName) {
        return getTranslatorOptions().tableMapName(tblName);
    }

    /**
     * Returns correct column name of the table, if there is a name mapping for it.
     *
     * @param tblName table name.
     * @param colName column name.
     * @return mapping column name.
     */
    protected final String columnName(String tblName, String colName) {
        return getTranslatorOptions().columnMapName(tblName, colName);
    }

    protected String generateTableJoinName(final Join join, final String joinType, final QContextType contextType, List<AParam> paramOrder) {
        StringBuilder qstr = new StringBuilder();

        if (join.getTable1().__isResultOf()) {
            QResultProxy proxy = (QResultProxy) join.getTable1().get__resultOf();
            addAllSafely(paramOrder, proxy.getOrderedParameters());
        }
        qstr.append(___resolve(join.getTable1(), contextType, paramOrder));
        qstr.append(" ").append(joinType).append(" ");

        if (join.getTable2().__isResultOf()) {
            QResultProxy proxy = (QResultProxy) join.getTable2().get__resultOf();
            addAllSafely(paramOrder, proxy.getOrderedParameters());
        }
        qstr.append(___resolve(join.getTable2(), contextType, paramOrder));

        if (join.___hasCondition()) {
            qstr.append(" ON ").append(___expandConditions(join.getOnConditions(), paramOrder, QUtils.findDeleteContext(contextType)));
        }
        return qstr.toString();
    }

    protected QResultProxy generateInsertQuery(QueryInsert q, String quoteChar) throws NyException {
        if (QUtils.isNullOrEmpty(q.get_data()) && q.get_assigns() == null) {
            return ___selectQuery(q);
        }

        List<AParam> paramList = new LinkedList<>();
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(___resolve(q.getSourceTbl(), QContextType.INTO, paramList)).append(" (");
        List<String> colList = new LinkedList<>();
        List<String> valList = new LinkedList<>();

        if (q.get_data() != null) {
            for (Map.Entry<String, Object> entry : q.get_data().entrySet()) {
                colList.add(QUtils.quote(entry.getKey(), quoteChar));

                ___scanForParameters(entry.getValue(), paramList);
                valList.add(String.valueOf(___resolve(entry.getValue(), QContextType.INSERT_DATA, paramList)));
            }
        }

        if (q.get_assigns() != null && q.get_assigns().__hasAssignments()) {
            for (Object object : q.get_assigns().getAssignments()) {
                if (object instanceof Assign.AnAssign) {
                    Assign.AnAssign anAssign = (Assign.AnAssign)object;

                    if (anAssign.getLeftOp() instanceof Column) {
                        colList.add(String.valueOf(___resolve(anAssign.getLeftOp(), QContextType.INSERT_PROJECTION, paramList)));
                    } else {
                        colList.add(QUtils.quote(anAssign.toString(), quoteChar));
                    }
                    ___scanForParameters(anAssign.getRightOp(), paramList);
                    valList.add(String.valueOf(___resolve(anAssign.getRightOp(), QContextType.INSERT_DATA, paramList)));
                }
            }
        }

        query.append(colList.stream().collect(Collectors.joining(COMMA)))
                .append(") VALUES (")
                .append(valList.stream().collect(Collectors.joining(COMMA)))
                .append(")");

        QResultProxy resultProxy = createProxy(query.toString(), QueryType.INSERT, paramList, null, null);
        resultProxy.setReturnType(q.getReturnType());
        return resultProxy;
    }

    protected StringBuilder generateSelectQueryBody(QuerySelect q, List<AParam> paramList) throws NyException {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        if (q.is_distinct()) {
            query.append("DISTINCT ");
        }
        query.append(___expandProjection(q.getProjection(), paramList, QContextType.SELECT)).append(NL);

        ___selectQueryAfterFetchClause(q, query, paramList);

        // target is optional
        if (q.get_joiningTable() != null) {
            query.append(" FROM ").append(___deriveSource(q.get_joiningTable(), paramList, QContextType.FROM)).append(NL);
        } else if (q.getSourceTbl() != null) {
            query.append(" FROM ").append(___deriveSource(q.getSourceTbl(), paramList, QContextType.FROM)).append(NL);
        }

        if (q.getWhereObj() != null && q.getWhereObj().__hasClauses()) {
            query.append(" WHERE ").append(___expandConditions(q.getWhereObj(), paramList, QContextType.CONDITIONAL)).append(NL);
        }

        if (QUtils.notNullNorEmpty(q.getGroupBy())) {
            ___selectQueryGroupByClause(q, query, paramList);
        }

        if (q.getGroupHaving() != null) {
            query.append(NL).append(" HAVING ").append(___expandConditions(q.getGroupHaving(), paramList, QContextType.HAVING));
            query.append(NL);
        }

        if (QUtils.notNullNorEmpty(q.getOrderBy())) {
            String oClauses = QUtils.join(q.getOrderBy(), it -> ___resolve(it, QContextType.ORDER_BY, paramList), COMMA, "", "");
            query.append(" ORDER BY ").append(oClauses).append(NL);
        }

        if (q.get_limit() != null) {
            if (q.get_limit() instanceof Integer && ((Integer) q.get_limit()) > 0) {
                query.append(" LIMIT ").append(String.valueOf(q.get_limit())).append(NL);
            } else if (q.get_limit() instanceof AParam) {
                paramList.add((AParam) q.get_limit());
                query.append(" LIMIT ").append(___resolve(q.get_limit(), QContextType.ORDER_BY)).append(NL);
            }
        }

        if (q.getOffset() != null) {
            if (q.getOffset() instanceof Integer && ((Integer) q.getOffset()) >= 0) {
                query.append(" OFFSET ").append(String.valueOf(q.getOffset())).append(NL);
            } else if (q.getOffset() instanceof AParam) {
                paramList.add((AParam) q.getOffset());
                query.append(" OFFSET ").append(___resolve(q.getOffset(), QContextType.ORDER_BY)).append(NL);
            }
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    protected List<QResultProxy> generateCTE(CTE cte) throws NyException {
        List<AParam> paramList = new LinkedList<>();
        List<String> qctes = new LinkedList<>();
        int recCount = 0;
        for (Object item : cte.getWiths()) {
            StringBuilder iqStr = new StringBuilder();
            Map map = (Map)item;
            Table tbl = (Table) map.get("table");

            iqStr.append(___tableName(tbl, QContextType.INTO));
            List<String> cols = (List) map.get("cols");
            if (QUtils.notNullNorEmpty(cols)) {
                iqStr.append(" (").append(String.join(", ", cols)).append(')');
            }

            Object query = map.get("query");
            if (query instanceof QuerySelect) {
                QResultProxy proxy = ___selectQuery((QuerySelect) query);
                iqStr.append(_AS_).append('(').append(proxy.getQuery()).append(')');
                paramList.addAll(proxy.getOrderedParameters());
            } else if (query instanceof WithClosure) {
                WithClosure withClosure = (WithClosure) query;
                QResultProxy proxy = ___combinationQuery(withClosure.getCombineType(),
                        Arrays.asList(withClosure.getAnchor(), withClosure.getRecursion()));
                iqStr.append(_AS_).append('(').append(proxy.getQuery()).append(')');
                paramList.addAll(proxy.getOrderedParameters());
                recCount++;
            }

            qctes.add(iqStr.toString());
        }

        StringBuilder mq = new StringBuilder();
        mq.append("WITH ");
        if (recCount > 0) {
            mq.append("RECURSIVE ");
        }
        mq.append(String.join(", ", qctes));

        QResultProxy proxy = ___selectQuery(cte.getQuerySelect());
        paramList.addAll(proxy.getOrderedParameters());
        mq.append(' ').append(proxy.getQuery());

        return Collections.singletonList(createProxy(mq.toString(), QueryType.CTE, paramList, null, null));
    }

    /**
     * Generate sql select query if it has full-outer-joins by replacing
     * them with left/right joins.
     *
     * @param q input select query.
     * @return query result.
     * @throws NyException any exception thrown while generating.
     */
    protected QResultProxy _generateSelectQFullJoin(QuerySelect q) throws NyException {
        int count;
        if ((count = SqlMisc.countJoin(q.get_joiningTable(), JoinType.FULL_JOIN)) > 0) {
            List<String> qs = new LinkedList<>();
            QResultProxy resultProxy = new QResultProxy();
            resultProxy.setOrderedParameters(new LinkedList<>());

            for (int i = count; i >= 0; i--) {
                QuerySelect qt = SqlMisc.cloneQuery(q);
                SqlMisc.flipNthFullJoin(qt.get_joiningTable(), i, 0);
                SqlMisc.appendNullableConstraints(qt,count - i - 1);

                StringBuilder qr = generateSelectQueryBody(qt, resultProxy.getOrderedParameters());
                qs.add(qr.toString());
            }

            resultProxy.setQueryType(QueryType.SELECT);
            resultProxy.setQuery(String.join(" UNION ALL ", qs));
            return resultProxy;

        } else {
            final List<AParam> paramList = new LinkedList<>();
            QueryType queryType = QueryType.SELECT;

            return createProxy(generateSelectQueryBody(q, paramList).toString(), queryType, paramList, null, null);
        }
    }

    /**
     * Generate the query clauses to between fetching column list and FROM tables.
     * This will be used by MsSQL to append INTO clause.
     *
     * @param q input select query model.
     * @param query query string to generate.
     * @param paramList parameter list.
     * @throws NyException any exception thrown while generating.
     */
    protected void ___selectQueryAfterFetchClause(QuerySelect q, StringBuilder query, List<AParam> paramList) throws NyException {
        // by default nothing will insert...
    }

    /**
     * Generated group by clause as it is different with rollup introduction.
     *
     * @param q input select query model.
     * @param query query string to generate.
     * @param paramList parameter list.
     * @throws NyException any exception thrown while generating.
     */
    protected void ___selectQueryGroupByClause(QuerySelect q, StringBuilder query, List<AParam> paramList) throws NyException {
        String gClauses = QUtils.join(q.getGroupBy(), it -> ___resolve(it, QContextType.GROUP_BY, paramList), COMMA, "", "");
        query.append(" GROUP BY ").append(gClauses);

        if (q.getGroupByRollup()) {
            // rollup enabled
            query.append(" WITH ROLLUP");
        }
    }

    @Override
    public QResultProxy ___truncateQuery(QueryTruncate q) {
        String query = "TRUNCATE TABLE " + ___tableName(q.getSourceTbl(), QContextType.TRUNCATE);
        return createProxy(query, QueryType.TRUNCATE, new ArrayList<>(), null, null);
    }

    @Override
    public QResultProxy ___partQuery(QueryPart q) throws NyException {
        List<AParam> paramList = new LinkedList<>();
        StringBuilder query = new StringBuilder();
        QueryType queryType = QueryType.PART;

        if (q.get_allProjections() != null) {
            query.append(___expandProjection(q.get_allProjections(), paramList, QContextType.SELECT));
            return createProxy(query.toString(), queryType, paramList, q.get_allProjections(), q);
        }

        if (q.getSourceTbl() != null) {
            query.append(___deriveSource(q.getSourceTbl(), paramList, QContextType.FROM));
            return createProxy(query.toString(), queryType, paramList, q.getSourceTbl(), q);
        }

        if (q.getWhereObj() != null) {
            query.append(___expandConditions(q.getWhereObj(), paramList, QContextType.CONDITIONAL));
            return createProxy(query.toString(), queryType, paramList, q.getWhereObj(), q);
        }

        if (q.get_assigns() != null) {
            query.append(___expandAssignments(q.get_assigns(), paramList, QContextType.UPDATE_SET));
            return createProxy(query.toString(), queryType, paramList, q.get_assigns(), q);
        }

        if (QUtils.notNullNorEmpty(q.get_intoColumns())) {
            query.append(___expandProjection(q.get_intoColumns(), paramList, QContextType.INSERT_PROJECTION));
            return createProxy(query.toString(), queryType, paramList, q.get_intoColumns(), q);
        }

        if (!QUtils.isNullOrEmpty(q.get_dataColumns())) {
            return createProxy("", queryType, paramList, q.get_dataColumns(), q);
        }
        throw new NyException("Unknown or incomplete re-usable query clause!");
    }

    @SuppressWarnings("unchecked")
    @Override
    public QResultProxy ___valueTable(ValueTable valueTable) throws NyException {
        Object vals = valueTable.getValues();
        if (vals == null) {
            throw new NyException("Values cannot be null for creating table out of it!");
        }

        String col = valueTable.getColumnAlias();
        List<AParam> params = new LinkedList<>();
        List<String> qItems = new LinkedList<>();
        if (vals instanceof Collection) {
            Collection<?> objVals = (Collection)vals;
            boolean first = true;
            for (Object item : objVals) {
                StringBuilder qi = new StringBuilder();
                qi.append("SELECT ");
                if (first) {
                    first = false;
                    if (item instanceof Map) {
                        qi.append(((Map<String, Object>) item).entrySet().stream()
                                .map(entry -> ___resolve(entry.getValue(), QContextType.FROM, params)
                                        + _AS_ + convertToAlias(entry.getKey(), getQuoteChar()))
                                .collect(Collectors.joining(", ")));

                        qItems.add(qi.toString());
                        continue;
                    } else if (col != null) {
                        qi.append(___resolve(item, QContextType.FROM, params))
                                .append(_AS_)
                                .append(convertToAlias(col, getQuoteChar()));
                        qItems.add(qi.toString());
                        continue;
                    }
                }

                if (item instanceof Map) {
                    qi.append(((Map) item).values().stream()
                            .map(v -> ___resolve(v, QContextType.FROM, params))
                            .collect(Collectors.joining(", ")));
                } else {
                    qi.append(___resolve(item, QContextType.FROM, params));
                }

                qItems.add(qi.toString());
            }
            QResultProxy proxy = new QResultProxy();
            proxy.setQuery(String.join(" UNION ALL ", qItems));
            proxy.setOrderedParameters(params);
            proxy.setRawObject(valueTable);
            return proxy;

        } else {
            throw new NyException("Values must be an instance of list!");
        }
    }

    protected QResultProxy createProxy(String query, QueryType queryType, List<AParam> params,
                                            Object raw, Query queryObject) {
        QResultProxy proxy = new QResultProxy();
        proxy.setQuery(query);
        proxy.setQueryType(queryType);
        proxy.setOrderedParameters(params);
        proxy.setRawObject(raw);
        proxy.setqObject(queryObject);
        return proxy;
    }

    protected String ___expandProjection(List<Object> columns, List<AParam> paramList, QContextType contextType) throws NyException {
        List<String> cols = new ArrayList<>();
        if (columns == null || columns.isEmpty()) {
            return "*";
        }

        List<Object> finalCols = new LinkedList<>();
        for (Object c : columns) {
            if (c instanceof QResultProxy) {
                if (((QResultProxy)c).getQueryType() != QueryType.PART) {
                    throw new NyException("Only query parts allowed to import within sql projection!");
                }
                List otherColumns = (List) ((QResultProxy)c).getRawObject();
                finalCols.addAll(otherColumns);
            } else if (c instanceof List) {
                finalCols.addAll((List)c);
            } else {
                finalCols.add(c);
            }
        }

        for (Object c : finalCols) {
            if (!(c instanceof TableAll)) {
                ___scanForParameters(c, paramList);
            }

            if (c instanceof TableAll) {
                cols.add(((TableAll) c).get__alias() + ".*");
            } else if (c instanceof Table) {
                //appendParamsFromTable((Table)c, paramList)
                String tbName = ___tableName((Table)c, contextType);
                if (((Table)c).__isResultOf()) {
                    cols.add(tbName);
                } else {
                    cols.add(tbName + ".*");
                }
            } else if (c instanceof Column) {
                appendParamsFromColumn((Column)c, paramList);
                String cName = ___columnName((Column)c, contextType, paramList);
                cols.add(cName);
            } else if (c instanceof String) {
                cols.add((String)c);
            } else {
                cols.add(String.valueOf(___resolve(c, contextType, paramList)));
            }
        }
        return cols.stream().collect(Collectors.joining(", "));
    }

    private static void appendParamsFromColumn(Column column, List<AParam> paramList) {
        if (column instanceof FunctionColumn) {
            if (((FunctionColumn) column).get_setOfCols()) {
                for (Object it : ((FunctionColumn) column).get_columns()) {
                    if (it instanceof QResultProxy && ((QResultProxy)it).getOrderedParameters() != null) {
                        paramList.addAll(((QResultProxy)it).getOrderedParameters());
                    }
                }
            } else {
                Object wrap = ((FunctionColumn) column).get_wrapper();
                if (wrap instanceof QResultProxy && ((QResultProxy)wrap).getOrderedParameters() != null) {
                    paramList.addAll(((QResultProxy)wrap).getOrderedParameters());
                }
            }
        }
    }

    protected void ___scanForParameters(Object expression, List<AParam> paramOrder) {
        if (expression != null) {
            if (expression instanceof QResultProxy) {
                QResultProxy resultProxy = (QResultProxy)expression;
                if (resultProxy.getOrderedParameters() != null) {
                    paramOrder.addAll(resultProxy.getOrderedParameters());
                }
            } else if (expression instanceof Table && ((Table)expression).__isResultOf()) {
                QResultProxy resultProxy = (QResultProxy)(((Table)expression).get__resultOf());
                if (resultProxy.getOrderedParameters() != null) {
                    paramOrder.addAll(resultProxy.getOrderedParameters());
                }
            } else if (expression instanceof FunctionColumn) {
                ___expandColumn((FunctionColumn) expression, paramOrder);
            }
            if (expression instanceof List) {
                for (Object it : (List)expression) {
                    ___scanForParameters(it, paramOrder);
                }
            }
        }
    }

    protected String ___deriveSource(Table table, List<AParam> paramOrder, QContextType contextType) {
        if (table instanceof Join) {
            return ___tableJoinName((Join)table, contextType, paramOrder);
        } else {
            if (table.__isResultOf()) {
                QResultProxy proxy = (QResultProxy) table.get__resultOf();
                if (proxy.getOrderedParameters() != null) {
                    paramOrder.addAll(proxy.getOrderedParameters());
                }
            }
            return ___tableName(table, contextType);
        }
    }

    protected void ___expandColumn(Column column, List<AParam> paramList) {
        if (column instanceof FunctionColumn && ((FunctionColumn) column).get_columns() != null) {
            for (Object it : ((FunctionColumn) column).get_columns()) {
                if (it instanceof FunctionColumn) {
                    ___expandColumn((FunctionColumn)it, paramList);
                }
            }
        }
    }

    protected String ___expandConditions(Where where, List<AParam> paramOrder, QContextType contextType) {
        StringBuilder builder = new StringBuilder();
        List<Object> clauses = where.getClauses();
        int ccount = 0;
        for (Object c : clauses) {
            if (c instanceof QOperator) {
                ccount++;
            } else if (c instanceof String) {
                String expr = String.valueOf(c).trim();
                if (expr.equals("AND") || expr.equals("OR")) {
                    ccount++;
                }
            }
        }

        if (ccount == 0 && clauses.size() > 1) {
            // add AND between them
            List<Object> tmp = new LinkedList<>();
            boolean add = false;
            for (Object c : where.getClauses()) {
                if (add) tmp.add(QOperator.AND);
                tmp.add(c);
                add = true;
            }
            clauses = tmp;
        }

        for (Object c : clauses) {
            if (c instanceof String) {
                builder.append(c);
            } else if (c instanceof QOperator) {
                builder.append(' ').append(___convertOperator((QOperator)c)).append(' ');
            } else if (c instanceof Where.QCondition) {
                builder.append(___expandCondition((Where.QCondition)c, paramOrder, contextType));
            } else if (c instanceof Where.QConditionGroup) {
                builder.append(QUtils.parenthesis(
                        ___expandConditionGroup((Where.QConditionGroup)c, paramOrder, contextType)));
            }
        }

        return builder.toString();
    }

    protected String ___expandCondition(Where.QCondition c, List<AParam> paramOrder, QContextType contextType) {
        ___scanForParameters(c.getLeftOp(), paramOrder);
        ___scanForParameters(c.getRightOp(), paramOrder);
        boolean parenthesis = (c.getRightOp() instanceof QResultProxy);

        if (c instanceof Where.QUnaryCondition) {
            QOperator op = c.getOp();
            return ___convertOperator(op) + (op != QOperator.UNKNOWN ? " " : "") +
                    (parenthesis ?
                            QUtils.parenthesis(___resolve(((Where.QUnaryCondition) c).chooseOp(), contextType, paramOrder))
                            : ___resolve(((Where.QUnaryCondition) c).chooseOp(), contextType, paramOrder));
        } else {
            return ___resolveOperand(c.getLeftOp(), paramOrder, contextType) +
                    (c.getOp() != QOperator.UNKNOWN ? ' ' + ___convertOperator(c.getOp()) + ' ' : ' ') +
                    (!parenthesis ? ___resolveOperand(c.getRightOp(), paramOrder, contextType)
                            : QUtils.parenthesis(___resolveOperand(c.getRightOp(), paramOrder, contextType)));
        }
    }

    private String ___resolveOperand(Object operand, List<AParam> paramOrder, QContextType contextType) {
        if (operand instanceof Where.QCondition) {
            return ___expandCondition((Where.QCondition)operand, paramOrder, contextType);
        } else {
            return ___resolve(operand, contextType, paramOrder);
        }
    }

    protected String ___expandConditionGroup(Where.QConditionGroup group, List<AParam> paramOrder, QContextType contextType) {
        String gCon = group.getCondConnector() == null ? "" : " " + ___convertOperator(group.getCondConnector()) + " ";
        List<String> list = new LinkedList<>();

        for (Object clause : group.getWhere().getClauses()) {
            if (clause instanceof Where.QCondition) {
                list.add(___expandCondition((Where.QCondition)clause, paramOrder, contextType));
            } else if (clause instanceof Where.QConditionGroup) {
                list.add(QUtils.parenthesis(
                        ___expandConditionGroup((Where.QConditionGroup)clause, paramOrder, contextType)));
            } else {
                list.add(___resolve(clause, contextType, paramOrder));
            }
        }
        return list.stream().collect(Collectors.joining(gCon));
    }

    protected String ___expandAssignments(Assign assign, List<AParam> paramOrder, QContextType contextType) {
        List<Object> clauses = assign.getAssignments();
        List<String> derived = new ArrayList<>();
        for (Object c : clauses) {
            if (c instanceof Assign.AnAssign) {
                Assign.AnAssign anAssign = (Assign.AnAssign)c;
                if (anAssign.getLeftOp() instanceof AParam) {
                    paramOrder.add((AParam)anAssign.getLeftOp());
                }
                ___scanForParameters(anAssign.getRightOp(), paramOrder);

                String val = ___resolve(anAssign.getLeftOp(), contextType, paramOrder) +
                        ' ' + ___convertOperator(anAssign.getOp()) + ' ' +
                        ___resolve(anAssign.getRightOp(), contextType, paramOrder);
                derived.add(val);
            } else {
                derived.add(___resolve(c, contextType, paramOrder));
            }
        }

        return derived.stream().collect(Collectors.joining(", "));
    }

    private <T> List<T> addSafely(List<T> list, T item) {
        if (list != null) {
            list.add(item);
        }
        return list;
    }

    private <T> List<T> addAllSafely(List<T> list, Collection<T> items) {
        if (list != null) {
            list.addAll(items);
        }
        return list;
    }
}
