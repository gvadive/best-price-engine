import { useState, useRef } from "react";
import { useAuth } from "../AuthContext";
import * as api from "../api";

export default function ComparePage({ setView }) {
  const { user } = useAuth();

  const [product, setProduct] = useState("USB Cable");
  const [quantity, setQuantity] = useState(1);
  const [maxDeliveryDays, setMaxDeliveryDays] = useState("");
  const [minRating, setMinRating] = useState("");
  const [strategy, setStrategy] = useState("TOTAL_COST");
  const [suggestions, setSuggestions] = useState([]);
  const debounceRef = useRef(null);

  const [status, setStatus] = useState("");
  const [offers, setOffers] = useState([]);
  const [searched, setSearched] = useState(false);

  const [selectedOffer, setSelectedOffer] = useState(null);
  const [actionType, setActionType] = useState(null); // "order" | "bulk" | "watch" | null
  const [actionResult, setActionResult] = useState("");
  const [bulkResult, setBulkResult] = useState("");
  const [activeBulkPricingRequestId, setActiveBulkPricingRequestId] = useState(null);

  const [orderQuantity, setOrderQuantity] = useState(1);
  const [orderFulfillmentMode, setOrderFulfillmentMode] = useState("EXACT_QUANTITY");
  const [bulkQuantity, setBulkQuantity] = useState(100);
  const [watchQuantity, setWatchQuantity] = useState(1);
  const [watchMaxPrice, setWatchMaxPrice] = useState("");
  const [watchFulfillmentMode, setWatchFulfillmentMode] = useState("EXACT_QUANTITY");
  const [watchExpiresAfter, setWatchExpiresAfter] = useState(86400);

  function onProductInput(value) {
    setProduct(value);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      const res = await api.searchProducts(value.trim());
      if (res.ok) setSuggestions(res.data);
    }, 200);
  }

  async function runComparison(event) {
    event.preventDefault();
    setStatus("Comparing...");
    setOffers([]);
    setSearched(false);
    setActionType(null);

    const params = { product: product.trim(), quantity, strategy };
    if (maxDeliveryDays) params.maxDeliveryDays = maxDeliveryDays;
    if (minRating) params.minRating = minRating;

    const res = await api.compareOffers(params);
    setSearched(true);
    if (!res.ok) {
      setStatus(`Error: request failed (${res.status})`);
      return;
    }
    setOffers(res.data);
    setStatus(
      res.data.length === 0
        ? "No offers match your criteria."
        : `${res.data.length} offer(s) found. Best match highlighted.`
    );
  }

  function unitPriceAt(offer, qty) {
    const applicable = offer.quantityTiers.filter((t) => qty >= t.minQuantity).map((t) => t.unitPrice);
    return applicable.length ? Math.min(...applicable) : offer.basePrice;
  }

  function openAction(type, offer) {
    if (!user) {
      setView("login");
      return;
    }
    setSelectedOffer(offer);
    setActionType(type);
    setActionResult("");
    setBulkResult("");
    setActiveBulkPricingRequestId(null);
  }

  async function submitOrder() {
    const result = await api.placeOrder({
      offerId: selectedOffer.id,
      quantity: Number(orderQuantity),
      quotedPriceVersion: selectedOffer.priceVersion,
      fulfillmentMode: orderFulfillmentMode,
      bulkPricingRequestId: null,
    });
    renderOrderResult(result.data);
  }

  async function submitBulkRequest() {
    const res = await api.requestBulkPricing(selectedOffer.id, Number(bulkQuantity));
    const quote = res.data;
    setActiveBulkPricingRequestId(quote.id);
    setBulkResult(
      `Negotiated price: $${quote.negotiatedUnitPrice.toFixed(2)}/unit for ${quote.requestedQuantity} units ` +
        `(valid until ${new Date(quote.expiresAt).toLocaleTimeString()})`
    );
  }

  async function submitOrderFromBulk() {
    const result = await api.placeOrder({
      offerId: selectedOffer.id,
      quantity: Number(bulkQuantity),
      quotedPriceVersion: 0,
      fulfillmentMode: "EXACT_QUANTITY",
      bulkPricingRequestId: activeBulkPricingRequestId,
    });
    renderOrderResult(result.data);
  }

  async function submitPriceWatch() {
    const res = await api.placeWatch({
      offerId: selectedOffer.id,
      quantity: Number(watchQuantity),
      maxAcceptablePrice: Number(watchMaxPrice),
      fulfillmentMode: watchFulfillmentMode,
      expiresAfterSeconds: Number(watchExpiresAfter),
    });
    const watch = res.data;
    setActionResult(
      `Price watch order #${watch.id}: ${watch.status}` +
        (watch.status === "FILLED"
          ? ` (order #${watch.resultingOrderId})`
          : ` (expires ${new Date(watch.expiresAt).toLocaleString()})`)
    );
  }

  function renderOrderResult(order) {
    setActionResult(
      `Order status: ${order.status} -- filled ${order.filledQuantity}/${order.requestedQuantity}` +
        (order.status !== "ACCEPTED" && order.rejectReason
          ? ` (${order.rejectReason})`
          : ` at $${order.unitPrice.toFixed(2)}/unit`)
    );
  }

  return (
    <main>
      <h1>Best Price Engine</h1>
      <p className="subtitle">Compare dummy-retailer offers for a product under your own criteria.</p>

      <form id="compare-form" onSubmit={runComparison}>
        <label>
          Product
          <input
            type="text"
            id="product"
            name="product"
            value={product}
            onChange={(e) => onProductInput(e.target.value)}
            required
            autoComplete="off"
            list="product-suggestions"
          />
          <datalist id="product-suggestions">
            {suggestions.map((name) => (
              <option value={name} key={name} />
            ))}
          </datalist>
        </label>
        <label>
          Quantity
          <input
            type="number"
            id="quantity"
            name="quantity"
            value={quantity}
            min="1"
            required
            onChange={(e) => setQuantity(e.target.value)}
          />
        </label>
        <label>
          Max delivery days (optional)
          <input
            type="number"
            id="maxDeliveryDays"
            name="maxDeliveryDays"
            min="0"
            value={maxDeliveryDays}
            onChange={(e) => setMaxDeliveryDays(e.target.value)}
          />
        </label>
        <label>
          Min rating (optional)
          <input
            type="number"
            id="minRating"
            name="minRating"
            min="0"
            max="5"
            step="0.1"
            value={minRating}
            onChange={(e) => setMinRating(e.target.value)}
          />
        </label>
        <label>
          Strategy
          <select id="strategy" name="strategy" value={strategy} onChange={(e) => setStrategy(e.target.value)}>
            <option value="TOTAL_COST">Cheapest total for this quantity</option>
            <option value="UNIT_PRICE">Cheapest per unit</option>
          </select>
        </label>
        <button type="submit">Compare</button>
      </form>

      <p id="status" role="status">{status}</p>

      <table id="results" hidden={!searched || offers.length === 0}>
        <thead>
          <tr>
            <th>Retailer</th>
            <th>Unit price</th>
            <th>Total for quantity</th>
            <th>Delivery days</th>
            <th>Rating</th>
            <th>Stock</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody id="results-body">
          {offers.map((offer, index) => {
            const unitPrice = unitPriceAt(offer, quantity);
            const totalPrice = (unitPrice * quantity).toFixed(2);
            return (
              <tr key={offer.id} className={index === 0 ? "cheapest" : undefined}>
                <td>{offer.retailer}</td>
                <td>${unitPrice.toFixed(2)}</td>
                <td>${totalPrice}</td>
                <td>{offer.deliveryDays}</td>
                <td>{offer.rating}</td>
                <td>{offer.availableQuantity}</td>
                <td className="actions">
                  <button type="button" className="action-order-btn" onClick={() => openAction("order", offer)}>
                    Order
                  </button>
                  <button type="button" className="action-bulk-btn" onClick={() => openAction("bulk", offer)}>
                    Bulk Pricing
                  </button>
                  <button type="button" className="action-watch-btn" onClick={() => openAction("watch", offer)}>
                    Price Watch
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      <section id="action-panel" hidden={!actionType}>
        <h2 id="action-panel-title">
          {selectedOffer &&
            (actionType === "order"
              ? `Place an order -- ${selectedOffer.retailer}, ${selectedOffer.productName}`
              : actionType === "bulk"
              ? `Request bulk pricing -- ${selectedOffer.retailer}, ${selectedOffer.productName}`
              : actionType === "watch"
              ? `Create a price watch order -- ${selectedOffer.retailer}, ${selectedOffer.productName}`
              : "")}
        </h2>

        <div id="order-fields" hidden={actionType !== "order"}>
          <label>
            Quantity
            <input id="order-quantity" type="number" min="1" value={orderQuantity} onChange={(e) => setOrderQuantity(e.target.value)} />
          </label>
          <label>
            Fulfillment
            <select id="order-fulfillment-mode" value={orderFulfillmentMode} onChange={(e) => setOrderFulfillmentMode(e.target.value)}>
              <option value="EXACT_QUANTITY">Exact quantity only</option>
              <option value="BEST_EFFORT">Best effort (partial OK)</option>
            </select>
          </label>
          <button id="submit-order" onClick={submitOrder}>Place Order</button>
        </div>

        <div id="bulk-fields" hidden={actionType !== "bulk"}>
          <label>
            Requested quantity (bulk)
            <input id="bulk-quantity" type="number" min="1" value={bulkQuantity} onChange={(e) => setBulkQuantity(e.target.value)} />
          </label>
          <button id="submit-bulk-request" onClick={submitBulkRequest}>Request Bulk Pricing</button>
          <div id="bulk-result">{bulkResult}</div>
          <button id="submit-order-from-bulk" hidden={!activeBulkPricingRequestId} onClick={submitOrderFromBulk}>
            Place Order at Negotiated Price
          </button>
        </div>

        <div id="watch-fields" hidden={actionType !== "watch"}>
          <label>
            Quantity
            <input id="watch-quantity" type="number" min="1" value={watchQuantity} onChange={(e) => setWatchQuantity(e.target.value)} />
          </label>
          <label>
            Max acceptable price
            <input id="watch-max-price" type="number" step="0.01" value={watchMaxPrice} onChange={(e) => setWatchMaxPrice(e.target.value)} />
          </label>
          <label>
            Fulfillment
            <select id="watch-fulfillment-mode" value={watchFulfillmentMode} onChange={(e) => setWatchFulfillmentMode(e.target.value)}>
              <option value="EXACT_QUANTITY">Exact quantity only</option>
              <option value="BEST_EFFORT">Best effort (partial OK)</option>
            </select>
          </label>
          <label>
            Expires after (seconds)
            <input id="watch-expires-after" type="number" value={watchExpiresAfter} onChange={(e) => setWatchExpiresAfter(e.target.value)} />
          </label>
          <button id="submit-price-watch" onClick={submitPriceWatch}>Create Price Watch Order</button>
        </div>

        <div id="action-result" role="status">{actionResult}</div>
      </section>
    </main>
  );
}
