import { Badge, Button } from '@mantine/core'
import { formatDateTime, formatMoney, humanizeStatus, paymentMethodLabel } from '../../../utils/format'

export function RefundCaseList({ refunds = [], onUpdate }) {
  if (!refunds.length) return <p className="emptyText">No refund cases.</p>

  return (
    <div className="supportCaseList">
      {refunds.map(refund => (
        <article className="supportCase" key={refund.refundId}>
          <div className="supportCaseHeader">
            <div>
              <strong>{refund.refundCode} · {refund.bookingCode}</strong>
              <small>{refund.customer} · {paymentMethodLabel(refund.paymentMethod)}</small>
            </div>
            <Badge variant="light" color={refundStatusColor(refund.status)}>{humanizeStatus(refund.status)}</Badge>
          </div>
          <div className="supportCaseAmount">{formatMoney(refund.refundAmount)}</div>
          <p>{refund.refundReason || 'No refund reason was provided.'}</p>
          <div className="supportCaseMeta">
            {refund.requestedAt && <span>Requested {formatDateTime(refund.requestedAt)}</span>}
            {refund.requestedBy && <span>Requested by {refund.requestedBy}</span>}
            {refund.processedBy && <span>Last handled by {refund.processedBy}</span>}
            {refund.gatewayMessage && <span>Processor update: {refund.gatewayMessage}</span>}
          </div>
          {onUpdate && <RefundActions refund={refund} onUpdate={onUpdate} />}
        </article>
      ))}
    </div>
  )
}

function RefundActions({ refund, onUpdate }) {
  if (refund.status === 'requested') {
    return (
      <div className="supportCaseActions">
        <Button size="xs" color="green" onClick={() => onUpdate(refund, 'approved')}>Approve</Button>
        <Button size="xs" color="red" variant="light" onClick={() => onUpdate(refund, 'rejected')}>Reject</Button>
      </div>
    )
  }
  if (refund.status === 'approved') {
    const next = refund.paymentMethod === 'paypal_sandbox' ? 'processing' : 'completed'
    return (
      <div className="supportCaseActions">
        <Button size="xs" variant="light" onClick={() => onUpdate(refund, next)}>
          {refund.paymentMethod === 'paypal_sandbox' ? 'Send refund to PayPal' : 'Record cash refund'}
        </Button>
      </div>
    )
  }
  if (refund.paymentMethod === 'paypal_sandbox' && ['processing', 'failed'].includes(refund.status)) {
    return (
      <div className="supportCaseActions">
        <Button size="xs" variant="light" onClick={() => onUpdate(refund, 'processing')}>Retry or check PayPal</Button>
      </div>
    )
  }
  return null
}

function refundStatusColor(status) {
  if (status === 'completed') return 'green'
  if (status === 'rejected' || status === 'failed') return 'red'
  if (status === 'processing') return 'blue'
  return 'yellow'
}
