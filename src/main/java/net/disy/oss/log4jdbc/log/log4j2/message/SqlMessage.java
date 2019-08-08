package net.sf.log4jdbc.log.log4j2.message;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import net.sf.log4jdbc.Properties;

/**
 * Parent class of all <code>Message</code>s associated with log4jdbc log events, 
 * to perform common operations such as sql formatting.
 * <p>
 * Subclasses must implement the abstract <code>buildMessage()</code> method, 
 * that will then be called by the method <code>getFormattedMessage()</code> of this class, 
 * to populate the <code>message</code> attribute, only once. 
 * This way, messages are generated only once, and only when needed 
 * (avoid useless strings concatenations for instance).
 * 
 * @author Frederic Bastian
 * @version 1.0
 * @since 1.0
 */
public abstract class SqlMessage
{
	/**
	 * System dependent line separator. 
	 */
	protected static String nl = System.getProperty("line.separator");
	/**
	 * A <code>boolean</code> to define whether debugInfo should be displayed.
	 * @see #getDebugInfo()
	 */
	private boolean isDebugEnabled;
	/**
	 * A <code>String</code> representing the final message 
	 * built when needed using the attributes of this class.
     * @see #buildMessage()
	 */
	private String message;

	/**
	 * Default constructor.
	 */
    public SqlMessage()
    {
    	this(false);
    }
    /**
     * Constructor
     * @param isDebugEnabled A <code>boolean</code> used to set the <code>isDebugEnabled</code> attribute
     * @see #isDebugEnabled
     */
    public SqlMessage(boolean isDebugEnabled)
    {
    	this.setDebugEnabled(isDebugEnabled);
    	this.setMessage(null);
    }
    
    /**
     * Populate the <code>message</code> attribute.
     * All subclasses can implement this method as they want, 
     * but the outcome must be to assign a <code>String</code> not <code>null</code> 
     * to the <code>message</code> attribute.
     * This method is called only when this <code>Message</code> is actually logged, 
     * avoiding useless concatenation costs, etc.
     * 
     * @see #message
     */
    protected abstract void buildMessage();

	public String getFormattedMessage() {
		if (this.getMessage() == null) {
			this.buildMessage();
		}
		return this.getMessage();
	}

	public String getFormat() {
		return this.getFormattedMessage();
	}

	public Object[] getParameters() {
		return null;
	}
	
	/**
	 * Always return <code>null</code>, no messages store a <code>Throwable</code> in this project.
	 * @return 	always <code>null</code>
	 */
	public Throwable getThrowable() {
		return null;
	}
    
