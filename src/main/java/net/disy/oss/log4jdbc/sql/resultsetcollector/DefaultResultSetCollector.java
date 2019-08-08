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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.log4jdbc.sql.jdbcapi.ResultSetSpy;


public class DefaultResultSetCollector implements ResultSetCollector {

  private static final String NULL_RESULT_SET_VAL = "[null]";
  private static final String UNREAD = "[unread]";
  private static final String UNREAD_ERROR = "[unread!]";
  private boolean fillInUnreadValues = false;

  public DefaultResultSetCollector(boolean fillInUnreadValues) {
	  this.reset();
      this.fillInUnreadValues = fillInUnreadValues;
      this.lastValueReturnedByNext = true;
  }
  /**
   * A {@code boolean} defining whether parameters of this {@code DefaultResultSetColector} 
   * has already been obtained from a {@code ResultSetMetaData}.
   */
  private boolean loaded;
  /**
   * An <code>int</code> representing the number of columns 
   * in the real <code>ResultSet</code> object, obtained by calling 
   * <code>getColumnCount</code> on the related <code>ResultSetMetaData</code> object.
   * This attribute is an <code>Integer</code> rather than an <code>int</code>, 
   * so that we can distinguish the case where the column count has not yet been obtained, 
   * from the case when the column count is 0 (should never happend, but still...)
   */
  private int columnCount;
  /**
   * A <code>Map</code> where the key is an <code>Integer</code> representing 
   * the index of a column, and the corresponding value is a <code>String</code> 
   * being the label of the column. 
   * This <code>Map</code> is populated by calling <code>getColumnLabel</code> 
   * on the <code>ResultSetMetaData</code> object of the real <code>ResultSet</code> object, 
   * for each column (see {@link #columnCount}).
   */
  private Map<Integer, String> columnLabels;
  /**
   * A <code>Map</code> where the key is an <code>Integer</code> representing 
   * the index of a column, and the corresponding value is a <code>String</code> 
   * being the name of the column. 
   * This <code>Map</code> is populated by calling <code>getColumnLabel</code> 
   * on the <code>ResultSetMetaData</code> object of the real <code>ResultSet</code> object, 
   * for each column (see {@link #columnCount}).
   */
  private Map<Integer, String> columnNames;
  /**
   * A <code>boolean</code> that is the last value returned by a call 
   * to <code>next</code> (or <code>first</code>) on the related <code>ResultSet</code>. 
   * This allows to know if this <code>ResultSetCollector</code> must be printed 
   * when <code>close</code> is called on the related <code>ResultSet</code>: 
   * if the previous call to <code>next</code> returned <code>true</code>, 
   * then this <code>ResultSetCollector</code> must be printed 
   * on the call to <code>close</code>; otherwise, it was already printed following 
   * the previous call to <code>next</code>, and should not be printed 
   * on the call to <code>close</code>.
   * <p>
   * This attribute must not be reset after a printing (the other attributes are, through a call 
   * to <code>reset</code>), in case the related <code>ResultSet</code> is reused. 
   * <p>
   * Default value at initialization is <code>true</code> (<code>ResultSetCollector</code> 
   * not printed).
   */
  private boolean lastValueReturnedByNext;
  private List<Object> row;
  private List<List<Object>> rows;
  private Map<String, Integer> colNameToColIndex;
  private int colIndex; 
  private static final List<String> GETTERS = Arrays.asList(new String[] { "getString", "getLong", "getInt", "getDate", "getTimestamp", "getTime",
      "getBigDecimal", "getFloat", "getDouble", "getByte", "getShort", "getObject", "getBoolean", });

  public List<List<Object>> getRows() {
    return rows;
  }

  public int getColumnCount() {
    return columnCount;
  }

  public void reset() {
    loaded = false;
    rows = null;
    row = null;
    colNameToColIndex = null;
    colIndex = -1;// Useful for wasNull calls
    columnCount = 0;
    columnLabels = new HashMap<Integer, String>();
    columnNames = new HashMap<Integer, String>();
  }

