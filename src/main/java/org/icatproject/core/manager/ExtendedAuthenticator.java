package org.icatproject.core.manager;

import org.icatproject.authentication.Authenticator;

public class ExtendedAuthenticator {

	private Authenticator authenticator;
	private String friendly;
	private boolean admin;

	public ExtendedAuthenticator(Authenticator authenticator, String friendly, boolean admin) {
		this.authenticator = authenticator;
		this.friendly = friendly;
		this.admin = admin;
	}

	public Authenticator getAuthenticator() {
		return authenticator;
	}

	public String getFriendly() {
		return friendly;
	}

	public boolean isAdmin() {
		return admin;
	}
}
