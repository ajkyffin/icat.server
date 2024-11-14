package org.icatproject.exposed;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jws.WebService;

/**
 * CDI deployment of the SOAP interface.
 *
 * This is intended for use in Quarkus.
 */
@ApplicationScoped
@WebService(name="ICAT", serviceName="ICATService", targetNamespace="http://icatproject.org")
public class ICAT_CDI extends ICAT {
}
