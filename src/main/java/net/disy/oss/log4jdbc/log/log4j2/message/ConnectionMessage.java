package net.sf.log4jdbc.log.log4j2.message;

import net.sf.log4jdbc.sql.Spy;
import net.sf.log4jdbc.sql.jdbcapi.ConnectionSpy;

import org.apache.logging.log4j.message.Message;

/**
 * <code>SqlMessage</code> related to connection events.
 * 
 * @author Frederic Bastian
 * @author Mathieu Seppey
 * @see net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator#connectionOpened(Spy, long)
 * @see net.sf.log4jdbc.log4j2.Log4j2SpyLogDelegator#connectionClosed(Spy, long)
 * @version 1.0
 * @since 1.0
 */
public class ConnectionMessage extends SqlMessage implements Message 
{
  private static final long serialVersionUID = 6278727380958233518L;

  /**
   * An <code>enum</code> to define the value of <code>operation</code> 
   * when the action on the connection was to open,close or abort it.
   * @see operation
   */   
  public static enum Operation
  { 
    OPENING, CLOSING, ABORTING; 
  }; 

  /**
   * <code>ConnectionSpy</code> that was opened or closed. 
   * Will be used to build the <code>message</code>, only when needed.
   * @see #message
   * @see #buildMessage()
   */
  private Spy spy;
  /**
   * an <code>int</code> to define if the operation was to open, or to close connection. 
   * Should be equals to <code>OPENING</code> if the operation was to open the connection, 
   * to <code>CLOSING</code> if the operation was to close the connection. 
   * Will be used to build the <code>message</code>, only when needed.
   * @see #Operation
   * @see #message
   * @see #buildMessage()
   */
  private Operation operation;  
  /**
   * A <code>long</code> defining the time elapsed to open or close the connection in ms. 
   * Will be used to build the <code>message</code>, only when needed.
   * @see #message
   * @see #buildMessage()
   */
  private long execTime;

  /**
   * Default constructor
   */
  public ConnectionMessage()
  {
    this(null, -1L, null, false);
  }

  /**
   * 
   * @param spy 			<code>ConnectionSpy</code> that was opened or closed.
   * @param execTime 		A <code>long</code> defining the time elapsed to open or close the connection in ms
   * 					Caller should pass -1 if not used
   * @param operation 	an <code>int</code> to define if the operation was to open, or to close connection. 
   * 						Should be equals to <code>OPENING</code> if the operation was to open the connection, 
   * 						to <code>CLOSING</code> if the operation was to close the connection.
   * @param isDebugEnabled A <code>boolean</code> to define whether debugInfo should be displayed.
   */
  public ConnectionMessage(Spy spy, long execTime, Operation operation, boolean isDebugEnabled)
  {
    super(isDebugEnabled);

    this.spy = spy;
    this.execTime = execTime;
    if (operation == Operation.OPENING || operation == Operation.CLOSING || operation == Operation.ABORTING) {
      this.operation = operation;
    } else {
      this.operation = null;
    }
  }

  @Override
  protected void buildMessage() 
  {
    StringBuffer buildMsg = new StringBuffer();

    if (this.isDebugEnabled()) {
      buildMsg.append(SqlMessage.getDebugInfo());
      buildMsg.append(SqlMessage.nl);
    }

    buildMsg.append(spy.getConnectionNumber()).append(". Connection ");
    if (this.operation == Operation.OPENING) {
      buildMsg.append("opened.");
    } else if (this.operation == Operation.CLOSING) {
      buildMsg.append("closed.");
    } else if (this.operation == Operation.ABORTING) {
      buildMsg.append("aborted.");      
    } else {
      buildMsg.append("opened, closed or aborted.");
    }
    if (this.execTime != -1) {
      buildMsg.append(" {executed in ").append(this.execTime).append("ms} ");
    }
    if (this.isDebugEnabled()) {
      buildMsg.append(SqlMessage.nl);
      buildMsg.append(ConnectionSpy.getOpenConnectionsDump());
    }

    this.setMessage(buildMsg.toString());
  }
}
