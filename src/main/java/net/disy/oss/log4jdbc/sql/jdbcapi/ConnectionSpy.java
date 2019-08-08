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
package net.sf.log4jdbc.sql.jdbcapi;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.SQLClientInfoException;
import java.sql.SQLXML;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;

import net.sf.log4jdbc.log.SpyLogDelegator;
import net.sf.log4jdbc.sql.Spy;
import net.sf.log4jdbc.sql.rdbmsspecifics.RdbmsSpecifics;

/**
 * Wraps a JDBC Connection and reports method calls, returns and exceptions.
 *
 * This version is for jdbc 4.
 * <p>
 * <h3>Modifications for log4j2: </h3>
 * <ul>
 * <li>Addition of new constructors, to accept a parameter <code>execTime</code>, 
 * a <code>long</code> defining the time elapsed to open the connection in ms. 
 * (see <code>SpyLogDelegator#connectionOpened(Spy, long)</code> for more details, 
 * and modifications in <code>DriverSpy#connect(String, Properties)</code>).
 * <li>Modification of the method <code>close()</code> in order to compute 
 * execution time to close the connection (see <code>SpyLogDelegator#connectionClosed(Spy, long), 
 * or before an <code>Exception</code> is thrown if a problem occurs. </code>)
 * <li>Addition of a new method <code>ConnectionSpy#reportException(String, SQLException, long)</code> 
 * to log execution time before an <code>Exception</code> is thrown when the connection closing failed. 
 * </ul>
 *
 * @author Arthur Blake
 * @author Frederic Bastian
 * @author Mathieu Seppey
 */
public class ConnectionSpy implements Connection, Spy
{
  private Connection realConnection;

  /**
   * Get the real underlying Connection that this ConnectionSpy wraps.
   *
   * @return the real underlying Connection.
   */
  public Connection getRealConnection()
  {
    return realConnection;
  }

  private SpyLogDelegator log;

  private final Integer connectionNumber;
  private static int lastConnectionNumber = 0;

  /**
   * Contains a Mapping of connectionNumber to currently open ConnectionSpy
   * objects.
   */
  private static final Map<Integer, ConnectionSpy> connectionTracker = 
		  new HashMap<Integer, ConnectionSpy>();

  /**
   * Get a dump of how many connections are open, and which connection numbers
   * are open.
   *
   * @return an open connection dump.
   */
  public static String getOpenConnectionsDump()
  {
    StringBuffer dump = new StringBuffer();
    int size;
    Integer[] keysArr;
    synchronized (connectionTracker)
    {
      size = connectionTracker.size();
      if (size==0)
      {
        return "open connections:  none";
      }
      Set<Integer> keys = connectionTracker.keySet();
      keysArr = keys.toArray(new Integer[keys.size()]);
    }

    Arrays.sort(keysArr);

    dump.append("open connections:  ");
    for (int i=0; i < keysArr.length; i++)
    {
      dump.append(keysArr[i]);
      dump.append(" ");
    }

    dump.append("(");
    dump.append(size);
    dump.append(")");
    return dump.toString();
  }

  /**
   * Create a new ConnectionSpy that wraps a given Connection.
   *
   * @param realConnection &quot;real&quot; Connection that this ConnectionSpy wraps.
   * @param logDelegator 	The <code>SpyLogDelegator</code> used by 
   * 						this <code>ConnectionSpy</code> and all resources obtained from it 
   * 						(<code>StatementSpy</code>s, ...)
   */
  public ConnectionSpy(Connection realConnection, SpyLogDelegator logDelegator)
  {
    this(realConnection, DriverSpy.defaultRdbmsSpecifics, logDelegator);
  }

  /**
   * Create a new ConnectionSpy that wraps a given Connection.
   *
   * @param realConnection &quot;real&quot; Connection that this ConnectionSpy wraps.
   * @param execTime 	a <code>long</code> defining the time in ms 
   * 					taken to open the connection to <code>realConnection</code>. 
   * @param logDelegator 	The <code>SpyLogDelegator</code> used by 
   * 						this <code>ConnectionSpy</code> and all resources obtained from it 
   * 						(<code>StatementSpy</code>s, ...)
   */
  public ConnectionSpy(Connection realConnection, long execTime, SpyLogDelegator logDelegator)
  {
    this(realConnection, null, execTime, logDelegator);
  }

