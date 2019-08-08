package net.sf.log4jdbc.log.log4j2.message;

import net.sf.log4jdbc.sql.Spy;

import org.apache.logging.log4j.message.Message;

/**
 * <code>SqlMessage</code> related to the logging of <code>Exception</code>s.
 * 
 * @author Frederic Bastian
 * @see net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator#exceptionOccured(Spy, String, Exception, String, long)
 * @version 1.0
 * @since 1.0
 */
public class ExceptionOccuredMessage extends SqlMessage implements Message
{
    private static final long serialVersionUID = 4033892630843448750L;
    /**
     * the <code>Spy</code> wrapping the class that threw an <code>Exception</code>. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private Spy spy;
	/**
     * a <code>String</code> describing the name and call parameters 
     * of the method generated the <code>Exception</code>. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private String methodCall;
	/**
     * <code>long</code> representing the amount of time 
     * that passed before an <code>Exception</code> was thrown when sql was being executed.
     * Optional and should be set to -1 if not used. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private long execTime;
	/**
     * <code>String</code> representing the sql that occurred 
     * just before the exception occurred. 
     * Optional and <code>null</code> if not used.
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private String sql;
    
    /**
     * Default constructor
     */
    public ExceptionOccuredMessage()
    {
    	this(null, null, null, -1, false);
    }
    /**
     * 
     * @param spy        	the <code>Spy</code> wrapping the class that threw an <code>Exception</code>.
     * @param methodCall 	a <code>String</code> describing the name and call parameters 
     * 						of the method generated the <code>Exception</code>.
     * @param e          	the <code>Exception</code> that was thrown.
     * @param sql        	optional <code>String</code> representing the sql that occurred 
     * 						just before the exception occurred.
     * @param execTime   	optional <code>long</code> representing the amount of time 
     * 						that passed before an <code>Exception</code> was thrown when sql was being executed.
     *                   	caller should pass -1 if not used.
     * @param isDebugEnabled A <code>boolean</code> to define whether debugInfo should be displayed.
     */
    public ExceptionOccuredMessage(Spy spy, String methodCall, 
	        String sql, long execTime, boolean isdebugEnabled) {
    	
		super(isdebugEnabled);
		
		this.spy = spy;
		this.methodCall = methodCall;
		this.sql = sql;
		this.execTime = execTime;
    }
    
    /**
     * Populate the <code>message</code> attribute 
     * using the attributes of this class.
     * This method is called only when this <code>Message</code> is actually logged, 
     * avoiding useless concatenation costs, etc.
     * 
     * @see #message
     */
    protected void buildMessage()
    {
    	String tempMessage = "";
    	
    	String classType = this.spy.getClassType();
    	Integer spyNo = this.spy.getConnectionNumber();
    	String header = spyNo + ". " + classType + "." + this.methodCall;

    	if (this.sql == null) {
    		tempMessage = header;

    	} else {
    		String tempSql = this.processSql(this.sql);

    		// if at debug level, display debug info to error log
    		if (this.isDebugEnabled()) {
    			tempMessage = SqlMessage.getDebugInfo() + SqlMessage.nl + spyNo + ". " + tempSql;
    		} else {
    			tempMessage = header + " FAILED! " + tempSql;
    		}
    		if (this.execTime != -1) {
    			tempMessage += " {FAILED after " + execTime + " ms}";
    		}
    	}
    	
    	this.setMessage(tempMessage);
    }
}
