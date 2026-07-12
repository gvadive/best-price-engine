const API_BASE = window.API_BASE || "/api";

const form = document.getElementById("compare-form");
const statusEl = document.getElementById("status");
const resultsTable = document.getElementById("results");
const resultsBody = document.getElementById("results-body");

const actionPanel = document.getElementById("action-panel");
const actionPanelTitle = document.getElementById("action-panel-title");
const orderFields = document.getElementById("order-fields");
const bulkFields = document.getElementById("bulk-fields");
const watchFields = document.getElementById("watch-fields");
const actionResult = document.getElementById("action-result");
const bulkResult = document.getElementById("bulk-result");
const submitOrderFromBulkBtn = document.getElementById("submit-order-from-bulk");

let selectedOffer = null; // { id, priceVersion, retailer, productName }
let activeBulkPricingRequestId = null;

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  await runComparison();
});

async function runComparison() {
  const product = document.getElementById("product").value.trim();
  const quantity = document.getElementById("quantity").value;
  const maxDeliveryDays = document.getElementById("maxDeliveryDays").value;
  const minRating = document.getElementById("minRating").value;
  const strategy = document.getElementById("strategy").value;

  const params = new URLSearchParams({ product, quantity, strategy });
  if (maxDeliveryDays) params.set("maxDeliveryDays", maxDeliveryDays);
  if (minRating) params.set("minRating", minRating);

  statusEl.textContent = "Comparing...";
  resultsTable.hidden = true;
  resultsBody.innerHTML = "";
  actionPanel.hidden = true;

  try {
    const response = await fetch(`${API_BASE}/compare?${params.toString()}`);
    if (!response.ok) {
      throw new Error(`Request failed: ${response.status}`);
    }
    const offers = await response.json();
    renderResults(offers, quantity);
  } catch (err) {
    statusEl.textContent = `Error: ${err.message}`;
  }
}

function renderResults(offers, quantity) {
  if (offers.length === 0) {
    statusEl.textContent = "No offers match your criteria.";
    return;
  }

  statusEl.textContent = `${offers.length} offer(s) found. Best match highlighted.`;
  resultsTable.hidden = false;

  offers.forEach((offer, index) => {
    const row = document.createElement("tr");
    if (index === 0) {
      row.classList.add("cheapest");
    }

    const unitPrice = unitPriceAt(offer, quantity);
    const totalPrice = (unitPrice * quantity).toFixed(2);

    row.innerHTML = `
      <td>${offer.retailer}</td>
      <td>$${unitPrice.toFixed(2)}</td>
      <td>$${totalPrice}</td>
      <td>${offer.deliveryDays}</td>
      <td>${offer.rating}</td>
      <td>${offer.availableQuantity}</td>
      <td class="actions">
        <button type="button" class="action-order-btn">Order</button>
        <button type="button" class="action-bulk-btn">Bulk Pricing</button>
        <button type="button" class="action-watch-btn">Price Watch</button>
      </td>
    `;

    for (const btn of row.querySelectorAll("button")) {
      btn.dataset.offerId = offer.id;
      btn.dataset.priceVersion = offer.priceVersion;
      btn.dataset.retailer = offer.retailer;
      btn.dataset.productName = offer.productName;
    }

    resultsBody.appendChild(row);
  });
}

resultsBody.addEventListener("click", (event) => {
  const btn = event.target.closest("button");
  if (!btn) return;

  selectedOffer = {
    id: Number(btn.dataset.offerId),
    priceVersion: Number(btn.dataset.priceVersion),
    retailer: btn.dataset.retailer,
    productName: btn.dataset.productName,
  };

  if (btn.classList.contains("action-order-btn")) {
    openActionPanel("order", `Place an order -- ${selectedOffer.retailer}, ${selectedOffer.productName}`);
  } else if (btn.classList.contains("action-bulk-btn")) {
    openActionPanel("bulk", `Request bulk pricing -- ${selectedOffer.retailer}, ${selectedOffer.productName}`);
  } else if (btn.classList.contains("action-watch-btn")) {
    openActionPanel("watch", `Create a price watch order -- ${selectedOffer.retailer}, ${selectedOffer.productName}`);
  }
});

