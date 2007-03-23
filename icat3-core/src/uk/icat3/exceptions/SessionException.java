
package uk.icat3.exceptions;

/*
 * SessionException.java
 *
 * Created on 21 February 2007, 10:17
 *
 * Exception class that must be thrown by facility user
 * database in circumstances as decribed in User.java
 *
 * @author df01
 * version 1.0
 */
public class SessionException extends ICATAPIException {
    
    /**
     * Creates a new instance of <code>SessionException</code> without detail message.
     */
    public SessionException() {
    }
    
    
    /**
     * Constructs an instance of <code>SessionException</code> with the specified detail message.
     * 
     * @param msg the detail message.
     */
    public SessionException(String msg) {
        super(msg);
    }
    
    /**
     * Constructs an instance of <code>SessionException</code> with the specified detail message.
     * 
     * @param msg the detail message.
     */
    public SessionException(String msg,Exception ex) {
        super(msg,ex);
    }
    
}