  /**
   * Create a new ConnectionSpy that wraps a given Connection.
   *
   * @param realConnection &quot;real&quot; Connection that this ConnectionSpy wraps.
   * @param rdbmsSpecifics the RdbmsSpecifics object for formatting logging appropriate for the Rdbms used.
   * @param logDelegator 	The <code>SpyLogDelegator</code> used by 
   * 						this <code>ConnectionSpy</code> and all resources obtained from it 
   * 						(<code>StatementSpy</code>s, ...)
   */
  public ConnectionSpy(Connection realConnection, RdbmsSpecifics rdbmsSpecifics, 
		  SpyLogDelegator logDelegator)
  {
    this(realConnection, rdbmsSpecifics, -1L, logDelegator);
  }

  /**
   * Create a new ConnectionSpy that wraps a given Connection.
   *
   * @param realConnection &quot;real&quot; Connection that this ConnectionSpy wraps.
   * @param rdbmsSpecifics the RdbmsSpecifics object for formatting logging appropriate for the Rdbms used.
   * @param execTime 	a <code>long</code> defining the time in ms 
   * 					taken to open the connection to <code>realConnection</code>. 
   * 					Should be equals to -1 if not used. 
   * @param logDelegator 	The <code>SpyLogDelegator</code> used by 
   * 						this <code>ConnectionSpy</code> and all resources obtained from it 
   * 						(<code>StatementSpy</code>s, ...)
   */
  public ConnectionSpy(Connection realConnection, RdbmsSpecifics rdbmsSpecifics, 
		  long execTime, SpyLogDelegator logDelegator)
  {
    if (rdbmsSpecifics == null)
    {
      rdbmsSpecifics = DriverSpy.defaultRdbmsSpecifics;
    }
    setRdbmsSpecifics(rdbmsSpecifics);
    if (realConnection == null)
    {
      throw new IllegalArgumentException("Must pass in a non null real Connection");
    }
    this.realConnection = realConnection;
    log = logDelegator;

    synchronized (connectionTracker)
    {
      connectionNumber = new Integer(++lastConnectionNumber);
      connectionTracker.put(connectionNumber, this);
    }
    log.connectionOpened(this, execTime);
    reportReturn("new Connection");
  }

  private RdbmsSpecifics rdbmsSpecifics;

  /**
   * Set the RdbmsSpecifics object for formatting logging appropriate for the Rdbms used on this connection.
   *
   * @param rdbmsSpecifics the RdbmsSpecifics object for formatting logging appropriate for the Rdbms used.
   */
  void setRdbmsSpecifics(RdbmsSpecifics rdbmsSpecifics)
  {
    this.rdbmsSpecifics = rdbmsSpecifics;
  }

  /**
   * Get the RdbmsSpecifics object for formatting logging appropriate for the Rdbms used on this connection.
   *
   * @return the RdbmsSpecifics object for formatting logging appropriate for the Rdbms used.
   */
  RdbmsSpecifics getRdbmsSpecifics()
  {
    return rdbmsSpecifics;
  }

  public Integer getConnectionNumber()
  {
    return connectionNumber;
  }

  public String getClassType()
  {
    return "Connection";
  }

  protected void reportException(String methodCall, SQLException exception, String sql)
  {
    log.exceptionOccured(this, methodCall, exception, sql, -1L);
  }

  protected void reportException(String methodCall, SQLException exception)
  {
    log.exceptionOccured(this, methodCall, exception, null, -1L);
  }

  protected void reportException(String methodCall, SQLException exception, long execTime)
  {
    log.exceptionOccured(this, methodCall, exception, null, execTime);
  }

  protected void reportAllReturns(String methodCall, String returnValue)
  {
    log.methodReturned(this, methodCall, returnValue);
  }

  private boolean reportReturn(String methodCall, boolean value)
  {
    reportAllReturns(methodCall, "" + value);
    return value;
  }

  private int reportReturn(String methodCall, int value)
  {
    reportAllReturns(methodCall, "" + value);
    return value;
  }

