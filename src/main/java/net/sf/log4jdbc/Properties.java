package net.sf.log4jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;

import net.sf.log4jdbc.log.SpyLogDelegator;
import net.sf.log4jdbc.log.SpyLogFactory;


/**
 * This class loads the properties for <code>log4jdbc-log4j2</code>. 
 * They are tried to be read first from a property file in the classpath 
 * (called "log4jdbc.log4j2.properties"), then from the <code>System</code> properties.
 * <p>
 * This class has been copied from <code>net.sf.log4jdbc.DriverSpy</code> 
 * developed by Arthur Blake. ALl the properties that were loaded in this class 
 * are now loaded here. Differences as compared to this former implementation: 
 * <h3>Modifications for log4jdbc</h3>
 * <ul>
 * <li>Addition of public getters for the following attributes: <code>TrimSql</code>, 
 * <code>DumpSqlMaxLineLength</code>, <code>DumpSqlAddSemicolon</code>, 
 * <code>TrimExtraBlankLinesInSql</code>, <code>DumpFullDebugStackTrace</code>, 
 * <code>TraceFromApplication</code>, 
 * <code>DebugStackPrefix</code>, <code>DumpSqlFilteringOn</code>, 
 * <code>DumpSqlSelect</code>, <code>DumpSqlInsert</code>, <code>DumpSqlUpdate</code>, 
 * <code>DumpSqlCreate</code>, <code>DumpSqlDelete</code>, 
 * <code>SqlTimingErrorThresholdEnabled</code>, <code>SqlTimingErrorThresholdMsec</code>, 
 * <code>SqlTimingWarnThresholdEnabled</code>, <code>SqlTimingWarnThresholdMsec</code>, 
 * <code>AutoLoadPopularDrivers</code>.
 * <li>Addition of a new attribute, <code>SpyLogDelegatorName</code>, and the corresponding getter, 
 * <code>getSpyLogDelegatorName()</code>. 
 * Corresponds to the property "log4jdbc.spylogdelegator.name". 
 * Define the class implementing <code>SpyLogDelegator</code> to load.
 * Default is <code>net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator</code>. 
 * <code>net.sf.log4jdbc.SpyLogFactory</code> has been modified accordingly.  
 * <li><code>DebugStackPrefix</code> is now a <code>String</code> corresponding to a REGEX, 
 * not only to the beginning of the package name (this can obviously done using "^"). 
 * This is true only if log4j2 is used (see <code>SpyLogDelegatorName</code>), 
 * otherwise it has the standard behavior.
 * </ul>
 * 
 * @author Mathieu Seppey
 * @author Frederic Bastian
 * @author Arthur Blake
 * @version 0.1
 * @since 0.1
 */
public final class Properties 
{
	private static volatile SpyLogDelegator log;

	/**
	 * A <code>String</code> representing the name of the class implementing 
	 * <code>SpyLogDelegator</code> to use. It is used by {@link SpyLogFactory} 
	 * to determine which class to load. 
	 * Default is <code>net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator</code> 
	 * 
	 * @see SpyLogFactory
	 */
	static final String SpyLogDelegatorName;	  

	/**
	 * Optional package prefix to use for finding application generating point of
	 * SQL.
	 */
	static final String DebugStackPrefix;

	/**
	 * Flag to indicate debug trace info should be from the calling application
	 * point of view (true if DebugStackPrefix is set.)
	 */
	static final boolean TraceFromApplication;

	/**
	 * Flag to indicate if a warning should be shown if SQL takes more than
	 * SqlTimingWarnThresholdMsec milliseconds to run.  See below.
	 */
	static final boolean SqlTimingWarnThresholdEnabled;

	/**
	 * An amount of time in milliseconds for which SQL that executed taking this
	 * long or more to run shall cause a warning message to be generated on the
	 * SQL timing logger.
	 *
	 * This threshold will <i>ONLY</i> be used if SqlTimingWarnThresholdEnabled
	 * is true.
	 */
	static final long SqlTimingWarnThresholdMsec;

	/**
	 * Flag to indicate if an error should be shown if SQL takes more than
	 * SqlTimingErrorThresholdMsec milliseconds to run.  See below.
	 */
	static final boolean SqlTimingErrorThresholdEnabled;

