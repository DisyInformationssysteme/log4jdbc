package net.sf.log4jdbc.log.log4j2.message;

import net.sf.log4jdbc.sql.Spy;

import org.apache.logging.log4j.message.Message;

/**
 * <code>SqlMessage</code> related to the logging of methods calls returns.
 * 
 * @author Frederic Bastian
 * @see net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator#methodReturned(Spy, String, String)
 * @version 1.0
 * @since 1.0
 */
public class MethodReturnedMessage extends SqlMessage implements Message {

	private static final long serialVersionUID = 3672279172754686950L;
	/**
     * the <code>Spy</code> wrapping the class that called the method that returned. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private Spy spy;
	/**
     * a <code>String</code> describing the name and call parameters of the method that returned. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private String returnMsg;
	/**
     * return value converted to a String for integral types, or String representation for Object.
     * Will be null for void return types.. 
     * Will be used to build the <code>message</code>, only when needed.
     * @see #message
     * @see #buildMessage()
     */
	private String methodCall;
	
	/**
	 * Default constructor.
	 */
	public MethodReturnedMessage()
	{
		this(null, null, null, false);
	}
	
	/**
	 * 
	 * @param spy        the <code>Spy</code> wrapping the class that called the method that returned.
     * @param methodCall a <code>String</code> describing the name and call parameters of the method that returned.
     * @param returnMsg  return value converted to a String for integral types, or String representation for Object.
     *                   Will be null for void return types.
     * @param isDebugEnabled A <code>boolean</code> to define whether debugInfo should be displayed.
	 */
	public MethodReturnedMessage(Spy spy, String methodCall, String returnMsg, boolean isDebugEnabled)
	{
		super(isDebugEnabled);
		this.spy = spy;
		this.methodCall = methodCall;
		this.returnMsg = returnMsg;
		
	}

	@Override
	protected void buildMessage() 
	{
		String header = this.spy.getConnectionNumber() + ". " + this.spy.getClassType() + "." +
				this.methodCall + " returned " + this.returnMsg;

		if (this.isDebugEnabled()) {
			this.setMessage(SqlMessage.getDebugInfo() + SqlMessage.nl + header);

		} else {
			this.setMessage(header);
		}
	}

}
