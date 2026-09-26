package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import tools.jackson.databind.ObjectMapper;

class DoorSignalServiceTests {

	@Test
	void processParsesAndStoresDoorStatus() {
		DoorSignalHistoryRepository repository = Mockito.mock(DoorSignalHistoryRepository.class);
		DoorSignalService service = new DoorSignalService(repository, new ObjectMapper());

		service.process(
				"smart-door/door-01/status",
				"{\"status\":\"OPENED\",\"requestId\":\"abc-123\"}");

		ArgumentCaptor<DoorSignalHistory> historyCaptor = ArgumentCaptor.forClass(DoorSignalHistory.class);
		verify(repository).save(historyCaptor.capture());
		DoorSignalHistory history = historyCaptor.getValue();
		assertThat(history.getDeviceId()).isEqualTo("door-01");
		assertThat(history.getSignalType()).isEqualTo("STATUS");
		assertThat(history.getStatus()).isEqualTo("OPENED");
		assertThat(history.getRequestId()).isEqualTo("abc-123");
		assertThat(history.getReceivedAt()).isNotNull();
	}
}
