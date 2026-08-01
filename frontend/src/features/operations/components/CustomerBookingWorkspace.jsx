import { useEffect, useState } from 'react'
import { Badge } from '@mantine/core'
import { CalendarPlus, ReceiptText, Settings2 } from 'lucide-react'
import { FieldControl, InfoPanel } from '../../../components/common'
import { BillingDetails } from '../../payments/components/BillingDetails'
import api from '../../../services/api'
import { formatDate, formatMoney, formatTime, humanizeStatus, today, tomorrow } from '../../../utils/format'
import { BookingList } from './BookingList'

export function CustomerBookingWorkspace({
  bookings,
  selectedBookingId,
  onSelectBooking,
  selectedBooking,
  billingLoading,
  billingError,
  cancellationPreview,
  services,
  onPreviewCancellation,
  onCancelBooking,
  onRequestRefund,
  onReschedule,
  onUpdateBookingServices,
  onStartBooking
}) {
  const [rescheduleDate, setRescheduleDate] = useState(tomorrow)
  const [rescheduleSlots, setRescheduleSlots] = useState([])
  const [rescheduleLoading, setRescheduleLoading] = useState(false)
  const [rescheduleError, setRescheduleError] = useState('')

  useEffect(() => {
    setRescheduleDate(tomorrow())
    setRescheduleSlots([])
    setRescheduleError('')
  }, [selectedBooking?.bookingId])

  useEffect(() => {
    if (!selectedBooking || !rescheduleDate || !['pending', 'confirmed'].includes(selectedBooking.status)) return undefined
    let cancelled = false
    setRescheduleLoading(true)
    setRescheduleError('')
    api.get('/slots/search', { params: { date: rescheduleDate } })
      .then(response => {
        if (!cancelled) setRescheduleSlots(response.data.filter(slot => slot.available))
      })
      .catch(error => {
        if (!cancelled) {
          setRescheduleSlots([])
          setRescheduleError(error.response?.data?.error || 'Could not load slots for this date.')
        }
      })
      .finally(() => { if (!cancelled) setRescheduleLoading(false) })
    return () => { cancelled = true }
  }, [selectedBooking?.bookingId, selectedBooking?.status, rescheduleDate])

  if (!bookings.length) {
    return (
      <InfoPanel title="No bookings yet" className="bookingWorkspaceEmpty">
        <p className="emptyText">Choose a field and available time to create your first booking.</p>
        <button type="button" className="primaryButton" onClick={onStartBooking}>Book a field</button>
      </InfoPanel>
    )
  }

  return (
    <div className="bookingWorkspace customerBookingWorkspace">
      <InfoPanel title="My bookings" className="bookingQueuePanel">
        <div className="panelLeadRow">
          <p className="panelHint">Select a booking to view payment and change options.</p>
          <button type="button" className="secondaryButton compactButton" onClick={onStartBooking}>
            <CalendarPlus size={16} aria-hidden="true" /> New booking
          </button>
        </div>
        <BookingList bookings={bookings} selectedBookingId={selectedBookingId} onSelect={onSelectBooking} />
      </InfoPanel>

      <InfoPanel title="Booking details" className="bookingDetailPanel">
        {!selectedBooking ? (
          <p className="emptyText">Select a booking from the list.</p>
        ) : (
          <>
            <BookingDetailHeading booking={selectedBooking} />

            <section className="bookingDetailSection" aria-labelledby="customer-billing-title">
              <h4 id="customer-billing-title"><ReceiptText size={17} aria-hidden="true" /> Invoice & payment</h4>
              <BillingDetails detail={selectedBooking} loading={billingLoading} error={billingError} />
            </section>

            <section className="bookingDetailSection" aria-labelledby="customer-change-title">
              <h4 id="customer-change-title"><Settings2 size={17} aria-hidden="true" /> Change booking</h4>
              <BookingChangeActions
                booking={selectedBooking}
                preview={cancellationPreview}
                rescheduleDate={rescheduleDate}
                onRescheduleDateChange={setRescheduleDate}
                availableSlots={rescheduleSlots}
                slotsLoading={rescheduleLoading}
                slotsError={rescheduleError}
                onPreview={onPreviewCancellation}
                onCancel={onCancelBooking}
                onRefund={onRequestRefund}
                onReschedule={onReschedule}
              />
            </section>

            <details className="bookingDetailDisclosure">
              <summary>Edit booking add-ons</summary>
              <BookingServiceEditor booking={selectedBooking} services={services} onSave={onUpdateBookingServices} />
            </details>
          </>
        )}
      </InfoPanel>
    </div>
  )
}

