package ua.newerp.api.delivery;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import ua.newerp.application.delivery.DeliveryService;
import ua.newerp.application.delivery.NovaPoshtaClient;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.TrackingRefreshResult;
import ua.newerp.domain.common.DomainException;

class NovaPoshtaJobsTest {

    final DeliveryService delivery = mock(DeliveryService.class);
    final ShipmentService shipments = mock(ShipmentService.class);
    final NovaPoshtaClient client = mock(NovaPoshtaClient.class);
    final NovaPoshtaJobs jobs = new NovaPoshtaJobs(delivery, shipments, client);

    @Test
    void withoutAKeyNothingIsCalled() {
        jobs.refreshTracking();
        jobs.refreshWarehouses();

        verifyNoInteractions(delivery, shipments);
    }

    @Test
    void withAKeyBothRefreshAndAFailureDoesNotStopTheScheduler() {
        when(client.isConfigured()).thenReturn(true);
        when(delivery.refreshTracking()).thenReturn(new TrackingRefreshResult(true, 4, 1));
        when(shipments.refreshWarehouses()).thenThrow(new DomainException("порожній довідник"));

        jobs.refreshTracking();
        jobs.refreshWarehouses(); // logged, not thrown

        verify(delivery).refreshTracking();
        verify(shipments).refreshWarehouses();
    }
}