	/**
	 * An amount of time in milliseconds for which SQL that executed taking this
	 * long or more to run shall cause an error message to be generated on the
	 * SQL timing logger.
	 *
	 * This threshold will <i>ONLY</i> be used if SqlTimingErrorThresholdEnabled
	 * is true.
	 */
	static final long SqlTimingErrorThresholdMsec;

	/**
	 * When dumping boolean values, dump them as 'true' or 'false'.
	 * If this option is not set, they will be dumped as 1 or 0 as many
	 * databases do not have a boolean type, and this allows for more
	 * portable sql dumping.
	 */
	static final boolean DumpBooleanAsTrueFalse;

	/**
	 * When dumping SQL, if this is greater than 0, than the SQL will
	 * be broken up into lines that are no longer than this value.
	 */
	static final int DumpSqlMaxLineLength;

	/**
	 * If this is true, display a special warning in the log along with the SQL
	 * when the application uses a Statement (as opposed to a PreparedStatement.)
	 * Using Statements for frequently used SQL can sometimes result in
	 * performance and/or security problems.
	 */
	static final boolean StatementUsageWarn;

	/**
	 * Options to more finely control which types of SQL statements will
	 * be dumped, when dumping SQL.
	 * By default all 5 of the following will be true.  If any one is set to
	 * false, then that particular type of SQL will not be dumped.
	 */
	static final boolean DumpSqlSelect;
	static final boolean DumpSqlInsert;
	static final boolean DumpSqlUpdate;
	static final boolean DumpSqlDelete;
	static final boolean DumpSqlCreate;

	// only true if one ore more of the above 4 flags are false.
	static final boolean DumpSqlFilteringOn;

	/**
	 * If true, add a semilcolon to the end of each SQL dump.
	 */
	static final boolean DumpSqlAddSemicolon;

	/**
	 * If dumping in debug mode, dump the full stack trace.
	 * This will result in a VERY voluminous output, but can be very useful
	 * under some circumstances.
	 */
	static final boolean DumpFullDebugStackTrace;

	/**
	 * Attempt to Automatically load a set of popular JDBC drivers?
	 */
	static final boolean AutoLoadPopularDrivers;
	/**
	 * A <code>Collection</code> of <code>String</code>s listing the additional drivers 
	 * to use beside the default drivers auto-loaded.
	 */
	static final Collection<String> AdditionalDrivers;

	/**
	 * Trim SQL before logging it?
	 */
	static final boolean TrimSql;

	/**
	 * Remove extra Lines in the SQL that consist of only white space?
	 * Only when 2 or more lines in a row like this occur, will the extra lines (beyond 1)
	 * be removed.
	 */
	static final boolean TrimExtraBlankLinesInSql;