function BookingDetailHeading({ booking }) {
  return (
    <div className="bookingDetailHeading">
      <span>
        <small>Selected booking</small>
        <strong>{booking.bookingCode}</strong>
        <span>{booking.fieldName} · {formatDate(booking.slotDate)} · {formatTime(booking.startTime)}</span>
      </span>
      <Badge variant="light" color={statusColor(booking.status)}>{humanizeStatus(booking.status)}</Badge>
    </div>
  )
}

function BookingServiceEditor({ booking, services = [], onSave }) {
  const [quantities, setQuantities] = useState({})

  useEffect(() => {
    setQuantities(Object.fromEntries((booking?.services || []).map(item => [item.serviceId, item.quantity])))
  }, [booking?.bookingId, booking?.services])

  if (!booking || !['pending', 'confirmed'].includes(booking.status) || hasBookingStarted(booking)) {
    return <p className="emptyText">Add-ons can be changed only before the booked start time.</p>
  }

  return (
    <div className="profileForm compactEditor">
      <p className="hintText">Saving recalculates the invoice, discounts, and remaining balance.</p>
      {services.filter(service => service.status === 'active').map(service => (
        <FieldControl key={service.extraServiceId} label={`${service.serviceName} · ${formatMoney(service.unitPrice)}`}>
          <input
            name={`service-${service.extraServiceId}`}
            type="number"
            min="0"
            max={service.maxQuantityPerBooking || service.stockQuantity || 99}
            value={quantities[service.extraServiceId] || 0}
            onChange={event => setQuantities(current => ({ ...current, [service.extraServiceId]: Number(event.target.value) }))}
          />
        </FieldControl>
      ))}
      <button type="button" className="primaryButton" onClick={() => onSave(
        Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0)
          .map(([serviceId, quantity]) => ({ serviceId: Number(serviceId), quantity: Number(quantity) })),
        booking.bookingId
      )}>Save add-ons</button>
    </div>
  )
}

