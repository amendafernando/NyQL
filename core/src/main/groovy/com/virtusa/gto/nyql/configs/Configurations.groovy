package com.virtusa.gto.nyql.configs

import com.virtusa.gto.nyql.db.QDbFactory
import com.virtusa.gto.nyql.exceptions.NyConfigurationException
import com.virtusa.gto.nyql.exceptions.NyException
import com.virtusa.gto.nyql.model.*
import com.virtusa.gto.nyql.model.impl.QNoProfiling
import com.virtusa.gto.nyql.model.impl.QProfExecutorFactory
import com.virtusa.gto.nyql.model.impl.QProfRepository
import com.virtusa.gto.nyql.utils.Constants
import com.virtusa.gto.nyql.utils.QUtils
import com.virtusa.gto.nyql.utils.ReflectUtils
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Constructor
import java.time.format.DateTimeFormatter
/**
 * @author IWEERARATHNA
 */
class Configurations {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configurations)

    private static final String[] DEF_IMPORTS = [
            "groovy.transform.Field",
            "com.virtusa.gto.nyql.ddl.DKeyIndexType",
            "com.virtusa.gto.nyql.ddl.DFieldType",
            "com.virtusa.gto.nyql.ddl.DReferenceOption",
            "com.virtusa.gto.nyql.utils.QueryType",
            "java.sql.JDBCType",
            "java.sql.Types",
            "com.virtusa.gto.nyql.model.units.AParam.ParamScope"
    ]

    private static final Map Q_LOGGING_LEVELS = [trace: 1, debug: 2, info: 3, warn: 4, error: 5].asImmutable()

    protected DateTimeFormatter timestampFormatter = DateTimeFormatter.ISO_INSTANT

    protected Map properties = [:]

    protected String name
    protected int qLogLevel = -1
    protected String cacheVarName
    protected final Object lock = new Object()
    protected boolean configured = false
    protected ClassLoader classLoader
    protected QProfiling profiler

    protected QDatabaseRegistry databaseRegistry
    protected QExecutorRegistry executorRegistry
    protected QRepositoryRegistry repositoryRegistry
    protected QMapperRegistry mapperRegistry

    @PackageScope Configurations() {}

    Configurations configure(Map configProps) throws NyException {
        properties = configProps
        classLoader = Thread.currentThread().contextClassLoader

        synchronized (lock) {
            doConfig()
            configured = true
        }
        return this
    }

    boolean isConfigured() {
        synchronized (lock) {
            return configured
        }
    }

    protected void setName() {
        name = properties.name
        if (name == null) {
            name = QUtils.genId()
        }
    }

    protected void doConfig() throws NyException {
        setName()

        databaseRegistry = QDatabaseRegistry.newInstance()
        executorRegistry = QExecutorRegistry.newInstance()
        repositoryRegistry = QRepositoryRegistry.newInstance()
        mapperRegistry = QMapperRegistry.newInstance().discover(this.classLoader)

        // load query related configurations
        loadQueryInfo(properties.queries as Map)

        boolean profileEnabled = startProfiler(this)

        // mark active database
        String activeDb = getActivatedDb()
        LOGGER.info("Activated: " + activeDb)
        loadActivatedTranslator(activeDb)

        // load repositories
        loadRepos(profileEnabled)

        // load executors
        DbInfo dbInfo = loadExecutors(activeDb, profileEnabled)

        // finally, initialize factory
        def factory = databaseRegistry.getDbFactory(activeDb)
        factory.init(this, dbInfo)

        runBootstrapScript(activeDb, factory)
    }

    protected boolean startProfiler(Configurations configurations) {
        boolean profileEnabled = loadProfiler()
        if (!profileEnabled) {
            if (!(configurations instanceof ConfigurationsV2)) {
                LOGGER.warn('Query profiling has been disabled! You might not be able to figure out timing of executions.')
            }
        } else {
            LOGGER.debug("Query profiling enabled with ${profiler.getClass().simpleName}!")
            Map profOptions = properties.profiling?.options ?: [:]
            profOptions['isCached'] = properties.caching.compiledScripts
            profiler.start(profOptions)
        }
        profileEnabled
    }

    @CompileStatic
    private void runBootstrapScript(String activeDb, QDbFactory dbFactory) {
        QSession bootSession = QSession.create(this, '__bootstrapscript__')
        def scripts = dbFactory.createTranslator().getBootstrapScripts(bootSession)
        if (scripts != null) {
            LOGGER.debug("Bootstrapping database using initial scripts [" + activeDb + "]...")
            executorRegistry.defaultExecutorFactory().create().execute(scripts)
            LOGGER.debug("Bootstrapping completed successfully!")
        }
    }

    private void loadActivatedTranslator(String activeDb) {
        if (activeDb == null) {
            throw new NyConfigurationException('No database has been specified to be activated!')
        }

        def factoryClasses = getAvailableTranslators()
        if (QUtils.notNullNorEmpty(factoryClasses)) {
            Exception firstEx = null
            for (def tr : factoryClasses) {
                try {
                    loadDBFactory(tr)
                } catch (ReflectiveOperationException | NyConfigurationException ex) {
                    LOGGER.warn(ex.getMessage())
                    if (firstEx == null) {
                        firstEx = ex
                    }
                }
            }
            //databaseRegistry.load(activeDb)

        } else {
            throw new NyConfigurationException('No NyQL translators have been specified in the configuration file!')
        }
    }

    @CompileStatic
    protected void loadQueryInfo(Map options) {
        if (options != null && options.parameters) {
            Map paramConfig = options.parameters as Map
            String tsFormat = paramConfig[ConfigKeys.QUERY_TIMESTAMP_FORMAT]
            if (tsFormat != null && !tsFormat.isEmpty()) {
                String tsLocale = paramConfig[ConfigKeys.QUERY_TIMESTAMP_LOCALE]
                timestampFormatter = DateTimeFormatter.ofPattern(tsFormat,
                        tsLocale == null ? Locale.default : Locale.forLanguageTag(tsLocale))

                LOGGER.debug('JDBC executor uses time-format ' + tsFormat + ' in ' + (tsLocale ?: 'system default') + ' locale.')
            }
        } else {
            LOGGER.info('JDBC executor uses Java ISO Instant time-format in system default locale.')
        }
    }

    protected boolean loadProfiler() throws NyConfigurationException {
        def profiling = properties[ConfigKeys.PROFILING]
        if (profiling?.enabled) {
            def prof = profiling.profiler
            if (prof instanceof QProfiling) {
                profiler = prof
            } else {
                try {
                    profiler = classLoader.loadClass(String.valueOf(prof)).newInstance() as QProfiling
                } catch (ReflectiveOperationException ex) {
                    throw new NyConfigurationException("Error occurred while loading profiler! $prof", ex)
                }
            }
            return true

        } else {
            profiler = QNoProfiling.INSTANCE
            return false
        }
    }

    protected void loadRepos(boolean profEnabled=false) {
        int added = 0
        String defRepo = properties.defaultRepository ?: Constants.DEFAULT_REPOSITORY_NAME
        List repos = properties.repositories ?: []
        for (Map r : repos) {
            Map args = r.mapperArgs ?: [:]
            args.put(ConfigKeys.LOCATION_KEY, properties._location)

            boolean thisDef = r.name == defRepo
            String mapper = String.valueOf(r.mapper)

            QMapperFactory mapperFactory = mapperRegistry.getMapperFactory(mapper)
            QScriptMapper scriptMapper = mapperFactory.create(mapper, args, this)
            QRepository qRepository = ReflectUtils.newInstance(String.valueOf(r.repo), classLoader, this, scriptMapper)

            if (profEnabled) {
                qRepository = new QProfRepository(this, qRepository)
            }
            repositoryRegistry.register(String.valueOf(r.name), qRepository, thisDef)
            added++
        }

        if (properties[ConfigKeys.REPO_MAP]) {
            Map<String, QRepository> repositoryMap = (Map<String, QRepository>) properties[ConfigKeys.REPO_MAP]
            repositoryMap.each {
                QRepository qRepository = profEnabled ? new QProfRepository(this, it.value) : it.value
                repositoryRegistry.register(it.key, qRepository, it.key == defRepo)
                added++
            }
        }
    }

    protected DbInfo loadExecutors(String activeDb, boolean profEnabled=false) {
        QDbFactory activeFactory = databaseRegistry.getDbFactory(activeDb)
        boolean loadDefOnly = properties.loadDefaultExecutorOnly ?: false
        String defExec = properties.defaultExecutor ?: Constants.DEFAULT_EXECUTOR_NAME
        List execs = properties.executors ?: []
        DbInfo activeDbInfo = DbInfo.UNRESOLVED
        for (Map r : execs) {
            boolean thisDef = r.name == defExec
            if (loadDefOnly && !thisDef) {
                LOGGER.warn("Executor '{}' will not load since it is not the default executor!", r.name)
                continue
            }

            r.put(ConfigKeys.JDBC_DRIVER_CLASS_KEY, r.jdbcDriverClass ?: activeFactory.driverClassName())
            r.put(ConfigKeys.JDBC_DATASOURCE_CLASS_KEY, r.jdbcDataSourceClass ?: activeFactory.dataSourceClassName())
            Class<?> clazz = classLoader.loadClass(String.valueOf(r.factory))
            QExecutorFactory executorFactory = createExecFactoryInstance(clazz, r)

            if (profEnabled) {
                executorFactory = new QProfExecutorFactory(this, executorFactory)
            }
            DbInfo dbInfo = executorFactory.init(r, this)
            if (dbInfo != DbInfo.UNRESOLVED) {
                activeDbInfo = dbInfo
            }
            executorRegistry.register(String.valueOf(r.name), executorFactory, thisDef)
        }
        activeDbInfo
    }

    private static QExecutorFactory createExecFactoryInstance(Class<?> clz, Map options) {
        try {
            Constructor<?> constructor = clz.getDeclaredConstructor(Map)
            (QExecutorFactory) constructor.newInstance(options)
        } catch (NoSuchMethodException ignored) {
            (QExecutorFactory) clz.newInstance()
        }
    }

    private void loadDBFactory(String clzName) throws NyConfigurationException {
        try {
            loadDBFactory(classLoader.loadClass(clzName))
        } catch (ReflectiveOperationException ex) {
            throw new NyConfigurationException("No database factory implementation class found by name '$clzName'!", ex)
        }
    }

    private void loadDBFactory(Class factoryClz) throws NyConfigurationException {
        try {
            QDbFactory factory = factoryClz.newInstance() as QDbFactory
            loadDBFactory(factory)
        } catch (ReflectiveOperationException ex) {
            throw new NyConfigurationException("Failed to initialize database factory class by name '${factoryClz.name}'!", ex)
        }
    }

    private void loadDBFactory(QDbFactory qDbFactory) {
        databaseRegistry.register(qDbFactory)
    }

    void shutdown() {
        LOGGER.debug('Shutting down nyql...')
        safeClose('Executors') { executorRegistry.shutdown() }
        safeClose('Repositories') { repositoryRegistry.shutdown() }
        safeClose('Profiler') {
            if (profiler != null) {
                profiler.close()
            }
        }
        synchronized (lock) {
            configured = false
        }
        ConfigBuilder.instance().reset()
        LOGGER.debug('Shutdown completed.')
    }

    @SuppressWarnings('CatchThrowable')
    private static void safeClose(String workerName, Runnable runnable) {
        try {
            runnable.run()
        } catch (Throwable ignored) {
            LOGGER.error('Failed to close ' + workerName + '!', ignored)
        }
    }

    String cachingIndicatorVarName() {
        if (cacheVarName != null) {
            return cacheVarName
        }
        cacheVarName = properties.caching.indicatorVariableName ?: Constants.DSL_CACHE_VARIABLE_NAME
        cacheVarName
    }

    String[] defaultImports() {
        Object defImports = properties.defaultImports
        if (defImports == null) {
            DEF_IMPORTS
        } else if (Boolean.isAssignableFrom(defImports.class)) {
            if ((boolean) defImports) {
                DEF_IMPORTS
            } else {
                null
            }
        } else {
            (String[]) defImports
        }
    }

    String getActivatedDb() {
        (String) QUtils.readEnv(ConfigKeys.SYS_ACTIVE_DB, properties.activate)
    }

    boolean isCheckCacheValidations() {
        (boolean) (properties.caching.checkCacheValidations ?: true)
    }

    boolean cacheRawScripts() {
        String cacheStatus = QUtils.readEnv(ConfigKeys.SYS_CACHE_RAW_ENABLED, null)
        if (cacheStatus == null) {
            (boolean) properties.caching.compiledScripts
        } else {
            Boolean.parseBoolean(cacheStatus);
        }
    }

    boolean isAllowRecompilation() {
        (boolean) (properties.caching.allowRecompilation ?: false)
    }

    boolean cacheGeneratedQueries() {
        String cacheStatus = QUtils.readEnv(ConfigKeys.SYS_CACHE_RAW_ENABLED, null)
        if (cacheStatus == null) {
            (boolean) properties.caching.generatedQueries
        } else {
            Boolean.parseBoolean(cacheStatus)
        }
    }

    List getAvailableTranslators() {
        properties.translators
    }

    List getSupportedScriptExtensions() {
        properties.supportedExtensions ?: ConfigKeys.DEFAULT_EXTENSIONS
    }

    Map getAllProperties() {
        properties
    }

    QProfiling getProfiler() {
        profiler
    }

    QExecutorRegistry getExecutorRegistry() {
        executorRegistry
    }

    QRepositoryRegistry getRepositoryRegistry() {
        repositoryRegistry
    }

    DateTimeFormatter getTimestampFormatter() {
        timestampFormatter
    }

    QDbFactory getActiveDbFactory() {
        databaseRegistry.getDbFactory(getActivatedDb())
    }

    Map getQueryConfigs() {
        properties.queries as Map
    }

    String getName() {
        name
    }

    boolean isRegisterMXBeans() {
        (boolean) properties.getOrDefault("registerMXBeans", true);
    }

    /**
     * Returns query logging level. By default is is set to 'trace'.
     *
     * @return query logging level.
     */
    int getQueryLoggingLevel() {
        if (qLogLevel > 0) {
            qLogLevel
        } else {
            qLogLevel = (int) Q_LOGGING_LEVELS.getOrDefault(properties.queryLoggingLevel ?: 'trace', 1)
            qLogLevel
        }
    }

}
