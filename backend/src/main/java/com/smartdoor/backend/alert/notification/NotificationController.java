package com.smartdoor.backend.alert.notification;

import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.security.CurrentAccountProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
	private final NotificationService notifications;
	private final CurrentAccountProvider currentAccount;
	public NotificationController(NotificationService notifications, CurrentAccountProvider currentAccount) {
		this.notifications = notifications; this.currentAccount = currentAccount;
	}
	@GetMapping
	public PageResponse<NotificationView> list(@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return notifications.list(currentAccount.requireAccountId(), unread, page, size);
	}
	@PostMapping("/{id}/read")
	public NotificationView read(@PathVariable Long id) {
		return notifications.markRead(currentAccount.requireAccountId(), id);
	}
}
