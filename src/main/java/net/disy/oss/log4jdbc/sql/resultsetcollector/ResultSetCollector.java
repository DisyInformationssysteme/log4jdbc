/**
 * Copyright 2010 Tim Azzopardi
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
package net.sf.log4jdbc.sql.resultsetcollector;

import java.sql.ResultSet;
import java.util.List;

import net.sf.log4jdbc.sql.jdbcapi.ResultSetSpy;

/**
 * Collect a result set, ultimately available from getRow().
 * A ResultSetSpy instance may call a ResultSetCollector instance's methodReturned and preMethod
 * as and when appropriate. The ResultSetCollector is then expected to build a simple representation 
 * of the rows and columns in getRow()/getColumnCount()/getColumnName().
 * @author Tim Azzopardi
 */
public interface ResultSetCollector {

  /**
   * Expected to be called by a ResultSetSpy for all jdbc methods.
   * @return true if the result set is complete (next() returns false)
   */
  public boolean methodReturned(ResultSetSpy resultSetSpy,
          String methodCall, Object returnValue, Object targetObject,
          Object... methodParams);

  /**
   * Expected to be called by a ResultSetSpy for prior to the execution of all jdbc methods.
   */
  public void preMethod(ResultSetSpy resultSetSpy, String methodCall, Object... methodParams);
  
  /**
   * @return the result set objects
   */
  public List<List<Object>> getRows();

  /**
   * @return the result set column count
   */
  public int getColumnCount();

  /**
   * @return the result set column name for a given column number previously obtained 
   * from the <code>ResultSetMetaData</code>. Index starts from 1.
   * @column 	An <code>int</code> representing the index of the column for which 
   * 			the name will be returned. Index starts from 1. 
   */
  public String getColumnName(int column);

  /**
   * Clear the result set so far.
   */
  public void reset();
  
  /**
   * Allow this <code>ResultSetCollector</code> to obtain a <code>ResultSetMetaData</code> 
   * from the real underlying JDBC <code>ResultSet</code>, and obtains immediately 
   * from it the column count, the column names and column labels. This information 
   * needs to be stored, to be later used by the methods <code>getColumnCount</code> and 
   * <code>getColumnName</code>, when this <code>ResultSetCollector</code> is printed. 
   * It is not possible to simply store the <code>ResultSetMetaData</code>, 
   * as the <code>ResultSet</code> might be closed at the time of printing, and some drivers 
   * do not support to query the <code>ResultSetMetaData</code> when the <code>ResultSet</code> 
   * is closed.
   * <p>
   * The <code>ResultSetMetaData</code> should be requested and the data obtained 
   * by this method only once, so a control needs to be done before obtaining 
   * the <code>ResultSetMetaData</code>. This method should also first check 
   * if <code>rs</code> is not already closed. 
   * This methods is usually called under the hood by the <code>ResultSetCollector</code> 
   * itself, but it might by required to manually load the <code>ResultSetMetaData</code> 
   * if, for instance, the real <code>ResultSet</code> is about to be closed before 
   * the <code>ResultSetCollector</code> requested for the <code>ResultSetMetaData</code> 
   * (for instance, if the method <code>next</code> was not previously called). 
   * 
   * @param rs 		The real JDBC <code>ResultSet</code> that was wrapped into 
   * 				a <code>ResultSetSpy</code>.
   */
  public void loadMetaDataIfNeeded(ResultSet rs);

}