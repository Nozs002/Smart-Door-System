package com.smartdoor.backend.door;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doors")
public class DoorController {

	private final DoorCommandService doorCommandService;
	private final DoorSignalService doorSignalService;

	public DoorController(DoorCommandService doorCommandService, DoorSignalService doorSignalService) {
		this.doorCommandService = doorCommandService;
		this.doorSignalService = doorSignalService;
	}

	@PostMapping("/{deviceId}/open")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public DoorCommandResponse open(@PathVariable String deviceId) {
		return doorCommandService.open(deviceId);
	}

	@GetMapping("/{deviceId}/history")
	public List<DoorSignalResponse> history(@PathVariable String deviceId) {
		return doorSignalService.getHistory(deviceId);
	}
}