	/**
	 * Coldfusion typically calls PreparedStatement.getGeneratedKeys() after
	 * every SQL update call, even if it's not warranted.  This typically produces
	 * an exception that is ignored by Coldfusion.  If this flag is true, then
	 * any exception generated by this method is also ignored by log4jdbc.
	 */
	static final boolean SuppressGetGeneratedKeysException;

	
	/**
	 * Static initializer. 
	 */
	static 
	{
        //first we init the logger
		log = null;
		
		//then we need the properties to define which logger to use
		java.util.Properties props = getProperties();
		SpyLogDelegatorName = props.getProperty("log4jdbc.spylogdelegator.name");
		
		//now we set the logger ourselves 
		//(as long as this class is not fully initialized, 
		//SpyLogFactory cannot determine which logger to use)
		SpyLogFactory.loadSpyLogDelegator(getSpyLogDelegatorName());
		log = SpyLogFactory.getSpyLogDelegator();
		
		//now log some debug message
		log.debug("log4jdbc-logj2 properties initialization...");
		log.debug("Using logger: " + getSpyLogDelegatorName());
		
		//and now we set all the other properties, with proper logging messages
		//here, this method should have been already called 
		//by preLoggerIntialization(), but we use it again here so that we can log 
		//where the properties come from.
		// look for additional driver specified in properties
		DebugStackPrefix = getStringOption(props, "log4jdbc.debug.stack.prefix");
		TraceFromApplication = DebugStackPrefix != null;

		Long thresh = getLongOption(props, "log4jdbc.sqltiming.warn.threshold");
		SqlTimingWarnThresholdEnabled = (thresh != null);
		long SqlTimingWarnThresholdMsecTemp = -1;
		if (SqlTimingWarnThresholdEnabled)
		{
			SqlTimingWarnThresholdMsecTemp = thresh.longValue();
		}
		SqlTimingWarnThresholdMsec = SqlTimingWarnThresholdMsecTemp;

		thresh = getLongOption(props, "log4jdbc.sqltiming.error.threshold");
		SqlTimingErrorThresholdEnabled = (thresh != null);
		long SqlTimingErrorThresholdMsecTemp = -1;
		if (SqlTimingErrorThresholdEnabled)
		{
			SqlTimingErrorThresholdMsecTemp = thresh.longValue();
		}
		SqlTimingErrorThresholdMsec = SqlTimingErrorThresholdMsecTemp;

		DumpBooleanAsTrueFalse =
				getBooleanOption(props, "log4jdbc.dump.booleanastruefalse",false);

		DumpSqlMaxLineLength = getLongOption(props,
				"log4jdbc.dump.sql.maxlinelength", 90L).intValue();

		DumpFullDebugStackTrace =
				getBooleanOption(props, "log4jdbc.dump.fulldebugstacktrace",false);

		StatementUsageWarn =
				getBooleanOption(props, "log4jdbc.statement.warn",false);

		DumpSqlSelect = getBooleanOption(props, "log4jdbc.dump.sql.select",true);
		DumpSqlInsert = getBooleanOption(props, "log4jdbc.dump.sql.insert",true);
		DumpSqlUpdate = getBooleanOption(props, "log4jdbc.dump.sql.update",true);
		DumpSqlDelete = getBooleanOption(props, "log4jdbc.dump.sql.delete",true);
		DumpSqlCreate = getBooleanOption(props, "log4jdbc.dump.sql.create",true);

		DumpSqlFilteringOn = !(DumpSqlSelect && DumpSqlInsert && DumpSqlUpdate &&
				DumpSqlDelete && DumpSqlCreate);

		DumpSqlAddSemicolon = getBooleanOption(props,
				"log4jdbc.dump.sql.addsemicolon", false);

		AutoLoadPopularDrivers = getBooleanOption(props,
				"log4jdbc.auto.load.popular.drivers", true);

		// look for additional driver specified in properties
		String moreDrivers = getStringOption(props, "log4jdbc.drivers");
		AdditionalDrivers = new HashSet<String>();
		
		if (moreDrivers != null) {
			String[] moreDriversArr = moreDrivers.split(",");
			for (int i = 0; i < moreDriversArr.length; i++) {
				AdditionalDrivers.add(moreDriversArr[i]);
				log.debug ("    will look for specific driver " + moreDriversArr[i]);
			}
		}

		TrimSql = getBooleanOption(props, "log4jdbc.trim.sql", true);

		TrimExtraBlankLinesInSql = getBooleanOption(props, "log4jdbc.trim.sql.extrablanklines", true);

		SuppressGetGeneratedKeysException =
				getBooleanOption(props, "log4jdbc.suppress.generated.keys.exception",
						false);

		
		log.debug("log4jdbc-logj2 properties initialization done.");
	}   
	
	/**
	 * Get the <code>java.util.Properties</code> either from the System properties, 
	 * or from a configuration file.
	 * Events will be logged only if <code>#log</code> has already been set, 
	 * and this method will not try to load it: 
	 * this method can be called before the properties needed to know 
	 * which <code>SpyLogDelegator</code> to use are loaded, 
	 * and it would generate an error to try to load it. 
	 * @return 		The <code>java.util.Properties</code> to get log4jdbc properties from.
	 */
	private static java.util.Properties getProperties()
	{
		java.util.Properties props = new java.util.Properties(System.getProperties());
    	//try to get the properties file.
    	//default name is log4jdbc.log4j2.properties
    	//check first if an alternative name has been provided in the System properties
    	String propertyFile = props.getProperty("log4jdbc.log4j2.properties.file", 
    			"/log4jdbc.log4j2.properties");
		if (log != null) {
		    log.debug("Trying to use properties file " + propertyFile);
		}
    	InputStream propStream = Properties.class.getResourceAsStream(propertyFile);
    	if (propStream != null) {
    		try {
    			props.load(propStream);
    		} catch (IOException e) {
    			if (log != null) {
    			    log.debug("Error when loading log4jdbc.log4j2.properties from classpath: " + 
    			        e.getMessage());
    			}
    		} finally {
    			try {
    				propStream.close();
    			} catch (IOException e) {
    				if (log != null) {
    				    log.debug("Error when closing log4jdbc.log4j2.properties file" + 
    			            e.getMessage());
    				}
    			}
    		}
    		if (log != null) {
    		    log.debug("log4jdbc.logj2.properties loaded from classpath");
    		}
    	} else {
    		if (log != null) {
    		    log.debug("log4jdbc.logj2.properties not found in classpath. Using System properties.");
    		}
    	}
    	
    	return props;
	}
	
