package com.virtusa.gto.nyql.engine

import com.virtusa.gto.nyql.configs.*
import com.virtusa.gto.nyql.engine.impl.NyQLResult
import com.virtusa.gto.nyql.exceptions.NyConfigurationException
import com.virtusa.gto.nyql.exceptions.NyException
import com.virtusa.gto.nyql.model.NyQLInstanceMXBean
import com.virtusa.gto.nyql.model.QPagedScript
import com.virtusa.gto.nyql.model.QScript
import com.virtusa.gto.nyql.model.QSession
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import java.util.function.BiFunction
/**
 * @author IWEERARATHNA
 */
@CompileStatic
class NyQLInstance implements NyQLInstanceMXBean {
    
    private static final Map<String, Object> EMPTY_MAP = [:]

    private final Configurations configurations

    private NyQLInstance(Configurations theConfigInstance) {
        this.configurations = theConfigInstance
    }

    static NyQLInstance createFromResource(String name, String resourcePath, ClassLoader classLoader = null) throws NyException {
        ClassLoader cl = classLoader ?: Thread.currentThread().contextClassLoader
        InputStream inputStream = null
        try {
            inputStream = cl.getResourceAsStream(resourcePath)
            create(name, inputStream)
        } catch (NyException ex) {
            throw ex
        } catch (Exception ex) {
            throw new NyConfigurationException("Failed to read resource '${resourcePath}'!", ex)
        } finally {
            if (inputStream != null) {
                inputStream.close()
            }
        }
    }

    @Deprecated
    static NyQLInstance create(InputStream inputStream) {
        create(null, inputStream)
    }

    static NyQLInstance create(String name, InputStream inputStream) {
        create(name, ConfigParser.parseAndResolve(inputStream))
    }

    @Deprecated
    static NyQLInstance create(File configFile) {
        create(null, configFile)
    }

    static NyQLInstance create(String name, File configFile) {
        create(name, ConfigParser.parseAndResolve(configFile))
    }

    static NyQLInstance create(String name, Map configData) {
        configData.put(ConfigKeys.LOCATION_KEY, new File('.').canonicalPath)
        Configurations configInst = ConfigBuilder.instance(name).setupFrom(configData).build()
        create(configInst)
    }

    static NyQLInstance create(Configurations configInst) {
        NyQLInstance nyQLInstance = new NyQLInstance(configInst)
        if (configInst.isRegisterMXBeans()) {
            JmxConfigurator.get().registerMXBean(nyQLInstance)
        }
        nyQLInstance
    }

    Configurations getConfigurations() {
        return configurations
    }

    /**
     * <p>
     * Parse the given file indicated from given script name and returns the generated query
     * with its other information. Your script name would be the relative path from the
     * script root directory, always having forward slashes (/).
     * </p>
     * You should call this <b>only</b> if you are working with a script repository.
     *
     * @param scriptName name of the script.
     * @return generated query instance.
     * @throws com.virtusa.gto.nyql.exceptions.NyException any exception thrown while parsing.
     */
    @CompileStatic
    QScript parse(String scriptName) throws NyException {
        parse(scriptName, EMPTY_MAP)
    }

    /**
     * <p>
     * Parse the given file indicated from given script name using the given variable set
     * and returns the generated query
     * with its other information. Your script name would be the relative path from the
     * script root directory, always having forward slashes (/).
     * </p>
     * You should call this <b>only</b> if you are working with a script repository.
     *
     * @param scriptName name of the script.
     * @param data set of variable data required for generation of query.
     * @return generated query instance.
     * @throws NyException any exception thrown while parsing.
     */
    @CompileStatic
    QScript parse(String scriptName, Map<String, Object> data) throws NyException {
        QSession qSession = QSession.create(configurations, scriptName)
        if (data) {
            qSession.sessionVariables.putAll(data)
        }
        configurations.repositoryRegistry.defaultRepository().parse(scriptName, qSession)
    }

    /**
     * Allows recompiling a script when it is already compiled and cached. You may call
     * this method at runtime, but it does not reload or recompile unless scripts are loaded from
     * file.
     *
     * @param scriptName unique script name.
     * @throws NyException any exception thrown while recompiling.
     * @since v2
     */
    @CompileStatic
    void recompileScript(String scriptName) throws NyException {
        configurations.repositoryRegistry.defaultRepository().reloadScript(scriptName)
    }

    /**
     * Shutdown the nyql engine.
     * This should be called only when your application exits.
     */
    void shutdown() {
        if (configurations.isRegisterMXBeans()) {
            JmxConfigurator.get().removeMXBean(this)
        }
        configurations.shutdown()
    }

    /**
     * <p>
     * Executes a given file indicated by the script name and returns the final result
     * which was output of the last statement in the script ran.
     * </p>
     * <p>
     * This method will automatically parse the script and execute using internally
     * configured executor.
     * </p>
     *
     * @param scriptName name of the script to be run.
     * @return the result of the script execution.
     * @throws NyException any exception thrown while parsing or executing.
     */
    @CompileStatic
    <T> T execute(String scriptName) throws NyException {
        (T) execute(scriptName, EMPTY_MAP)
    }

