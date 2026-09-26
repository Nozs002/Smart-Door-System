package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.smartdoor.backend.mqtt.MqttPublisher;

import tools.jackson.databind.ObjectMapper;

class DoorCommandServiceTests {

	@Test
	void openPublishesCommandToDeviceTopic() throws Exception {
		MqttPublisher publisher = Mockito.mock(MqttPublisher.class);
		DoorCommandService service = new DoorCommandService(publisher, new ObjectMapper());

		DoorCommandResponse response = service.open("door-01");

		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(publisher).publish(Mockito.eq("smart-door/door-01/command"), payloadCaptor.capture());
		DoorCommand payload = new ObjectMapper().readValue(payloadCaptor.getValue(), DoorCommand.class);
		assertThat(payload.command()).isEqualTo("OPEN");
		assertThat(payload.requestId()).isEqualTo(response.requestId());
		assertThat(response.status()).isEqualTo("ACCEPTED");
	}
}
