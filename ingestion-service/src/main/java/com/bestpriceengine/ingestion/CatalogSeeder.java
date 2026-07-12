package com.bestpriceengine.ingestion;

import com.bestpriceengine.ingestion.service.CatalogService;
import com.bestpriceengine.ingestion.repository.OfferRepository;
import com.bestpriceengine.ingestion.web.dto.OfferUploadRequest;
import com.bestpriceengine.ingestion.web.dto.QuantityTierDto;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a small demo catalog on startup so the app has something to compare
 * out of the box, instead of requiring manual curl calls first. Only runs
 * against an empty catalog -- each test suite seeds its own uniquely-named
 * products (see conftest.py / test_compare_flow.py), so this never collides
 * with them.
 */
@Component
public class CatalogSeeder implements CommandLineRunner {

    private final OfferRepository offerRepository;
    private final CatalogService catalogService;

    public CatalogSeeder(OfferRepository offerRepository, CatalogService catalogService) {
        this.offerRepository = offerRepository;
        this.catalogService = catalogService;
    }

    @Override
    public void run(String... args) {
        if (offerRepository.count() > 0) {
            return;
        }
        for (OfferUploadRequest offer : demoCatalog()) {
            catalogService.addOffer(offer);
        }
    }

    private List<OfferUploadRequest> demoCatalog() {
        return List.of(
                new OfferUploadRequest("Wireless Mouse", "RetailerA", 19.99, 3, 4.3, true, 250,
                        List.of(new QuantityTierDto(10, 16.99))),
                new OfferUploadRequest("Wireless Mouse", "RetailerB", 22.50, 1, 4.7, true, 120, List.of()),
                new OfferUploadRequest("Wireless Mouse", "RetailerC", 17.99, 5, 3.9, false, 0, List.of()),

                new OfferUploadRequest("Mechanical Keyboard", "RetailerA", 89.99, 2, 4.6, true, 80, List.of()),
                new OfferUploadRequest("Mechanical Keyboard", "RetailerB", 109.00, 1, 4.8, true, 40,
                        List.of(new QuantityTierDto(5, 95.00))),
                new OfferUploadRequest("Mechanical Keyboard", "RetailerC", 79.50, 6, 4.1, true, 200, List.of()),

                new OfferUploadRequest("Bluetooth Headphones", "RetailerA", 59.99, 3, 4.4, true, 150, List.of()),
                new OfferUploadRequest("Bluetooth Headphones", "RetailerB", 54.00, 4, 4.0, true, 90, List.of()),
                new OfferUploadRequest("Bluetooth Headphones", "RetailerC", 64.99, 1, 4.9, true, 60,
                        List.of(new QuantityTierDto(10, 49.99))),

                new OfferUploadRequest("27-inch 4K Monitor", "RetailerA", 329.00, 5, 4.5, true, 30, List.of()),
                new OfferUploadRequest("27-inch 4K Monitor", "RetailerB", 349.99, 2, 4.7, true, 15, List.of()),
                new OfferUploadRequest("27-inch 4K Monitor", "RetailerC", 299.00, 7, 3.8, true, 4, List.of()),

                new OfferUploadRequest("Portable SSD 1TB", "RetailerA", 89.00, 2, 4.6, true, 200,
                        List.of(new QuantityTierDto(5, 79.00))),
                new OfferUploadRequest("Portable SSD 1TB", "RetailerB", 94.50, 1, 4.8, true, 75, List.of()),
                new OfferUploadRequest("Portable SSD 1TB", "RetailerC", 84.99, 4, 4.2, true, 130, List.of()),

                new OfferUploadRequest("Webcam 1080p", "RetailerA", 34.99, 3, 4.0, true, 100, List.of()),
                new OfferUploadRequest("Webcam 1080p", "RetailerB", 29.99, 6, 3.7, true, 220, List.of()),
                new OfferUploadRequest("Webcam 1080p", "RetailerC", 39.99, 1, 4.6, true, 50, List.of()),

                new OfferUploadRequest("Phone Case", "RetailerA", 9.99, 2, 4.1, true, 500, List.of()),
                new OfferUploadRequest("Phone Case", "RetailerB", 12.50, 1, 4.5, true, 300,
                        List.of(new QuantityTierDto(20, 8.99))),
                new OfferUploadRequest("Phone Case", "RetailerC", 7.99, 5, 3.2, true, 400, List.of())
        );
    }
}
