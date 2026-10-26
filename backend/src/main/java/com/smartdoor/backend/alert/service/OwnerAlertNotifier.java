package com.smartdoor.backend.alert.service;

import com.smartdoor.backend.alert.dto.AlertView;

/** Adapter gửi push; accountId là đích duy nhất, không broadcast theo topic chung. */
public interface OwnerAlertNotifier {
	void notifyOwner(Long accountId, AlertView alert);
}
