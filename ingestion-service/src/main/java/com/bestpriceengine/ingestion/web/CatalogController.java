package com.bestpriceengine.ingestion.web;

import com.bestpriceengine.ingestion.service.CatalogService;
import com.bestpriceengine.ingestion.web.dto.OfferDto;
import com.bestpriceengine.ingestion.web.dto.OfferUploadRequest;
import com.bestpriceengine.ingestion.web.dto.PriceUpdateRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/offers")
    public ResponseEntity<OfferDto> addOffer(@RequestBody OfferUploadRequest request) {
        var saved = catalogService.addOffer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OfferDto.from(saved));
    }

    @GetMapping("/offers")
    public List<OfferDto> offersForProduct(@RequestParam String product) {
        return catalogService.offersForProduct(product).stream()
                .map(OfferDto::from)
                .toList();
    }

    // Simulation endpoint standing in for a retailer's live price feed --
    // updates the price and bumps priceVersion, which is what makes stale
    // order quotes detectable.
    @PutMapping("/offers/{id}/price")
    public OfferDto updatePrice(@PathVariable Long id, @RequestBody PriceUpdateRequest request) {
        return OfferDto.from(catalogService.updatePrice(id, request.newBasePrice()));
    }
}
