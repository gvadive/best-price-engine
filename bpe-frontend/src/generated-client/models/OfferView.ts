/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { QuantityTierView } from './QuantityTierView';
export type OfferView = {
    id?: number;
    priceVersion?: number;
    productName?: string;
    retailer?: string;
    basePrice?: number;
    deliveryDays?: number;
    rating?: number;
    inStock?: boolean;
    availableQuantity?: number;
    quantityTiers?: Array<QuantityTierView>;
};

