package com.smartdoor.backend.access;

import static org.mockito.Mockito.*;
import com.smartdoor.backend.infrastructure.outbox.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpiredDeliveryTests {
    @Test void expiredAlarmIsNeverReportedAsDeliveredOrRetried() throws Exception {
        var outbox=mock(OutboxService.class);
        var transport=mock(OutboxTransport.class);
        var claim=new OutboxService.Claim(new OutboxDelivery(1L,"ALARM",2L,"NODE-2","{}"),"lease");
        when(transport.supports("ALARM")).thenReturn(true);
        when(outbox.claim(List.of("ALARM"),10)).thenReturn(List.of(claim));
        doThrow(new ExpiredDeliveryException()).when(transport).deliver(claim.delivery());
        new OutboxDispatcher(outbox,List.of(transport)).dispatch();
        verify(outbox).expired(claim);
        verify(outbox,never()).delivered(any()); verify(outbox,never()).failed(any(),any());
    }
}
