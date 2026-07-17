/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { OfferView } from '../models/OfferView';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CompareControllerService {
    /**
     * @param product
     * @param quantity
     * @param maxDeliveryDays
     * @param minRating
     * @param strategy
     * @returns OfferView OK
     * @throws ApiError
     */
    public static compare(
        product: string,
        quantity: number = 1,
        maxDeliveryDays?: number,
        minRating?: number,
        strategy: 'UNIT_PRICE' | 'TOTAL_COST' = 'TOTAL_COST',
    ): CancelablePromise<Array<OfferView>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/compare',
            query: {
                'product': product,
                'quantity': quantity,
                'maxDeliveryDays': maxDeliveryDays,
                'minRating': minRating,
                'strategy': strategy,
            },
        });
    }
}
