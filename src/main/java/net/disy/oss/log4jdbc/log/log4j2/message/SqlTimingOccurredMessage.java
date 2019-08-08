package net.sf.log4jdbc.log.log4j2.message;

import net.sf.log4jdbc.sql.Spy;

import org.apache.logging.log4j.message.Message;

/**
 * <code>SqlMessage</code> related to the logging of SQL statements, with execution time information.
 * 
 * @author Frederic Bastian
 * @see net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator#sqlTimingOccurred(Spy, long, String, String)
 * @version 1.0
 * @since 1.0
 */
public class SqlTimingOccurredMessage extends SqlMessage implements Message 
{
    private static final long serialVersionUID = 6455975917838453692L;
    /**
     * a <code>String</code> describing the name and call parameters 
     * of the method that generated the SQL. 
     * Will be used to build the <code>message</code>, only when needed.
     * It is not used in the current implementation of log4jdbc
     * @see #message
     * @see #buildMessage()
     */
	@SuppressWarnings("unused")
	private String methodCall;
	/**
     * how long it took the sql to run, in ms. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private long execTime;
	/**
     * the <code>Spy</code> wrapping the class where the SQL occurred. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private Spy spy;
	/**
     * A <code>String</code> representing the sql that occurred. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private String sql;
    
    /**
     * Default Constructor
     */
    public SqlTimingOccurredMessage()
    {
    	this(null, -1, null, null, false);
    }
    
    /**
     * 
     * @param spy 			the <code>Spy</code> wrapping the class where the SQL occurred.
     * @param execTime   	how long it took the sql to run, in ms.
     * @param methodCall 	a <code>String</code> describing the name and call parameters 
     * 						of the method that generated the SQL.
     * @param sql       	A <code>String</code> representing the sql that occurred.
     * @param isDebugEnabled A <code>boolean</code> to define whether debugInfo should be displayed.
     */
    public SqlTimingOccurredMessage(Spy spy, long execTime, String methodCall, String sql, 
    		boolean isDebugEnabled)
    {
    	super(isDebugEnabled);
		this.spy = spy;
		this.execTime = execTime;
		this.methodCall = methodCall;
		this.sql = sql;
    }

	@Override
	protected void buildMessage() 
	{
	    StringBuffer out = new StringBuffer();

	    if (this.isDebugEnabled())
	    {
	      out.append(SqlMessage.getDebugInfo());
	      out.append(SqlMessage.nl);
	    }

	    out.append(this.spy.getConnectionNumber());
	    out.append(". ");
	      
	    out.append(this.processSql(this.sql));
	    out.append(" {executed in ");
	    out.append(this.execTime);
	    out.append(" ms}");

	    this.setMessage(out.toString());
	}

}