    /**
     * Break an SQL statement up into multiple lines in an attempt to make it
     * more readable.
     *
     * @param sql SQL to break up.
     * @return SQL broken up into multiple lines
     * @author Arthur Blake
     */
    protected String processSql(String sql)
    {
    	if (sql==null) {
    		return null;
    	}

    	if (Properties.isSqlTrim()) {
    		sql = sql.trim();
    	}

    	StringBuilder output = new StringBuilder();

    	if (Properties.getDumpSqlMaxLineLength() <= 0) {
    		output.append(sql);
    	} else {
    		// insert line breaks into sql to make it more readable
    		StringTokenizer st = new StringTokenizer(sql);
    		String token;
    		int linelength = 0;

    		while (st.hasMoreElements()) {
    			token = (String) st.nextElement();

    			output.append(token);
    			linelength += token.length();
    			output.append(" ");
    			linelength++;
    			if (linelength > Properties.getDumpSqlMaxLineLength()) {
    				output.append(nl);
    				linelength = 0;
    			}
    		}
    	}

    	if (Properties.isDumpSqlAddSemicolon()) {
    		output.append(";");
    	}

    	String stringOutput = output.toString();

    	if (Properties.isTrimExtraBlankLinesInSql()) {
    		LineNumberReader lineReader = new LineNumberReader(new StringReader(stringOutput));

    		output = new StringBuilder();

    		int contiguousBlankLines = 0;
    		int lineCount = 0;
    		try {
    			while (true) {
    				String line = lineReader.readLine();
    				if (line==null) {
    					break;
    				}
    				//add a line return only if several lines;
    				//it is the responsibility of the caller to add a final line return if needed
    				if (lineCount > 0) {
        				output.append(nl);
    				}

    				// is this line blank?
    				if (line.trim().length() == 0) {
    					contiguousBlankLines ++;
    					// skip contiguous blank lines
    					if (contiguousBlankLines > 1) {
    						continue;
    					}
    				} else {
    					contiguousBlankLines = 0;
    					output.append(line);
    				}
    				lineCount++;
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
     * Get debugging info - the module and line number that called the logger
     * version that prints the stack trace information from the point just before
     * we got it (net.sf.log4jdbc)
     *
     * if the optional log4jdbc.debug.stack.prefix system property is defined then
     * the last call point from an application is shown in the debug
     * trace output, instead of the last direct caller into log4jdbc
     *
     * @return debugging info for whoever called into JDBC from within the application.
     * @author Arthur Blake
     */
    protected static String getDebugInfo()
    {
    	Throwable t = new Throwable();
    	t.fillInStackTrace();

    	StackTraceElement[] stackTrace = t.getStackTrace();

    	if (stackTrace != null) {
    		String className;
    		StringBuffer dump = new StringBuffer();

    		/**
    		 * The DumpFullDebugStackTrace option is useful in some situations when
    		 * we want to see the full stack trace in the debug info-  watch out
    		 * though as this will make the logs HUGE!
    		 */
    		if (Properties.isDumpFullDebugStackTrace()) {
    			boolean first=true;
    			for (int i = 0; i < stackTrace.length; i++) {
    				className = stackTrace[i].getClassName();
    				if (!className.startsWith("net.sf.log4jdbc")) {
    					if (first) {
    						first = false;
    					} else {
    						dump.append("  ");
    					}
    					dump.append("at ");
    					dump.append(stackTrace[i]);
    					dump.append(nl);
    				}
    			}
    		} else {
    			dump.append(" ");
    			int firstLog4jdbcCall = 0;
    			int lastApplicationCall = 0;

    			for (int i = 0; i < stackTrace.length; i++) {
    				className = stackTrace[i].getClassName();
    				if (className.startsWith("net.sf.log4jdbc")) {
    					firstLog4jdbcCall = i;
    					
    				} else if (Properties.isTraceFromApplication() &&
    						Pattern.matches(Properties.getDebugStackPrefix(), className)) {
    					lastApplicationCall = i;
    					break;
    				}
    			}
    			int j = lastApplicationCall;

    			if (j == 0) { // if app not found, then use whoever was the last guy that called a log4jdbc class.
    				if (stackTrace.length > 1 + firstLog4jdbcCall) {
    				    j = 1 + firstLog4jdbcCall;
    				} else {
    					j = firstLog4jdbcCall;
    				}
    			}

    			dump.append(stackTrace[j].getClassName()).append(".").append(stackTrace[j].getMethodName()).append("(").
    			append(stackTrace[j].getFileName()).append(":").append(stackTrace[j].getLineNumber()).append(")");
    		}

    		return dump.toString();
    	}
    	return null;
    }

	/**
	 * @return the isDebugEnabled
	 */
	protected boolean isDebugEnabled() {
		return this.isDebugEnabled;
	}
	/**
	 * @param isDebugEnabled the isDebugEnabled to set
	 */
	protected void setDebugEnabled(boolean isDebugEnabled) {
		this.isDebugEnabled = isDebugEnabled;
	}
	
	/**
	 * @return the message
	 */
	protected String getMessage() {
		return message;
	}
	/**
	 * @param message the message to set
	 */
	protected void setMessage(String message) {
		this.message = message;
	}
}
