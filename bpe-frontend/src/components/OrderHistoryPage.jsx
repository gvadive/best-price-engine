import { useEffect, useState } from "react";
import * as api from "../api";

export default function OrderHistoryPage() {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.orderHistory().then((res) => {
      if (res.ok) setOrders(res.data);
      else setError("Could not load order history.");
    });
  }, []);

  return (
    <main>
      <h1>Order History</h1>

      {error && <p className="form-error">{error}</p>}
      {orders === null && !error && <p id="history-status">Loading...</p>}
      {orders !== null && orders.length === 0 && <p id="history-status">You haven't placed any orders yet.</p>}

      {orders !== null && orders.length > 0 && (
        <table id="history-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Retailer</th>
              <th>Product</th>
              <th>Requested</th>
              <th>Filled</th>
              <th>Unit Price</th>
              <th>Total</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody id="history-body">
            {orders.map((order) => (
              <tr key={order.id} className={`status-${order.status.toLowerCase()}`}>
                <td>{new Date(order.createdAt).toLocaleString()}</td>
                <td>{order.retailer}</td>
                <td>{order.productName}</td>
                <td>{order.requestedQuantity}</td>
                <td>{order.filledQuantity}</td>
                <td>${order.unitPrice.toFixed(2)}</td>
                <td>${order.filledTotal.toFixed(2)}</td>
                <td>
                  <span className={`status-badge status-${order.status.toLowerCase()}`}>{order.status}</span>
                  {order.rejectReason && <div className="reject-reason">{order.rejectReason}</div>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
