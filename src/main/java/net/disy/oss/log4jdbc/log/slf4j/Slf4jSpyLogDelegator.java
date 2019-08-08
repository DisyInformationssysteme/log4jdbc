/**
 * Copyright 2007-2012 Arthur Blake
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.log4jdbc.log.slf4j;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.StringTokenizer;
import java.util.regex.Pattern;


import net.sf.log4jdbc.Properties;
import net.sf.log4jdbc.log.SpyLogDelegator;
import net.sf.log4jdbc.sql.Spy;
import net.sf.log4jdbc.sql.jdbcapi.ConnectionSpy;
import net.sf.log4jdbc.sql.jdbcapi.ResultSetSpy;
import net.sf.log4jdbc.sql.resultsetcollector.ResultSetCollector;
import net.sf.log4jdbc.sql.resultsetcollector.ResultSetCollectorPrinter;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Delegates JDBC spy logging events to the the Simple Logging Facade for Java (slf4j).
 * <p>
 * Modifications for log4j2: 
 * <ul>
 * <li>Modification of the signature of the method <code>connectionOpened(Spy)</code> into 
 * <code>connectionOpened(Spy, long)</code>, to accept a parameter <code>execTime</code>, 
 * defining the time elapsed to open the connection in ms. This new method simply delegates 
 * to the formerly existing method, now private, so that the behavior of the slf4j logger is not modified. 
 * See <code>SpyLogDelegator</code> for more details. 
 * <li>Modification of the signature of the method <code>connectionClosed(Spy)</code> into 
 * <code>connectionClosed(Spy, long)</code>, to accept a parameter <code>execTime</code>, 
 * defining the time elapsed to open the connection in ms. This new method simply delegates 
 * to the formerly existing method, now private, so that the behavior of the slf4j logger is not modified. 
 * See <code>SpyLogDelegator</code> for more details. 
 * </ul>
 *
 * @author Arthur Blake
 * @author Frederic Bastian
 * @author Mathieu Seppey
 */
public class Slf4jSpyLogDelegator implements SpyLogDelegator
{
    /**
     * Create a SpyLogDelegator specific to the Simple Logging Facade for Java (slf4j).
     */
    public Slf4jSpyLogDelegator()
    {
    }

    // logs for sql and jdbc

    /**
     * Logger that shows all JDBC calls on INFO level (exception ResultSet calls)
     */
    private final Logger jdbcLogger = LoggerFactory.getLogger("jdbc.audit");

    /**
     * Logger that shows JDBC calls for ResultSet operations
     */
    private final Logger resultSetLogger = LoggerFactory.getLogger("jdbc.resultset");

    /**
     * Logger that shows only the SQL that is occuring
     */
    private final Logger sqlOnlyLogger = LoggerFactory.getLogger("jdbc.sqlonly");

    /**
     * Logger that shows the SQL timing, post execution
     */
    private final Logger sqlTimingLogger = LoggerFactory.getLogger("jdbc.sqltiming");

    /**
     * Logger that shows connection open and close events as well as current number
     * of open connections.
     */
    private final Logger connectionLogger = LoggerFactory.getLogger("jdbc.connection");

    // admin/setup logging for log4jdbc.

    /**
     * Logger just for debugging things within log4jdbc itself (admin, setup, etc.)
     */
    private final Logger debugLogger = LoggerFactory.getLogger("log4jdbc.debug");

    /**
     * Logger that shows the forward scrolled result sets in a table
     */
    private final Logger resultSetTableLogger = LoggerFactory.getLogger("jdbc.resultsettable");  

    /**
     * Determine if any of the 5 log4jdbc spy loggers are turned on (jdbc.audit | jdbc.resultset |
     * jdbc.sqlonly | jdbc.sqltiming | jdbc.connection)
     *
     * @return true if any of the 5 spy jdbc/sql loggers are enabled at debug info or error level.
     */
    @Override
    public boolean isJdbcLoggingEnabled()
    {
        return jdbcLogger.isErrorEnabled() || resultSetLogger.isErrorEnabled() || sqlOnlyLogger.isErrorEnabled() ||
                sqlTimingLogger.isErrorEnabled() || connectionLogger.isErrorEnabled();
    }