    /**
     * <p>
     * Executes a given file indicated by the script name using given set of variables
     * and returns the final result
     * which was output of the last statement in the script ran.
     * </p>
     * <p>
     * This method will automatically parse the script and execute using internally
     * configured executor.
     * </p>
     * <b>Note:</b><br/>
     * You should pass all parameter values required for the query execution.
     *
     * @param scriptName name of the script to be run.
     * @param data set of variables to be passed to the script run.
     * @return the result of the script execution.
     * @throws NyException any exception thrown while parsing or executing.
     */
    @CompileStatic
    <T> T execute(String scriptName, Map<String, Object> data) throws NyException {
        QScript script = null
        try {
            script = parse(scriptName, data)
            (T) configurations.executorRegistry.defaultExecutorFactory().create().execute(script)
        } finally {
            if (script != null) {
                script.free()
            }
        }
    }

    /**
     * Executes the given <code>select</code> query and fetches subset of result each has rows size of
     * <code>pageSize</code>. This query will run only once in the server and the result rows are
     * paginated.
     * 
     * <p>
     *     <b>Caution:</b> This execution is NOT equivalent to the db cursors, but this is
     *     a JDBC level pagination which makes easier for developers to iterate subset of
     *     results efficiently from code rather not loading all result rows into the application
     *     memory at once.
     * </p>
     *
     * @param scriptName name of the script to run.
     * @param pageSize number of rows per page to return in each block.
     * @param data set of variables to be passed to the script run.
     * @return an iterable list of pages (blocks) of rows. The last page may not have <code>pageSize</code> rows,
     *          but at least one.
     * @throws NyException any exception thrown while executing for pagination. This may cause the provided
     *          script has none-other than SELECT query type.
     */
    @CompileStatic
    Iterable<NyQLResult> paginate(String scriptName, int pageSize, Map<String, Object> data) throws NyException {
        QScript script = new QPagedScript(parse(scriptName, data), pageSize)
        (Iterable<NyQLResult>) configurations.executorRegistry.defaultExecutorFactory().create().execute(script)
    }

    /**
     * Executes the given script and returns the result as a json string.
     * <p>
     *     If you still want to parse the json again, use the other execute method
     *     <code>execute(String, Map)</code>.
     * </p>
     *
     * @param scriptName name of the script to run.
     * @param data set of variables required for script.
     * @return result as json string.
     * @throws NyException any exception thrown while executing and parsing.
     */
    @CompileStatic
    String executeToJSON(String scriptName, Map<String, Object> data) throws NyException {
        Object result = execute(scriptName, data)
        if (result == null) {
            null
        } else {
            JsonOutput.toJson(result)
        }
    }

    /**
     * Executes the given script and returns the result as a json string.
     * <p>
     *     If you still want to parse the json again, use the other execute method
     *     <code>execute(String, Map)</code>.
     * </p>
     *
     * @param scriptName name of the script to run.
     * @return result as json string.
     * @throws NyException any exception thrown while executing and parsing.
     */
    @CompileStatic
    String executeToJSON(String scriptName) throws NyException {
        executeToJSON(scriptName, [:])
    }

    /**
     * Programmatically (using API) do some sequence of operations inside a transaction. This would
     * be useful, specially if you don't want to script your transaction logic externally. In case
     * of an exception, transaction will be rollback automatically, but will throw the exception.
     *
     * Always use the provided nyql instance to execute scripts at all.
     *
     * @param transactionName a unique id for this executing transaction.
     * @param body the content of transaction.
     * @param parameter data for the transaction content.
     * @param autoCommit should do auto commit
     * @throws NyException any exception thrown while transaction.
     */
    @CompileStatic
    <T> T doTransaction(String transactionName, BiFunction<NyQLInstance, Map<String, Object>, T> body,
                        Map<String, Object> data, boolean autoCommit) throws NyException {
        QSession qSession = QSession.create(configurations, transactionName)
        try {
            qSession.executor.startTransaction()
            T result = body.apply(this, data)
            if (autoCommit) {
                qSession.executor.commit()
            }
            result

        } catch (Exception ex) {
            qSession.executor.rollback(null)
            throw new NyException("An exception occurred inside transaction '$transactionName'!", ex)
        } finally {
            qSession.executor.done()
        }
    }

    // *******************************************************************
    //          JMX Methods
    // *******************************************************************

    @Override
    String getName() {
        return configurations.getName()
    }

    @Override
    String executeToJSON(String scriptName, String dataJson) {
        Map<String, Object> jsonMap = (Map<String, Object>) new JsonSlurper().parseText(dataJson)
        executeToJSON(scriptName, jsonMap)
    }

    @Override
    String parseScript(String scriptName, String dataJson) {
        Map<String, Object> jsonMap = (Map<String, Object>) new JsonSlurper().parseText(dataJson)
        def script = parse(scriptName, jsonMap)
        return script.toString()
    }

    @Override
    void recompile(String scriptName) {
        recompileScript(scriptName)
    }
}