	/**
	 * Get a Long option from a property and
	 * log a debug message about this.
	 *
	 * @param props Properties to get option from.
	 * @param propName property key.
	 *
	 * @return the value of that property key, converted
	 * to a Long.  Or null if not defined or is invalid.
	 */
	private static Long getLongOption(java.util.Properties props, String propName)
	{
		String propValue = props.getProperty(propName);
		Long longPropValue = null;
		if (propValue == null)
		{
			log.debug("x " + propName + " is not defined");
		}
		else
		{
			try
			{
				longPropValue = new Long(Long.parseLong(propValue));
				log.debug("  " + propName + " = " + longPropValue);
			}
			catch (NumberFormatException n)
			{
				log.debug("x " + propName + " \"" + propValue  +
						"\" is not a valid number");
			}
		}
		return longPropValue;
	}

	/**
	 * Get a Long option from a property and
	 * log a debug message about this.
	 *
	 * @param props Properties to get option from.
	 * @param propName property key.
	 *
	 * @return the value of that property key, converted
	 * to a Long.  Or null if not defined or is invalid.
	 */
	private static Long getLongOption(java.util.Properties props, String propName,
			long defaultValue)
	{
		String propValue = props.getProperty(propName);
		Long longPropValue;
		if (propValue == null)
		{
			log.debug("x " + propName + " is not defined (using default of " +
					defaultValue +")");
			longPropValue = new Long(defaultValue);
		}
		else
		{
			try
			{
				longPropValue = new Long(Long.parseLong(propValue));
				log.debug("  " + propName + " = " + longPropValue);
			}
			catch (NumberFormatException n)
			{
				log.debug("x " + propName + " \"" + propValue  +
						"\" is not a valid number (using default of " + defaultValue +")");
				longPropValue = new Long(defaultValue);
			}
		}
		return longPropValue;
	}

	/**
	 * Get a String option from a property and
	 * log a debug message about this.
	 *
	 * @param props Properties to get option from.
	 * @param propName property key.
	 * @return the value of that property key.
	 */
	private static String getStringOption(java.util.Properties props, String propName)
	{
		String propValue = props.getProperty(propName);
		if (propValue == null || propValue.length()==0)
		{
			log.debug("x " + propName + " is not defined");
			propValue = null; // force to null, even if empty String
		}
		else
		{
			log.debug("  " + propName + " = " + propValue);
		}
		return propValue;
	}

	/**
	 * Get a boolean option from a property and
	 * log a debug message about this.
	 *
	 * @param props Properties to get option from.
	 * @param propName property name to get.
	 * @param defaultValue default value to use if undefined.
	 *
	 * @return boolean value found in property, or defaultValue if no property
	 *         found.
	 */
	private static boolean getBooleanOption(java.util.Properties props, String propName,
			boolean defaultValue)
	{
		String propValue = props.getProperty(propName);
		boolean val;
		if (propValue == null) {
			log.debug("x " + propName + " is not defined (using default value " +
					defaultValue + ")");
			return defaultValue;
		}
		propValue = propValue.trim().toLowerCase();
		if (propValue.length() == 0) {
			val = defaultValue;
		} else {
			val= "true".equals(propValue) ||
				 "yes".equals(propValue) || 
				 "on".equals(propValue);
		}
		log.debug("  " + propName + " = " + val);
		return val;
	}
	
	