function openActionPanel(type, title) {
  actionPanel.hidden = false;
  actionPanelTitle.textContent = title;
  actionResult.textContent = "";
  bulkResult.textContent = "";
  submitOrderFromBulkBtn.hidden = true;
  activeBulkPricingRequestId = null;

  orderFields.hidden = type !== "order";
  bulkFields.hidden = type !== "bulk";
  watchFields.hidden = type !== "watch";
}

document.getElementById("submit-order").addEventListener("click", async () => {
  const quantity = Number(document.getElementById("order-quantity").value);
  const fulfillmentMode = document.getElementById("order-fulfillment-mode").value;

  const result = await placeOrder({
    offerId: selectedOffer.id,
    quantity,
    quotedPriceVersion: selectedOffer.priceVersion,
    fulfillmentMode,
    bulkPricingRequestId: null,
  });
  renderOrderResult(result);
});

document.getElementById("submit-bulk-request").addEventListener("click", async () => {
  const requestedQuantity = Number(document.getElementById("bulk-quantity").value);

  const response = await fetch(`${API_BASE}/bulk-pricing-requests`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ offerId: selectedOffer.id, requestedQuantity }),
  });
  const quote = await response.json();
  activeBulkPricingRequestId = quote.id;

  bulkResult.textContent =
    `Negotiated price: $${quote.negotiatedUnitPrice.toFixed(2)}/unit for ${quote.requestedQuantity} units ` +
    `(valid until ${new Date(quote.expiresAt).toLocaleTimeString()})`;
  submitOrderFromBulkBtn.hidden = false;
});

submitOrderFromBulkBtn.addEventListener("click", async () => {
  const requestedQuantity = Number(document.getElementById("bulk-quantity").value);

  const result = await placeOrder({
    offerId: selectedOffer.id,
    quantity: requestedQuantity,
    quotedPriceVersion: 0,
    fulfillmentMode: "EXACT_QUANTITY",
    bulkPricingRequestId: activeBulkPricingRequestId,
  });
  renderOrderResult(result);
});

document.getElementById("submit-price-watch").addEventListener("click", async () => {
  const quantity = Number(document.getElementById("watch-quantity").value);
  const maxAcceptablePrice = Number(document.getElementById("watch-max-price").value);
  const fulfillmentMode = document.getElementById("watch-fulfillment-mode").value;
  const expiresAfterSeconds = Number(document.getElementById("watch-expires-after").value);

  const response = await fetch(`${API_BASE}/price-watch-orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ offerId: selectedOffer.id, quantity, maxAcceptablePrice, fulfillmentMode, expiresAfterSeconds }),
  });
  const watch = await response.json();

  actionResult.textContent = `Price watch order #${watch.id}: ${watch.status}` +
    (watch.status === "FILLED" ? ` (order #${watch.resultingOrderId})` : ` (expires ${new Date(watch.expiresAt).toLocaleString()})`);
});

async function placeOrder(payload) {
  const response = await fetch(`${API_BASE}/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return response.json();
}

function renderOrderResult(order) {
  actionResult.textContent =
    `Order status: ${order.status} -- filled ${order.filledQuantity}/${order.requestedQuantity}` +
    (order.status !== "ACCEPTED" && order.rejectReason ? ` (${order.rejectReason})` : ` at $${order.unitPrice.toFixed(2)}/unit`);
}

function unitPriceAt(offer, quantity) {
  const applicable = offer.quantityTiers
    .filter((t) => quantity >= t.minQuantity)
    .map((t) => t.unitPrice);
  return applicable.length ? Math.min(...applicable) : offer.basePrice;
}