  private <T> T reportReturn(String methodCall, T value)
  {
    reportAllReturns(methodCall, "" + value);
    return value;
  }

  private void reportReturn(String methodCall)
  {
    reportAllReturns(methodCall, "");
  }
  
  private void reportClosed(long execTime)
  {
    log.connectionClosed(this, execTime);
  }  
  
  private void reportAborted(long execTime)
  {
    log.connectionAborted(this, execTime);
  }     

  // forwarding methods

  @Override
  public boolean isClosed() throws SQLException
  {
    String methodCall = "isClosed()";
    try
    {
      return reportReturn(methodCall, (realConnection.isClosed()));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public SQLWarning getWarnings() throws SQLException
  {
    String methodCall = "getWarnings()";
    try
    {
      return reportReturn(methodCall, realConnection.getWarnings());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Savepoint setSavepoint() throws SQLException
  {
    String methodCall = "setSavepoint()";
    try
    {
      return reportReturn(methodCall, realConnection.setSavepoint());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException
  {
    String methodCall = "releaseSavepoint(" + savepoint + ")";
    try
    {
      realConnection.releaseSavepoint(savepoint);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException
  {
    String methodCall = "rollback(" + savepoint + ")";
    try
    {
      realConnection.rollback(savepoint);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException
  {
    String methodCall = "getMetaData()";
    try
    {
      return reportReturn(methodCall, realConnection.getMetaData());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void clearWarnings() throws SQLException
  {
    String methodCall = "clearWarnings()";
    try
    {
      realConnection.clearWarnings();
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public Statement createStatement() throws SQLException
  {
    String methodCall = "createStatement()";
    try
    {
      Statement statement = realConnection.createStatement();
      return reportReturn(methodCall, new StatementSpy(this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException
  {
    String methodCall = "createStatement(" + resultSetType + ", " + resultSetConcurrency + ")";
    try
    {
      Statement statement = realConnection.createStatement(resultSetType, resultSetConcurrency);
      return reportReturn(methodCall, new StatementSpy(this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException
  {
    String methodCall = "createStatement(" + resultSetType + ", " + resultSetConcurrency + ", " + resultSetHoldability + ")";
    try
    {
      Statement statement = realConnection.createStatement(resultSetType, resultSetConcurrency,
        resultSetHoldability);
      return reportReturn(methodCall, new StatementSpy(this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException
  {
    String methodCall = "setReadOnly(" + readOnly + ")";
    try
    {
      realConnection.setReadOnly(readOnly);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException
  {
    String methodCall = "prepareStatement(" + sql + ")";
    try
    {
      PreparedStatement statement = realConnection.prepareStatement(sql);
      return reportReturn(methodCall, new PreparedStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException
  {
    String methodCall = "prepareStatement(" + sql + ", " + autoGeneratedKeys + ")";
    try
    {
      PreparedStatement statement = realConnection.prepareStatement(sql, autoGeneratedKeys);
      return reportReturn(methodCall, new PreparedStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException
  {
    String methodCall = "prepareStatement(" + sql + ", " + resultSetType + ", " + resultSetConcurrency + ")";
    try
    {
      PreparedStatement statement = realConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
      return reportReturn(methodCall, new PreparedStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                            int resultSetHoldability) throws SQLException
  {
    String methodCall = "prepareStatement(" + sql + ", " + resultSetType + ", " + resultSetConcurrency + ", " + resultSetHoldability + ")";
    try
    {
      PreparedStatement statement = realConnection.prepareStatement(sql, resultSetType, resultSetConcurrency,
        resultSetHoldability);
      return reportReturn(methodCall, new PreparedStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int columnIndexes[]) throws SQLException
  {
    //todo: dump the array here?
    String methodCall = "prepareStatement(" + sql + ", " + columnIndexes + ")";
    try
    {
      PreparedStatement statement = realConnection.prepareStatement(sql, columnIndexes);
      return reportReturn(methodCall, new PreparedStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException
  {
    String methodCall = "setSavepoint(" + name + ")";
    try
    {
      return reportReturn(methodCall, realConnection.setSavepoint(name));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String columnNames[]) throws SQLException
  {
    //todo: dump the array here?
    String methodCall = "prepareStatement(" + sql + ", " + columnNames + ")";
    try
    {
      PreparedStatement statement = realConnection.prepareStatement(sql, columnNames);
      return reportReturn(methodCall, new PreparedStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public Clob createClob() throws SQLException {
    String methodCall = "createClob()";
    try
    {
      return reportReturn(methodCall, realConnection.createClob());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Blob createBlob() throws SQLException {
    String methodCall = "createBlob()";
    try
    {
      return reportReturn(methodCall, realConnection.createBlob());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public NClob createNClob() throws SQLException {
    String methodCall = "createNClob()";
    try
    {
      return reportReturn(methodCall, realConnection.createNClob());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    String methodCall = "createSQLXML()";
    try
    {
      return reportReturn(methodCall, realConnection.createSQLXML());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    String methodCall = "isValid(" + timeout + ")";
    try
    {
      return reportReturn(methodCall,realConnection.isValid(timeout));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {
    String methodCall = "setClientInfo(" + name + ", " + value + ")";
    try
    {
      realConnection.setClientInfo(name,value);
    }
    catch (SQLClientInfoException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {
    // todo: dump properties?
    String methodCall = "setClientInfo(" + properties + ")";
    try
    {
      realConnection.setClientInfo(properties);
    }
    catch (SQLClientInfoException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    String methodCall = "getClientInfo(" + name + ")";
    try
    {
      return reportReturn(methodCall,realConnection.getClientInfo(name));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    String methodCall = "getClientInfo()";
    try
    {
      return reportReturn(methodCall,realConnection.getClientInfo());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    //todo: dump elements?
    String methodCall = "createArrayOf(" + typeName + ", " + elements +")";
    try
    {
      return reportReturn(methodCall,realConnection.createArrayOf(typeName,elements));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    //todo: dump attributes?
    String methodCall = "createStruct(" + typeName + ", " + attributes +")";
    try
    {
      return reportReturn(methodCall,realConnection.createStruct(typeName, attributes));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public boolean isReadOnly() throws SQLException
  {
    String methodCall = "isReadOnly()";
    try
    {
      return reportReturn(methodCall,realConnection.isReadOnly());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void setHoldability(int holdability) throws SQLException
  {
    String methodCall = "setHoldability(" + holdability + ")";
    try
    {
      realConnection.setHoldability(holdability);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException
  {
    String methodCall = "prepareCall(" + sql + ")";
    try
    {
      CallableStatement statement = realConnection.prepareCall(sql);
      return reportReturn(methodCall, new CallableStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException
  {
    String methodCall = "prepareCall(" + sql + ", " + resultSetType + ", " + resultSetConcurrency + ")";
    try
    {
      CallableStatement statement = realConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
      return reportReturn(methodCall, new CallableStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                       int resultSetHoldability) throws SQLException
  {
    String methodCall = "prepareCall(" + sql + ", " + resultSetType + ", " + resultSetConcurrency + ", " + resultSetHoldability + ")";
    try
    {
      CallableStatement statement = realConnection.prepareCall(sql, resultSetType, resultSetConcurrency,
        resultSetHoldability);
      return reportReturn(methodCall, new CallableStatementSpy(sql, this, statement, this.log));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public void setCatalog(String catalog) throws SQLException
  {
    String methodCall = "setCatalog(" + catalog + ")";
    try
    {
      realConnection.setCatalog(catalog);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public String nativeSQL(String sql) throws SQLException
  {
    String methodCall = "nativeSQL(" + sql + ")";
    try
    {
      return reportReturn(methodCall, realConnection.nativeSQL(sql));
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, sql);
      throw s;
    }
  }

  @Override
  public Map<String,Class<?>> getTypeMap() throws SQLException
  {
    String methodCall = "getTypeMap()";
    try
    {
      return reportReturn(methodCall, realConnection.getTypeMap());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException
  {
    String methodCall = "setAutoCommit(" + autoCommit + ")";
    try
    {
      realConnection.setAutoCommit(autoCommit);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public String getCatalog() throws SQLException
  {
    String methodCall = "getCatalog()";
    try
    {
      return reportReturn(methodCall, realConnection.getCatalog());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void setTypeMap(java.util.Map<String,Class<?>> map) throws SQLException
  {
    //todo: dump map??
    String methodCall = "setTypeMap(" + map + ")";
    try
    {
      realConnection.setTypeMap(map);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException
  {
    String methodCall = "setTransactionIsolation(" + level + ")";
    try
    {
      realConnection.setTransactionIsolation(level);
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public boolean getAutoCommit() throws SQLException
  {
    String methodCall = "getAutoCommit()";
    try
    {
      return reportReturn(methodCall, realConnection.getAutoCommit());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public int getHoldability() throws SQLException
  {
    String methodCall = "getHoldability()";
    try
    {
      return reportReturn(methodCall, realConnection.getHoldability());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public int getTransactionIsolation() throws SQLException
  {
    String methodCall = "getTransactionIsolation()";
    try
    {
      return reportReturn(methodCall, realConnection.getTransactionIsolation());
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
  }

  @Override
  public void commit() throws SQLException
  {
    String methodCall = "commit()";
    try
    {
      realConnection.commit();
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public void rollback() throws SQLException
  {
    String methodCall = "rollback()";
    try
    {
      realConnection.rollback();
    }
    catch (SQLException s)
    {
      reportException(methodCall, s);
      throw s;
    }
    reportReturn(methodCall);
  }

  @Override
  public void close() throws SQLException
  {
    String methodCall = "close()";
    long tstart = System.currentTimeMillis();
    try
    {
      realConnection.close();
    }
    catch (SQLException s)
    {
      reportException(methodCall, s, System.currentTimeMillis() - tstart);
      throw s;
    }
    finally
    {
      synchronized (connectionTracker)
      {
        connectionTracker.remove(connectionNumber);
      }
      reportClosed(System.currentTimeMillis() - tstart);
    }
    reportReturn(methodCall);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    String methodCall = "unwrap(" + (iface==null?"null":iface.getName()) + ")";
    try
    {
      //todo: double check this logic
      return (T)reportReturn(methodCall, (iface != null && (iface == Connection.class || iface == Spy.class))?(T)this:realConnection.unwrap(iface));
    }
    catch (SQLException s)
    {
      reportException(methodCall,s);
      throw s;
    }
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException
  {
    String methodCall = "isWrapperFor(" + (iface==null?"null":iface.getName()) + ")";
    try
    {
      return reportReturn(methodCall, (iface != null && (iface == Connection.class || iface == Spy.class)) ||
          realConnection.isWrapperFor(iface));
    }
    catch (SQLException s)
    {
      reportException(methodCall,s);
      throw s;
    }
  }

  @Override
	public void setSchema(String schema) throws SQLException
	{
		String methodCall = "setSchema(" + schema + ")";
		try
		{
			realConnection.setSchema(schema);	
		}
		catch (SQLException s)
		{
			reportException(methodCall,s);
			throw s;			
		}
	}
	
  @Override	
	public String getSchema() throws SQLException
	{
		String methodCall = "getSchema()";
		try
		{
			return reportReturn(methodCall,realConnection.getSchema());	
		}
		catch (SQLException s)
		{
			reportException(methodCall,s);
			throw s;			
		}
	}
	
  @Override	
	public void abort(Executor executor) throws SQLException
	{
		String methodCall = "abort(" + executor + ")";
		long tstart = System.currentTimeMillis();
		try
		{
			realConnection.abort(executor);	
			reportAborted(System.currentTimeMillis() - tstart);
		}
		catch (SQLException s)
		{
		    reportException(methodCall, s, System.currentTimeMillis() - tstart);
			throw s;			
		}
	}

  @Override	
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException
	{
		String methodCall = "setNetworkTimeout(" + executor + ", " + milliseconds + ";";
		try
		{
			realConnection.setNetworkTimeout(executor,milliseconds);	
		}
		catch (SQLException s)
		{
			reportException(methodCall,s);
			throw s;			
		}
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		String methodCall = "getNetworkTimeout()";
		try
		{
			return reportReturn(methodCall, realConnection.getNetworkTimeout());	
		}
		catch (SQLException s)
		{
			reportException(methodCall,s);
			throw s;			
		}
	}
}