	/**
	 * @return the SpyLogDelegatorName
	 * @see #SpyLogDelegatorName
	 */
	public static String getSpyLogDelegatorName() {
		return SpyLogDelegatorName;
	}    
	

	  
	  public static boolean isSqlTrim()
	  {
		  return TrimSql;
	  }
	  /**
	   * @return the dumpSqlMaxLineLength
	   */
	  public static int getDumpSqlMaxLineLength() {
	    return DumpSqlMaxLineLength;
	  }
	  
	  /**
	   * @return the dumpSqlAddSemicolon
	   */
	  public static boolean isDumpSqlAddSemicolon() {
	  	return DumpSqlAddSemicolon;
	  }
	  
	  /**
	   * @return the trimExtraBlankLinesInSql
	   */
	  public static boolean isTrimExtraBlankLinesInSql() {
	  	return TrimExtraBlankLinesInSql;
	  }
	  
	  /**
	   * @return the dumpFullDebugStackTrace
	   */
	  public static boolean isDumpFullDebugStackTrace() {
	  	return DumpFullDebugStackTrace;
	  }
	  
	  /**
	   * @return the traceFromApplication
	   */
	  public static boolean isTraceFromApplication() {
	  	return TraceFromApplication;
	  }
	  
	  /**
	   * @return the debugStackPrefix
	   */
	  public static String getDebugStackPrefix() {
	  	return DebugStackPrefix;
	  }
	  
	  /**
	   * @return the dumpSqlFilteringOn
	   */
	  public static boolean isDumpSqlFilteringOn() {
	  	return DumpSqlFilteringOn;
	  }
	  
	  /**
	   * @return the dumpSqlSelect
	   */
	  public static boolean isDumpSqlSelect() {
	  	return DumpSqlSelect;
	  }
	  /**
	   * @return the DumpSqlUpdate
	   */
	  public static boolean isDumpSqlUpdate() {
	  	return DumpSqlUpdate;
	  }
	  /**
	   * @return the DumpSqlInsert
	   */
	  public static boolean isDumpSqlInsert() {
	  	return DumpSqlInsert;
	  }
	  /**
	   * @return the DumpSqlDelete
	   */
	  public static boolean isDumpSqlDelete() {
	  	return DumpSqlDelete;
	  }
	  /**
	   * @return the DumpSqlCreate
	   */
	  public static boolean isDumpSqlCreate() {
	  	return DumpSqlCreate;
	  }
	  
	  /**
	   * @return the sqlTimingErrorThresholdEnabled
	   */
	  public static boolean isSqlTimingErrorThresholdEnabled() {
	  	return SqlTimingErrorThresholdEnabled;
	  }
	  /**
	   * @return the sqlTimingErrorThresholdMsec
	   */
	  public static long getSqlTimingErrorThresholdMsec() {
	  	return SqlTimingErrorThresholdMsec;
	  }
	  
	  /**
	   * @return the sqlTimingWarnThresholdEnabled
	   */
	  public static boolean isSqlTimingWarnThresholdEnabled() {
	  	return SqlTimingWarnThresholdEnabled;
	  }
	  /**
	   * @return the sqlTimingWarnThresholdMsec
	   */
	  public static long getSqlTimingWarnThresholdMsec() {
	  	return SqlTimingWarnThresholdMsec;
	  }
	  /**
	   * @return the AutoLoadPopularDrivers
	   * @see #AutoLoadPopularDrivers
	   */
	  public static boolean isAutoLoadPopularDrivers() {
		  return AutoLoadPopularDrivers;
	  }
	  /**
	   * @return the AdditionalDrivers
	   * @see #AdditionalDrivers
	   */
	  public static Collection<String> getAdditionalDrivers() {
		  return AdditionalDrivers;
	  }
	  
	  /**
	   * @return the DumpBooleanAsTrueFalse
	   * @see #DumpBooleanAsTrueFalse
	   */
	  public static boolean isDumpBooleanAsTrueFalse() {
		  return DumpBooleanAsTrueFalse;
	  }
	  
	  /**
	   * @return the StatementUsageWarn
	   * @see #StatementUsageWarn
	   */
	  public static boolean isStatementUsageWarn() {
		  return StatementUsageWarn;
	  }
	  
	  /**
	   * @return the SuppressGetGeneratedKeysException
	   * @see #SuppressGetGeneratedKeysException
	   */
	  public static boolean isSuppressGetGeneratedKeysException() {
		  return SuppressGetGeneratedKeysException;
	  }

}