    @Override
    public void exceptionOccured(Spy spy, String methodCall, Exception e, String sql, long execTime)
    {
        String classType = spy.getClassType();
        Integer spyNo = spy.getConnectionNumber();
        String header = spyNo + ". " + classType + "." + methodCall;
        if (sql == null)
        {
            jdbcLogger.error(header, e);
            sqlOnlyLogger.error(header, e);
            sqlTimingLogger.error(header, e);
        }
        else
        {
            sql = processSql(sql);
            jdbcLogger.error(header + " " + sql, e);

            // if at debug level, display debug info to error log
            if (sqlOnlyLogger.isDebugEnabled())
            {
                sqlOnlyLogger.error(getDebugInfo() + nl + spyNo + ". " + sql, e);
            }
            else
            {
                sqlOnlyLogger.error(header + " " + sql, e);
            }

            // if at debug level, display debug info to error log
            if (sqlTimingLogger.isDebugEnabled())
            {
                sqlTimingLogger.error(getDebugInfo() + nl + spyNo + ". " + sql + " {FAILED after " + execTime + " msec}", e);
            }
            else
            {
                sqlTimingLogger.error(header + " FAILED! " + sql + " {FAILED after " + execTime + " msec}", e);
            }
        }
    }

    @Override
    public void methodReturned(Spy spy, String methodCall, String returnMsg)
    {
        String classType = spy.getClassType();
        Logger logger=ResultSetSpy.classTypeDescription.equals(classType)?
                resultSetLogger:jdbcLogger;
        if (logger.isInfoEnabled())
        {
            String header = spy.getConnectionNumber() + ". " + classType + "." +
                    methodCall + " returned " + returnMsg;
            if (logger.isDebugEnabled())
            {
                logger.debug(header + " " + getDebugInfo());
            }
            else
            {
                logger.info(header);
            }
        }
    }	

    @Override
    public void constructorReturned(Spy spy, String constructionInfo)
    {
        // not used in this implementation -- yet
    }

    private static String nl = System.getProperty("line.separator");

    /**
     * Determine if the given sql should be logged or not
     * based on the various DumpSqlXXXXXX flags.
     *
     * @param sql SQL to test.
     * @return true if the SQL should be logged, false if not.
     */
    private boolean shouldSqlBeLogged(String sql)
    {
        if (sql == null)
        {
            return false;
        }
        sql = sql.trim();

        if (sql.length()<6)
        {
            return false;
        }
        sql = sql.substring(0,6).toLowerCase();
        return
                (Properties.isDumpSqlSelect() && "select".equals(sql)) ||
                (Properties.isDumpSqlInsert() && "insert".equals(sql)) ||
                (Properties.isDumpSqlUpdate() && "update".equals(sql)) ||
                (Properties.isDumpSqlDelete() && "delete".equals(sql)) ||
                (Properties.isDumpSqlCreate() && "create".equals(sql));
    }

    @Override
    public void sqlOccurred(Spy spy, String methodCall, String sql)
    {
        if (!Properties.isDumpSqlFilteringOn() || shouldSqlBeLogged(sql))
        {
            if (sqlOnlyLogger.isDebugEnabled())
            {
                sqlOnlyLogger.debug(getDebugInfo() + nl + spy.getConnectionNumber() +
                        ". " + processSql(sql));
            }
            else if (sqlOnlyLogger.isInfoEnabled())
            {
                sqlOnlyLogger.info(processSql(sql));
            }
        }
    }

    /**
     * Break an SQL statement up into multiple lines in an attempt to make it
     * more readable
     *
     * @param sql SQL to break up.
     * @return SQL broken up into multiple lines
     */
    private String processSql(String sql)
    {
        if (sql==null)
        {
            return null;
        }

        if (Properties.isSqlTrim())
        {
            sql = sql.trim();
        }

        StringBuilder output = new StringBuilder();

        if (Properties.getDumpSqlMaxLineLength() <= 0)
        {
            output.append(sql);
        }
        else
        {
            // insert line breaks into sql to make it more readable
            StringTokenizer st = new StringTokenizer(sql);
            String token;
            int linelength = 0;

            while (st.hasMoreElements())
            {
                token = (String) st.nextElement();

                output.append(token);
                linelength += token.length();
                output.append(" ");
                linelength++;
                if (linelength > Properties.getDumpSqlMaxLineLength())
                {
                    output.append(nl);
                    linelength = 0;
                }
            }
        }

        if (Properties.isDumpSqlAddSemicolon())
        {
            output.append(";");
        }

        String stringOutput = output.toString();

        if (Properties.isTrimExtraBlankLinesInSql())
        {
            LineNumberReader lineReader = new LineNumberReader(new StringReader(stringOutput));

            output = new StringBuilder();

            int contiguousBlankLines = 0;
            try
            {
                while (true)
                {
                    String line = lineReader.readLine();
                    if (line==null)
                    {
                        break;
                    }

                    // is this line blank?
                    if (line.trim().length() == 0)
                    {
                        contiguousBlankLines ++;
                        // skip contiguous blank lines
                        if (contiguousBlankLines > 1)
                        {
                            continue;
                        }
                    }
                    else
                    {
                        contiguousBlankLines = 0;
                        output.append(line);
                    }
                    output.append(nl);
                }
            }
            catch (IOException e)
            {
                // since we are reading from a buffer, this isn't likely to happen,
                // but if it does we just ignore it and treat it like its the end of the stream
            }
            stringOutput = output.toString();
        }

        return stringOutput;
    }

