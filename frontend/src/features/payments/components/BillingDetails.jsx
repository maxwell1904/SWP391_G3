import { Badge } from '@mantine/core'
import { CreditCard, ReceiptText } from 'lucide-react'
import { formatDateTime, formatMoney } from '../../../utils/format'

export function BillingDetails({ detail, loading, error }) {
  if (loading) return <p className="emptyText">Loading invoice and payment status...</p>
  if (error) return <p className="errorText">{error}</p>
  if (!detail) return <p className="emptyText">Select a booking to view its billing details.</p>

  const invoice = detail.invoice
  const payments = detail.payments || []
  const refunds = detail.refunds || []

  return (
    <div className="billingDetails">
      <div className="billingHeading">
        <span>
          <ReceiptText size={19} />
          <span>
            <strong>{invoice?.invoiceCode || 'Invoice not issued'}</strong>
            <small>{detail.bookingCode} · {detail.fieldName}</small>
          </span>
        </span>
        <Badge variant="light" color={statusColor(detail.paymentStatus)}>
          {statusLabel(detail.paymentStatus)}
        </Badge>
      </div>

      <div className="invoiceAmounts">
        <Amount label="Field rental" value={invoice?.fieldAmount ?? detail.fieldPriceAmount} />
        <Amount label="Extra services" value={invoice?.serviceAmount ?? detail.serviceTotalAmount} />
        <Amount
          label="Discounts"
          value={invoice?.discountAmount ?? Number(detail.promotionDiscountAmount || 0) + Number(detail.membershipDiscountAmount || 0)}
          negative
        />
        <Amount label="Total" value={invoice?.totalAmount ?? detail.totalAmount} strong />
        <Amount label="Paid" value={invoice?.paidAmount ?? detail.paidAmount} />
        <Amount label="Remaining" value={invoice?.remainingAmount ?? detail.remainingAmount} />
        {Number(invoice?.refundAmount || 0) > 0 && <Amount label="Refunded" value={invoice.refundAmount} />}
      </div>

      <div className="paymentTimeline">
        <h4>Transactions</h4>
        {payments.length ? payments.map(payment => (
          <div className="paymentRecord" key={payment.paymentId}>
            <CreditCard size={17} />
            <span>
              <strong>{payment.paymentCode} · {optionLabel(payment.paymentOption)}</strong>
              <small>{payment.paymentMethod.replaceAll('_', ' ')} · {formatDateTime(payment.paidAt)}</small>
              {payment.providerFeeTracked && (
                <small>Processor fee {formatMoney(payment.providerFeeAmount)} · provider net {formatMoney(payment.providerNetAmount)}</small>
              )}
            </span>
            <span className="paymentRecordValue">
              <strong>{formatMoney(payment.amount)}</strong>
              <small>{payment.status}</small>
            </span>
          </div>
        )) : <p className="emptyText">No payment transactions yet.</p>}
      </div>
      {refunds.length > 0 && (
        <div className="paymentTimeline">
          <h4>Refunds</h4>
          {refunds.map(refund => (
            <div className="paymentRecord" key={refund.refundId}>
              <ReceiptText size={17} />
              <span>
                <strong>{refund.refundCode}</strong>
                <small>{refund.paymentMethod?.replaceAll('_', ' ') || 'Payment'} · {refund.gatewayMessage || refund.refundReason || 'Refund case'}</small>
              </span>
              <span className="paymentRecordValue">
                <strong>{formatMoney(refund.refundAmount)}</strong>
                <small>{refund.status}</small>
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function Amount({ label, value, negative = false, strong = false }) {
  return (
    <div className={strong ? 'invoiceAmount strong' : 'invoiceAmount'}>
      <span>{label}</span>
      <strong>{negative && Number(value || 0) > 0 ? '-' : ''}{formatMoney(value)}</strong>
    </div>
  )
}

function statusLabel(status) {
  return String(status || 'unpaid').replaceAll('_', ' ')
}

function statusColor(status) {
  if (status === 'paid') return 'green'
  if (status === 'partially_paid') return 'yellow'
  if (status === 'refunded' || status === 'partially_refunded') return 'blue'
  if (status === 'failed' || status === 'expired') return 'red'
  return 'gray'
}

function optionLabel(option) {
  if (option === 'full') return 'Full payment'
  if (option === 'remaining') return 'Remaining balance'
  return 'Deposit'
}
