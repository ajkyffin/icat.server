package org.icatproject.exposed;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.jws.WebService;

/**
 * EJB deployment of the SOAP interface.
 *
 * An EJB does not use the context-route defined in glassfish-web.xml, so this
 * gets deployed to /ICATService/ICAT.
 */
@Stateless
@WebService(name="ICAT", serviceName="ICATService", targetNamespace="http://icatproject.org")
@TransactionManagement(TransactionManagementType.BEAN)
public class ICAT_EJB extends ICAT {
}
