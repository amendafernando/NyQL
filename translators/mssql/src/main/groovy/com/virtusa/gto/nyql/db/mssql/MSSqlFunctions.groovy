package com.virtusa.gto.nyql.db.mssql

import com.virtusa.gto.nyql.Case
import com.virtusa.gto.nyql.FunctionColumn
import com.virtusa.gto.nyql.db.AbstractSQLTranslator
import com.virtusa.gto.nyql.db.QFunctions
import com.virtusa.gto.nyql.db.TranslatorOptions
import com.virtusa.gto.nyql.exceptions.NyException
import com.virtusa.gto.nyql.exceptions.NySyntaxException
import groovy.transform.CompileStatic

import java.util.stream.Collectors

/**
 * @author IWEERARATHNA
 */
abstract class MSSqlFunctions extends AbstractSQLTranslator implements QFunctions {

    protected MSSqlFunctions() {
        super()
    }

    MSSqlFunctions(TranslatorOptions theOptions) {
        super(theOptions)
    }

    @Override
    String date_format(Object pms) {
        def c = ___val(cx)
        def pmx = ___pm(cx)

        if (c instanceof List) {
            return String.format('FORMAT(%s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        } else {
            throw new NySyntaxException('GREATEST function requires at least two or more values!')
        }
    }

    String greatest(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)

        if (c instanceof List) {
            Case aCase = new Case()
            aCase.WHEN { GTE(c.get(0), c.get(1)) } THEN { c.get(0) } ELSE { c.get(1) }
            return ___resolveIn(aCase, pmx)
        } else {
            throw new NySyntaxException('GREATEST function requires at least two or more values!')
        }
    }

    String least(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)