  @Override
  public void loadMetaDataIfNeeded(ResultSet rs) {
	  //if data already loaded
	  if (this.loaded) {
		  return;
	  }
	  //otherwise, get all data now, so that we don't need 
	  //to use the ResultSetMetaData later (with some drivers, 
	  //it cannot be used once the ResultSet has been closed)
      try {
    	  if (!rs.isClosed()) {
    		  this.loadMetaDataIfNeeded(rs.getMetaData());
    	  }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
      this.loaded = true;
  }
  
  /**
   * Perform the operations requested by {@link #loadMetaDataIfNeeded(ResultSet)}, 
   * using directly the related <code>ResultSetMetaData</code>.
   * @param metaData 	The <code>ResultSetMetaData</code> through which the information 
   * 					needed by {@link #loadMetaDataIfNeeded(ResultSet)} are obtained. 
   */
  private void loadMetaDataIfNeeded(ResultSetMetaData metaData)
  {
	  //if data already loaded
	  if (this.loaded) {
		  return;
	  }
	  //otherwise, get all data now, so that we don't need 
	  //to use the ResultSetMetaData later (with some drivers, 
	  //it cannot be used once the ResultSet has been closed)
      try {
          if (metaData == null) {
              this.columnCount = 0;
          } else {
    	      this.columnCount = metaData.getColumnCount();
          }
    	  this.colNameToColIndex = new HashMap<String, Integer>(this.columnCount);
    	  for (int column = 1; column <= this.columnCount; column++) {
    		  String label = metaData.getColumnLabel(column).toLowerCase();
    		  String name  = metaData.getColumnName(column).toLowerCase();
    		  this.columnLabels.put(column, label);
    		  this.columnNames.put(column, name);
    		  colNameToColIndex.put(label, column);
    		  colNameToColIndex.put(name, column);
    	  }
    	  this.loaded = true;
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
  }

  public String getColumnName(int column) {
      return this.columnNames.get(column);
  }
  
  public String getColumnLabel(int column) {
	  return this.columnLabels.get(column);
  }

  /*
   * (non-Javadoc)
   * 
   * @see net.sf.log4jdbc.ResultSetCollector#methodReturned(net.sf.log4jdbc.
   * ResultSetSpy, java.lang.String, java.lang.Object, java.lang.Object,
   * java.lang.Object)
   */
  @Override
  public boolean methodReturned(ResultSetSpy resultSetSpy, String methodCall, Object returnValue, 
		  Object targetObject, Object... methodParams) 
  {
         
    if (methodCall.startsWith("get") && methodParams != null && methodParams.length == 1) {
            
      String methodName = methodCall.substring(0, methodCall.indexOf('('));
      if (GETTERS.contains(methodName) && getColumnCount() != 0) {
        setColIndexFromGetXXXMethodParams(methodParams);
        makeRowIfNeeded();
        row.set(colIndex - 1, returnValue);
      }
    }
    if (methodCall.equals("wasNull()") && getColumnCount() != 0) {
      if (Boolean.TRUE.equals(returnValue)) {
        row.set(colIndex - 1, NULL_RESULT_SET_VAL);
      }
    }
    if ("next()".equals(methodCall) || "first()".equals(methodCall)) {
    	this.lastValueReturnedByNext = (Boolean) returnValue;
    }
    if ("next()".equals(methodCall) || "first()".equals(methodCall) || 
    		"close()".equals(methodCall)) {
      loadMetaDataIfNeeded(resultSetSpy.getRealResultSet());
      boolean isEndOfResultSet = 
    		  //"close" triggers a printing only if the previous call to next 
    		  //did not return false (end of result set already reached)
    		  ("close()".equals(methodCall) && this.lastValueReturnedByNext != false) || 
    		  Boolean.FALSE.equals(returnValue) ;
      if (row != null) {
        if (rows == null)
          rows = new ArrayList<List<Object>>();
        rows.add(row);
        row = null;
      }
      if (isEndOfResultSet) {
        return true;
      }
    }
    // TODO: Tim: if prev() called, warn about no support for reverse cursors

    if ("getMetaData()".equals(methodCall)) {
      // If the client code calls getMetaData then we don't have to
      //here we assume that the real ResultSet is not yet closed. 
      this.loadMetaDataIfNeeded((ResultSetMetaData) returnValue);
    }
    return false;
  }

  private void makeRowIfNeeded() {
    if (row == null) {
      row = new ArrayList<Object>(getColumnCount());
      for (int i = 0; i < getColumnCount(); ++i) {
        row.add(UNREAD);
      }
    }
  }

  private void setColIndexFromGetXXXMethodParams(Object... methodParams) {
    Object param1 = methodParams[0];
    if (param1 == null) {
      throw new RuntimeException("ResultSet.getXXX() first param null? ");
    }
    if (param1 instanceof Integer) {
      colIndex = (Integer) param1;
    } else if (param1 instanceof String) {
      if (colNameToColIndex == null) {
        throw new RuntimeException("ResultSet.getXXX(colName): colNameToColIndex null");
      }
      Integer idx = colNameToColIndex.get(((String) param1).toLowerCase());

      if (idx == null) {
        throw new RuntimeException("ResultSet.getXXX(colName): could not look up name");
      }
      colIndex = idx;
    } else {
      throw new RuntimeException("ResultSet.getXXX called with: " + param1.getClass().getName());
    }
  }

  @Override
  public void preMethod(ResultSetSpy resultSetSpy, String methodCall, Object... methodParams) {
    if ((methodCall.equals("next()") || methodCall.equals("close()")) && 
    		fillInUnreadValues) {
      if (row != null) {
        int colIndex = 0;
        for (Object v : row) {
          if (v != null && v.toString().equals(UNREAD)) {
            Object resultSetValue = null;
            try {
              // Fill in any unread data 
              resultSetValue = JdbcUtils.getResultSetValue(resultSetSpy.getRealResultSet(),colIndex+1);
            } catch (SQLException e) {
              resultSetValue = UNREAD_ERROR;
            }
            if (resultSetValue!=null) {
              row.set(colIndex, resultSetValue);
            } else {
              row.set(colIndex, NULL_RESULT_SET_VAL);
            }
          }
          colIndex++;
        }
      }
    }
  }

}
