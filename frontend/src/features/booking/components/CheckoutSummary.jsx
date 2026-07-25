import { formatMoney } from '../../../utils/format'

export function CheckoutSummary({ checkout, loading, error }) {
  if (loading) return <p className="emptyText">Updating checkout…</p>
  if (error) return <p className="errorText">{error}</p>
  if (!checkout) return <p className="emptyText">Choose an available slot to see pricing.</p>
  const promotionDiscount = Number(checkout.promotionDiscountAmount || 0)
  const membershipDiscount = Number(checkout.membershipDiscountAmount || 0)
  return (
    <div className="checkoutSummary">
      <Line label="Field price" value={formatMoney(checkout.fieldPriceAmount)} />
      <Line label="Services" value={formatMoney(checkout.serviceTotalAmount)} />
      {promotionDiscount > 0 && <Line label="Promotion" value={`-${formatMoney(checkout.promotionDiscountAmount)}`} />}
      {membershipDiscount > 0 && <Line label="Membership" value={`-${formatMoney(checkout.membershipDiscountAmount)}`} />}
      <Line label="Deposit" value={formatMoney(checkout.depositAmount)} />
      <Line label="Total" value={formatMoney(checkout.totalAmount)} strong />
    </div>
  )
}

function Line({ label, value, strong }) {
  return (
    <div className={strong ? 'summaryLine strong' : 'summaryLine'}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}