function BookingChangeActions({
  booking,
  preview,
  rescheduleDate,
  onRescheduleDateChange,
  availableSlots,
  slotsLoading,
  slotsError,
  onPreview,
  onCancel,
  onRefund,
  onReschedule
}) {
  const [slotId, setSlotId] = useState('')
  const [refundOpen, setRefundOpen] = useState(false)
  const [refundAmount, setRefundAmount] = useState('')
  const [refundReason, setRefundReason] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setSlotId('')
    setRefundOpen(false)
    setRefundAmount(String(booking?.refundableAmount || ''))
    setRefundReason('')
  }, [booking?.bookingId, booking?.refundableAmount])

  if (!booking || !['pending', 'confirmed', 'cancelled'].includes(booking.status)) {
    return <p className="emptyText">No booking changes are available for this status.</p>
  }

  const cancellable = (booking.status === 'pending' || booking.status === 'confirmed') && !hasBookingStarted(booking)
  const cancellationReviewed = preview?.bookingId === booking.bookingId
  const existingRefund = (booking.refunds || []).find(refund => !['rejected', 'failed'].includes(refund.status))

  return (
    <div className="bookingChangeActions">
      {cancellable ? (
        <>
          <div className="inlineFormAction">
            <FieldControl label="New date">
              <input type="date" min={today()} value={rescheduleDate} onChange={event => onRescheduleDateChange(event.target.value)} />
            </FieldControl>
            <FieldControl label="Available slot">
              <select name="rescheduleSlot" value={slotId} onChange={event => setSlotId(event.target.value)}>
                <option value="">{slotsLoading ? 'Loading slots...' : 'Choose a new slot'}</option>
                {availableSlots.map(slot => <option key={slot.slotId} value={slot.slotId}>{slot.fieldName} · {slot.slotDate} · {slot.startTime}</option>)}
              </select>
            </FieldControl>
            <button type="button" className="secondaryButton" disabled={!slotId || saving} onClick={async () => {
              setSaving(true)
              const result = await onReschedule(slotId)
              if (result) setSlotId('')
              setSaving(false)
            }}>Reschedule</button>
          </div>
          {slotsError && <p className="errorText">{slotsError}</p>}
          {!slotsLoading && !slotsError && !availableSlots.length && <p className="hintText">No available slots on this date.</p>}

          {!cancellationReviewed ? (
            <button type="button" className="secondaryButton" onClick={onPreview}>Review cancellation terms</button>
          ) : (
            <div className="cancellationReview">
              <strong>Cancellation terms</strong>
              <p className="hintText">{preview.policy}: refund {formatMoney(preview.refundableAmount)}, fee {formatMoney(preview.cancellationFeeAmount)}.</p>
              <div className="buttonRow noMargin">
                <button type="button" className="ghostDarkButton" onClick={onPreview}>Refresh terms</button>
                <button type="button" className="dangerButton" onClick={onCancel}>Confirm cancellation</button>
              </div>
            </div>
          )}
        </>
      ) : booking.status !== 'cancelled' ? (
        <p className="hintText">This booking has started. Contact Venue Staff for check-in or no-show handling.</p>
      ) : null}

      {Number(booking.refundableAmount) > 0 && !existingRefund && (
        refundOpen ? (
          <div className="refundRequestState">
            <FieldControl label={`Refund amount (maximum ${formatMoney(booking.refundableAmount)})`}>
              <input type="number" min="0.01" max={booking.refundableAmount} step="0.01" value={refundAmount} onChange={event => setRefundAmount(event.target.value)} />
            </FieldControl>
            <FieldControl label="Reason">
              <textarea value={refundReason} onChange={event => setRefundReason(event.target.value)} placeholder="Explain why this refund is requested" />
            </FieldControl>
            <div className="buttonRow noMargin">
              <button type="button" className="primaryButton" disabled={saving || !refundReason.trim() || Number(refundAmount) <= 0 || Number(refundAmount) > Number(booking.refundableAmount)} onClick={async () => {
                setSaving(true)
                const result = await onRefund({ refundAmount: Number(refundAmount), refundReason: refundReason.trim() })
                if (result) setRefundOpen(false)
                setSaving(false)
              }}>Submit refund request</button>
              <button type="button" className="secondaryButton" disabled={saving} onClick={() => setRefundOpen(false)}>Cancel</button>
            </div>
          </div>
        ) : (
          <button type="button" className="primaryButton" onClick={() => setRefundOpen(true)}>
            {booking.status === 'cancelled' ? 'Request cancellation refund' : 'Request reschedule refund'} ({formatMoney(booking.refundableAmount)})
          </button>
        )
      )}
      {existingRefund && (
        <div className="refundRequestState" aria-live="polite">
          <strong>{existingRefund.refundCode}</strong>
          <span>Refund {humanizeStatus(existingRefund.status)} · {formatMoney(existingRefund.refundAmount)}</span>
          <small>{existingRefund.refundReason || 'The venue team will update this request after review.'}</small>
        </div>
      )}
    </div>
  )
}

function hasBookingStarted(booking) {
  if (!booking?.slotDate || !booking?.startTime) return false
  const start = new Date(`${booking.slotDate}T${String(booking.startTime).slice(0, 8)}`)
  return !Number.isNaN(start.getTime()) && Date.now() >= start.getTime()
}

function statusColor(status) {
  if (status === 'confirmed' || status === 'completed') return 'green'
  if (status === 'pending') return 'yellow'
  if (status === 'cancelled' || status === 'rejected' || status === 'no_show') return 'red'
  return 'gray'
}