        if (c instanceof List) {
            Case aCase = new Case()
            aCase.WHEN { LTE(c.get(0), c.get(1)) } THEN { c.get(0) } ELSE { c.get(1) }
            return ___resolveIn(aCase, pmx)
        } else {
            throw new NySyntaxException('LEAST function requires at least two or more values!')
        }
    }

    @Override
    String concat_ws(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof String) {
            return String.valueOf(c)
        } else {
            List list
            Object sep = null
            if (c instanceof FunctionColumn) {
                list = (List)c._columns
            } else if (c instanceof List) {
                list = (List)c.get(1)
                sep = c.get(0)
            } else {
                return null
            }

            String delim = ', ' + ___resolveIn(sep, pmx) + ', '
            return list.stream().map {
                col -> if (col instanceof String) {
                    return String.valueOf(col)
                } else {
                    return (String)___resolveIn(col, pmx)
                }
            }.collect(Collectors.joining(delim, 'CONCAT(', ')'))
        }
    }

    @Override
    String stat_stddevpop(Object cx) {
        return String.format('STDEVP(%s)', ___resolveInP(cx))
    }

    @Override
    String stat_stddevsamp(Object cx) {
        return String.format('STDEV(%s)', ___resolveInP(cx))
    }

    @Override
    String stat_varpop(Object cx) {
        return String.format('VARP(%s)', ___resolveInP(cx))
    }

    @Override
    String stat_varsamp(Object cx) {
        return String.format('VAR(%s)', ___resolveInP(cx))
    }

    @CompileStatic
    @Override
    String truncate(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            return 'ROUND(' + ___resolveIn(c.get(0), pmx) + ', ' + ___resolveIn(c.get(1), pmx) + ', 1)'
        }
        throw new NyException('Incorrect number of parameters for rounding with truncate function!')
    }

    @CompileStatic
    @Override
    String trig_atan2(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('ATN2(%s, %s)', ___resolveIn(((List)c)[0], pmx), ___resolveIn(((List)c)[1], pmx))
        else throw new NyException('ATAN2 function requires two parameters!')
    }

    @Override
    String lg_ln(Object cx) {
        String.format('LOG(%s)', ___resolveInP(cx))
    }

    @Override
    String str_lpad(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            if (c.size() == 3) {
                return String.format('RIGHT(%s, %s)',
                        String.format('REPLICATE(%s, %s) + %s', ___resolveIn(c.get(2), pmx), ___resolveIn(c.get(1), pmx), ___resolveIn(c.get(0), pmx)),
                        ___resolveIn(c.get(1), pmx))
            } else if (c.size() == 2) {
                return String.format('RIGHT(%s, %s)',
                        String.format('REPLICATE(\' \', %s) + %s', ___resolveIn(c.get(2), pmx), ___resolveIn(c.get(1), pmx), ___resolveIn(c.get(0), pmx)),
                        ___resolveIn(c.get(1), pmx))
            }
        }
        throw new NyException('Insufficient parameters for right pad function!')
    }

    @Override
    String str_rpad(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            if (c.size() == 3) {
                return String.format('LEFT(%s, %s)',
                        String.format('REPLICATE(%s, %s) + %s', ___resolveIn(c.get(2), pmx), ___resolveIn(c.get(1), pmx), ___resolveIn(c.get(0), pmx)),
                        ___resolveIn(c.get(1), pmx))
            } else if (c.size() == 2) {
                return String.format('LEFT(%s, %s)',
                        String.format('REPLICATE(\' \', %s) + %s', ___resolveIn(c.get(2), pmx), ___resolveIn(c.get(1), pmx), ___resolveIn(c.get(0), pmx)),
                        ___resolveIn(c.get(1), pmx))
            }
        }
        throw new NyException('Insufficient parameters for left pad function!')
    }

    @Override
    String str_replace(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            return 'REPLACE(' + ___resolveIn(c[0], pmx) + ', ' + ___resolveIn(c[1], pmx) + ', ' + ___resolveIn(c[2], pmx) + ')'
        }
        throw new NyException('Incorrect number of parameters for string replace function!')
    }

    @Override
    String str_repeat(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            return 'REPLICATE(' + ___resolveIn(c[0], pmx) + ', ' + ___resolveIn(c[1], pmx) + ')'
        }
        throw new NyException('Incorrect number of parameters for string repeat function')
    }

    @Override
    String date_trunc(Object it) {
        String.format('CAST(%s AS DATE)', ___resolveInP(it))
    }

    @Override
    String substr(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            'SUBSTRING(' + ___resolveIn(c[0], pmx) + ', ' + ___resolveIn(c[1], pmx) +
                    (c.size() > 2 ? ', ' + ___resolveIn(c[2], pmx) : '') + ')'
        } else {
            throw new NySyntaxException('Insufficient parameters for SUBSTRING function!')
        }
    }

    @Override
    String position(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('CHARINDEX(%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Insufficient parameters for POSITION function!')
        }
    }

    @Override
    String position_last(Object cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String tmp = ___resolveIn(c.get(0), pmx)
            String.format('LEN(%s) - CHARINDEX(%s, REVERSE(%s))', tmp, ___resolveIn(c.get(1), pmx), tmp)
        } else {
            throw new NyException('Insufficient parameters for POSITION function!')
        }
    }

    @Override
    String cast_to_int(Object col) {
        String.format('CAST(%s AS INT)', ___resolveInP(col))
    }

    @Override
    String cast_to_date(Object col) {
        date_trunc(col)
    }

    @Override
    String cast_to_str(Object col) {
        String.format('STR(%s)', ___resolveInP(col))
    }

    @Override
    String cast_to_bigint(Object col) {
        String.format('CAST(%s AS BIGINT)', ___resolveInP(col))
    }

    @Override
    String current_epoch() {
        'CAST(DATEDIFF(SECOND, \'19700101\', GETUTCDATE()) AS BIGINT) * 1000'
    }

    @Override
    String epoch_to_date(Object col) {
        String.format('CAST(DATEADD(s, %s, \'19700101\') AS DATE)', ___resolveInP(col))
    }

    @Override
    String epoch_to_datetime(Object col) {
        String.format('DATEADD(s, %s, \'19700101\')', ___resolveInP(col))
    }

    String date_diff_years(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(year, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }
    String date_diff_months(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(month, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }
    String date_diff_days(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(day, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }
    String date_diff_weeks(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(week, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }
    String date_diff_hours(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(hour, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }
    String date_diff_minutes(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(minute, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }
    String date_diff_seconds(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) String.format('DATEDIFF(second, %s, %s)', ___resolveIn(c[0], pmx), ___resolveIn(c[1], pmx))
        else throw new NySyntaxException("DATE DIFF function requires exactly two parameters!")
    }

    String date_add_days(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(day, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }
    String date_add_months(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(month, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }
    String date_add_years(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(year, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }
    String date_add_weeks(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(week, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }
    String date_add_hours(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(hour, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }
    String date_add_minutes(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(minute, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }
    String date_add_seconds(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(second, %s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date add function requires exactly two parameters!')
        }
    }

    String date_sub_days(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(day, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
    String date_sub_months(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(month, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
    String date_sub_years(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(year, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
    String date_sub_weeks(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(week, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
    String date_sub_hours(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(hour, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
    String date_sub_minutes(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(minute, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
    String date_sub_seconds(cx) {
        def c = ___val(cx)
        def pmx = ___pm(cx)
        if (c instanceof List) {
            String.format('DATEADD(second, -%s, %s)', ___resolveIn(c[1], pmx), ___resolveIn(c[0], pmx))
        } else {
            throw new NySyntaxException('Date subtract function requires exactly two parameters!')
        }
    }
}
