package com.smartdoor.backend.device.service;

import com.smartdoor.backend.device.entity.Device;

/**
 * Integration boundary for trusted device identity and applied configuration/PIN.
 * Implementations must verify current desired versions against trusted ACKs, not
 * infer readiness from heartbeat or from saving configuration in the database.
 * The device passed here is managed and must not be mutated by the implementation.
 */
@FunctionalInterface
public interface DeviceReadinessPolicy {
	boolean canActivate(Device device);

	/**
	 * Must verify revocation of the previous identity before replacing hardware.
	 * Database status alone cannot prove that a Broker credential was revoked.
	 */
	default boolean canReplace(Device previous, Device replacement) {
		return false;
	}
}
