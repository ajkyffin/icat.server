package org.icatproject.quarkus;

import org.icatproject.core.IcatException;
import org.icatproject.core.manager.AccessType;
import org.icatproject.core.manager.AuthenticatorCredentialKey;
import org.icatproject.core.manager.AuthenticatorInfo;
import org.icatproject.core.manager.Constraint;
import org.icatproject.core.manager.EntityField;
import org.icatproject.core.manager.EntityInfo;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets={
	AccessType.class,
	AuthenticatorCredentialKey.class,
	AuthenticatorInfo.class,
	Constraint.class,
	EntityField.class,
	EntityInfo.class,
	IcatException.class,
})
public class QuarkusReflectionConfig {
}
