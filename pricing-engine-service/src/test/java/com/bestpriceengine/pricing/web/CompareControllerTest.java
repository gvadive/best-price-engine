package com.bestpriceengine.pricing.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestpriceengine.pricing.client.IngestionClient;
import com.bestpriceengine.pricing.domain.OfferView;
import com.bestpriceengine.pricing.domain.PricingStrategy;
import com.bestpriceengine.pricing.engine.PriceRanker;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CompareControllerTest {

    @Test
    void comparePassesProductToIngestionClientAndReturnsCheapestFirst() {

        IngestionClient mockClient = mock(IngestionClient.class);

        OfferView expensiveOffer = new OfferView(
                1L, 1L, "usb-cable", "RetailerA",
                5.00, 2, 4.5, true, 100, List.of()
        );
        OfferView cheapOffer = new OfferView(
                2L, 1L, "usb-cable", "RetailerB",
                2.00, 3, 4.0, true, 50, List.of()
        );
        List<OfferView> offersFromIngestion = List.of(expensiveOffer, cheapOffer);

        when(mockClient.offersFor("usb-cable")).thenReturn(offersFromIngestion);

        CompareController controller = new CompareController(mockClient, new PriceRanker());

        List<OfferView> result = controller.compare("usb-cable", 1, null, null, PricingStrategy.TOTAL_COST);

        assertEquals("RetailerB", result.get(0).retailer());
        verify(mockClient).offersFor("usb-cable");
    }
}
