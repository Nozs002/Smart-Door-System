package com.smartdoor.backend.door.cli;

import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.door.service.DoorProvisioningService;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Chạy bằng profile local,provision; không mở cổng HTTP, không phải vai trò Admin trên App. */
@Component
@Profile("provision")
public class ProvisionCli implements ApplicationRunner {
	private final DoorProvisioningService provisioning;
	private final DeviceLifecycleService lifecycle;
	private final ConfigurableApplicationContext context;

	public ProvisionCli(DoorProvisioningService provisioning, DeviceLifecycleService lifecycle,
			ConfigurableApplicationContext context) {
		this.provisioning = provisioning; this.lifecycle = lifecycle; this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		String action = required(args, "tool.action");
		switch (action) {
			case "provision" -> print(provisioning.provision(required(args, "tool.name"),
					optional(args, "tool.location"), required(args, "tool.node1"), required(args, "tool.node2")));
			case "reissue" -> print(provisioning.reissue(id(args, "tool.door-id")));
			case "replacement" -> {
				var replacement = lifecycle.registerReplacement(id(args, "tool.door-id"), id(args, "tool.old-device-id"),
						required(args, "tool.device-code"), required(args, "tool.reason"));
				System.out.println("Replacement device ID: " + replacement.id());
				System.out.println("Replacement device code: " + replacement.deviceCode());
				System.out.println("Replacement Node registered as PROVISIONED; no Broker credential was issued.");
			}
			default -> throw new IllegalArgumentException("tool.action phải là provision, reissue hoặc replacement.");
		}
		context.close();
	}

	private void print(DoorProvisioningService.ProvisionResult result) {
		System.out.println("Door ID: " + result.doorId());
		System.out.println("Activation code (displayed once): " + result.activationCode());
		System.out.println("Expires at (UTC): " + result.expiresAt());
	}

	private String optional(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		if (values == null || values.isEmpty()) return null;
		if (values.size() != 1 || values.getFirst().isBlank()) throw new IllegalArgumentException("Giá trị không hợp lệ: " + name);
		return values.getFirst();
	}
	private String required(ApplicationArguments args, String name) {
		String value = optional(args, name);
		if (value == null) throw new IllegalArgumentException("Thiếu tùy chọn --" + name);
		return value;
	}
	private Long id(ApplicationArguments args, String name) {
		try {
			long value = Long.parseLong(required(args, name));
			if (value <= 0) throw new NumberFormatException();
			return value;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("ID phải là số nguyên dương: " + name);
		}
	}
}