    /**
     * Special call that is called only for JDBC method calls that contain SQL.
     *
     * @param spy        the Spy wrapping the class where the SQL occurred.
     *
     * @param execTime   how long it took the SQL to run, in milliseconds.
     *
     * @param methodCall a description of the name and call parameters of the
     *                   method that generated the SQL.
     *
     * @param sql        SQL that occurred.
     */
    @Override
    public void sqlTimingOccurred(Spy spy, long execTime, String methodCall, String sql)
    {
        if (sqlTimingLogger.isErrorEnabled() &&
                (!Properties.isDumpSqlFilteringOn() || shouldSqlBeLogged(sql)))
        {
            if (Properties.isSqlTimingErrorThresholdEnabled() &&
                    execTime >= Properties.getSqlTimingErrorThresholdMsec())
            {
                sqlTimingLogger.error(
                        buildSqlTimingDump(spy, execTime, methodCall, sql, sqlTimingLogger.isDebugEnabled()));
            }
            else if (sqlTimingLogger.isWarnEnabled())
            {
                if (Properties.isSqlTimingWarnThresholdEnabled() &&
                        execTime >= Properties.getSqlTimingWarnThresholdMsec())
                {
                    sqlTimingLogger.warn(
                            buildSqlTimingDump(spy, execTime, methodCall, sql, sqlTimingLogger.isDebugEnabled()));
                }
                else if (sqlTimingLogger.isDebugEnabled())
                {
                    sqlTimingLogger.debug(
                            buildSqlTimingDump(spy, execTime, methodCall, sql, true));
                }
                else if (sqlTimingLogger.isInfoEnabled())
                {
                    sqlTimingLogger.info(
                            buildSqlTimingDump(spy, execTime, methodCall, sql, false));
                }
            }
        }
    }

    /**
     * Helper method to quickly build a SQL timing dump output String for
     * logging.
     *
     * @param spy        the Spy wrapping the class where the SQL occurred.
     *
     * @param execTime   how long it took the SQL to run, in milliseconds.
     *
     * @param methodCall a description of the name and call parameters of the
     *                   method that generated the SQL.
     *
     * @param sql        SQL that occurred.
     *
     * @param debugInfo  if true, include debug info at the front of the output.
     *
     * @return a SQL timing dump String for logging.
     */
    private String buildSqlTimingDump(Spy spy, long execTime, String methodCall,
            String sql, boolean debugInfo)
    {
        StringBuffer out = new StringBuffer();

        if (debugInfo)
        {
            out.append(getDebugInfo());
            out.append(nl);
            out.append(spy.getConnectionNumber());
            out.append(". ");
        }

        // NOTE: if both sql dump and sql timing dump are on, the processSql
        // algorithm will run TWICE once at the beginning and once at the end
        // this is not very efficient but usually
        // only one or the other dump should be on and not both.

        sql = processSql(sql);

        out.append(sql);
        out.append(" {executed in ");
        out.append(execTime);
        out.append(" msec}");

        return out.toString();
    }

