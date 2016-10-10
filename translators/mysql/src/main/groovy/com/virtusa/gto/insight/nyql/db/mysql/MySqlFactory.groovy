package com.virtusa.gto.insight.nyql.db.mysql

import com.virtusa.gto.insight.nyql.db.QDbFactory
import com.virtusa.gto.insight.nyql.db.QTranslator

/**
 * @author IWEERARATHNA
 */
class MySqlFactory implements QDbFactory {
    @Override
    String dbName() {
        return "mysql"
    }

    @Override
    String dataSourceClassName() {
        return "com.mysql.jdbc.jdbc2.optional.MysqlDataSource"
    }

    @Override
    QTranslator createTranslator() {
        return new MySql()
    }

    @Override
    List<Class<?>> createTraits() {
        return []
    }

    @Override
    String driverClassName() {
        return "com.mysql.jdbc.Driver"
    }
}