    /**
     * Get debugging info - the module and line number that called the logger
     * version that prints the stack trace information from the point just before
     * we got it (net.sf.log4jdbc)
     *
     * if the optional log4jdbc.debug.stack.prefix system property is defined then
     * the last call point from an application is shown in the debug
     * trace output, instead of the last direct caller into log4jdbc
     *
     * @return debugging info for whoever called into JDBC from within the application.
     */
    private static String getDebugInfo()
    {
        Throwable t = new Throwable();
        t.fillInStackTrace();

        StackTraceElement[] stackTrace = t.getStackTrace();

        if (stackTrace != null)
        {
            String className;

            StringBuffer dump = new StringBuffer();

            /**
             * The DumpFullDebugStackTrace option is useful in some situations when
             * we want to see the full stack trace in the debug info-  watch out
             * though as this will make the logs HUGE!
             */
            if (Properties.isDumpFullDebugStackTrace())
            {
                boolean first=true;
                for (int i = 0; i < stackTrace.length; i++)
                {
                    className = stackTrace[i].getClassName();
                    if (!className.startsWith("net.sf.log4jdbc"))
                    {
                        if (first)
                        {
                            first = false;
                        }
                        else
                        {
                            dump.append("  ");
                        }
                        dump.append("at ");
                        dump.append(stackTrace[i]);
                        dump.append(nl);
                    }
                }
            }
            else
            {
                dump.append(" ");
                int firstLog4jdbcCall = 0;
                int lastApplicationCall = 0;

                for (int i = 0; i < stackTrace.length; i++)
                {
                    className = stackTrace[i].getClassName();
                    if (className.startsWith("net.sf.log4jdbc"))
                    {
                        firstLog4jdbcCall = i;
                    }
                    else if (Properties.isTraceFromApplication() &&
                    		Pattern.matches(Properties.getDebugStackPrefix(), className))
                    {
                        lastApplicationCall = i;
                        break;
                    }
                }
                int j = lastApplicationCall;

                if (j == 0)  // if app not found, then use whoever was the last guy that called a log4jdbc class.
                {
                    j = 1 + firstLog4jdbcCall;
                }

                dump.append(stackTrace[j].getClassName()).append(".").append(stackTrace[j].getMethodName()).append("(").
                append(stackTrace[j].getFileName()).append(":").append(stackTrace[j].getLineNumber()).append(")");
            }

            return dump.toString();
        }
        return null;
    }

    @Override
    public void debug(String msg)
    {
        debugLogger.debug(msg);
    }

    @Override
    public void connectionOpened(Spy spy, long execTime)
    {
        //we just delegate to the already existing method, 
        //so that we do not change the behavior of the standard implementation
        this.connectionOpened(spy);
    }

    /**
     * Called whenever a new connection spy is created.
     *
     * @param spy ConnectionSpy that was created.
     */
    private void connectionOpened(Spy spy)
    {
        if (connectionLogger.isDebugEnabled())
        {		  
            connectionLogger.info(spy.getConnectionNumber() + ". Connection opened " +
                    getDebugInfo());
            connectionLogger.debug(ConnectionSpy.getOpenConnectionsDump());
        }
        else
        {
            connectionLogger.info(spy.getConnectionNumber() + ". Connection opened");
        }
    }

    @Override
    public void connectionClosed(Spy spy, long execTime)
    {
        //we just delegate to the already existing method, 
        //so that we do not change the behavior of the standard implementation
        this.connectionClosed(spy);
    }

    /**
     * Called whenever a connection spy is closed.
     *
     * @param spy ConnectionSpy that was closed.
     */
    private void connectionClosed(Spy spy)
    {
        if (connectionLogger.isDebugEnabled())
        {
            connectionLogger.info(spy.getConnectionNumber() + ". Connection closed " +
                    getDebugInfo());
            connectionLogger.debug(ConnectionSpy.getOpenConnectionsDump());
        }
        else
        {
            connectionLogger.info(spy.getConnectionNumber() + ". Connection closed");
        }
    }

    @Override
    public void connectionAborted(Spy spy, long execTime)
    {
        this.connectionAborted(spy);
    }

    /**
     * Called whenever a connection spy is aborted.
     *
     * @param spy ConnectionSpy that was aborted.
     */
    private void connectionAborted(Spy spy)
    {
        if (connectionLogger.isDebugEnabled())
        {
            connectionLogger.info(spy.getConnectionNumber() + ". Connection aborted " +
                    getDebugInfo());
            connectionLogger.debug(ConnectionSpy.getOpenConnectionsDump());
        }
        else
        {
            connectionLogger.info(spy.getConnectionNumber() + ". Connection aborted");
        }
    }

    @Override
    public boolean isResultSetCollectionEnabled() {
        return resultSetTableLogger.isInfoEnabled();
    }

    @Override
    public boolean isResultSetCollectionEnabledWithUnreadValueFillIn() {
        return resultSetTableLogger.isDebugEnabled();
    }

    @Override
    public void resultSetCollected(ResultSetCollector resultSetCollector) {
        String resultsToPrint = new ResultSetCollectorPrinter().getResultSetToPrint(resultSetCollector);    
        resultSetTableLogger.info(resultsToPrint);
    }
}
