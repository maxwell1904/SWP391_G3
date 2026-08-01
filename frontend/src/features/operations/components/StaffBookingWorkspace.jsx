import { useEffect, useState } from 'react'
import { Button, Modal, Stack, Textarea } from '@mantine/core'
import { CalendarPlus, CreditCard, Settings2 } from 'lucide-react'
import { FieldControl, InfoPanel } from '../../../components/common'
import { BillingDetails } from '../../payments/components/BillingDetails'
import { formatDate, formatMoney, formatTime, today } from '../../../utils/format'
import { BookingList } from './BookingList'
import { SelectedBooking } from './SelectedBooking'

export function StaffBookingWorkspace({
  bookings,
  selectedBookingId,
  onSelectBooking,
  selectedBooking,
  selectedBookingDetail,
  cancellationPreview,
  billingLoading,
  billingError,
  canTransition,
  hasReachedBookingTime,
  onUpdateBooking,
  onPreviewCancellation,
  rescheduleSlotId,
  onRescheduleSlotChange,
  rescheduleDate,
  onRescheduleDateChange,
  rescheduleLoading,
  rescheduleError,
  availableSlots,
  onReschedule,
  onCaptureRemaining,
  services,
  serviceQuantities,
  onServiceQuantityChange,
  onSaveServices,
  onCreateWalkIn
}) {
  const [pendingAction, setPendingAction] = useState(null)
  const [actionNote, setActionNote] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    setPendingAction(null)
    setActionNote('')
  }, [selectedBooking?.bookingId])

  function requestStatus(status, title, noteRequired = false) {
    setPendingAction({ type: 'status', status, title, noteRequired })
    setActionNote('')
  }

  async function confirmAction() {
    if (!pendingAction || (pendingAction.noteRequired && !actionNote.trim())) return
    setSubmitting(true)
    const result = pendingAction.type === 'cash'
      ? await onCaptureRemaining()
      : await onUpdateBooking(pendingAction.status, actionNote.trim())
    setSubmitting(false)
    if (result) {
      setPendingAction(null)
      setActionNote('')
    }
  }

  if (!bookings.length) {
    return (
      <InfoPanel title="No bookings scheduled" className="bookingWorkspaceEmpty">
        <p className="panelHint">There are no bookings in the current queue. Create a walk-in booking or check the field schedule.</p>
        <Button leftSection={<CalendarPlus size={16} aria-hidden="true" />} onClick={onCreateWalkIn}>Create walk-in booking</Button>
      </InfoPanel>
    )
  }

  const cancellationReviewed = cancellationPreview?.bookingId === selectedBooking?.bookingId
  const editable = ['pending', 'confirmed'].includes(selectedBooking?.status) && !hasReachedBookingTime(selectedBooking)

  return (
    <div className="bookingWorkspace staffBookingWorkspace">
      <InfoPanel title="Booking queue" className="bookingQueuePanel">
        <div className="panelLeadRow">
          <p className="panelHint">Select a booking before recording an operational action.</p>
          <Button size="xs" variant="light" leftSection={<CalendarPlus size={15} aria-hidden="true" />} onClick={onCreateWalkIn}>Walk-in</Button>
        </div>
        <BookingList bookings={bookings} selectedBookingId={selectedBookingId} onSelect={onSelectBooking} />
      </InfoPanel>

      <InfoPanel title="Selected booking operation" className="bookingDetailPanel staffOperationPanel">
        {!selectedBooking ? (
          <p className="emptyText">Select a booking from the queue.</p>
        ) : (
          <>
            <SelectedBooking booking={selectedBooking} />
            <div className="operationFactGrid">
              <OperationFact label="Start" value={`${formatDate(selectedBooking.slotDate)} · ${formatTime(selectedBooking.startTime)}`} />
              <OperationFact label="Paid" value={formatMoney(selectedBooking.paidAmount)} />
              <OperationFact label="Remaining" value={formatMoney(selectedBooking.remainingAmount)} warning={Number(selectedBooking.remainingAmount) > 0} />
            </div>

            <section className="bookingDetailSection" aria-labelledby="lifecycle-actions-title">
              <h4 id="lifecycle-actions-title"><Settings2 size={17} aria-hidden="true" /> Booking status</h4>
              <div className="actionGrid lifecycleActionGrid">
                <Button disabled={!canTransition(selectedBooking.status, 'checked_in') || !hasReachedBookingTime(selectedBooking, -30) || Number(selectedBooking.paidAmount) < Number(selectedBooking.depositAmount)} variant="light" onClick={() => requestStatus('checked_in', 'Confirm customer check-in')}>Check-in</Button>
                <Button disabled={!canTransition(selectedBooking.status, 'completed') || !hasReachedBookingTime(selectedBooking) || Number(selectedBooking.remainingAmount) > 0} variant="light" onClick={() => requestStatus('completed', 'Confirm booking completion')}>Complete</Button>
                <Button disabled={!canTransition(selectedBooking.status, 'no_show') || !hasReachedBookingTime(selectedBooking, 15)} color="yellow" variant="light" onClick={() => requestStatus('no_show', 'Record customer no-show', true)}>No-show</Button>
              </div>
            </section>

            <section className="bookingDetailSection" aria-labelledby="schedule-actions-title">
              <h4 id="schedule-actions-title">Schedule change</h4>
              <div className="inlineFormAction">
                <FieldControl label="New date">
                  <input type="date" min={today()} value={rescheduleDate} onChange={event => onRescheduleDateChange(event.target.value)} disabled={!editable} />
                </FieldControl>
                <FieldControl label="New available slot">
                  <select
                    name="staffRescheduleSlot"
                    disabled={!editable}
                    value={rescheduleSlotId}
                    onChange={event => onRescheduleSlotChange(event.target.value)}
                  >
                    <option value="">{rescheduleLoading ? 'Loading slots...' : 'Choose a new slot'}</option>
                    {availableSlots.map(slot => <option key={slot.slotId} value={slot.slotId}>{slot.fieldName} · {slot.slotDate} · {slot.startTime}</option>)}
                  </select>
                </FieldControl>
                <Button variant="light" disabled={!rescheduleSlotId || !editable} onClick={() => onReschedule(rescheduleSlotId)}>Reschedule</Button>
              </div>
              {rescheduleError && <p className="errorText">{rescheduleError}</p>}
              {!rescheduleLoading && !rescheduleError && editable && !availableSlots.length && <p className="panelHint">No available slots on this date.</p>}
              <Button
                fullWidth
                disabled={!canTransition(selectedBooking.status, 'cancelled') || hasReachedBookingTime(selectedBooking)}
                color="red"
                variant="light"
                onClick={() => cancellationReviewed ? requestStatus('cancelled', 'Confirm booking cancellation') : onPreviewCancellation()}
              >
                {cancellationReviewed ? 'Confirm cancellation' : 'Review cancellation terms'}
              </Button>
              {cancellationReviewed && (
                <p className="panelHint cancellationSummary">
                  {formatMoney(cancellationPreview.refundableAmount)} refundable · {formatMoney(cancellationPreview.cancellationFeeAmount)} cancellation fee.
                </p>
              )}
            </section>

            <section className="bookingDetailSection" aria-labelledby="payment-action-title">
              <h4 id="payment-action-title"><CreditCard size={17} aria-hidden="true" /> Counter payment</h4>
              <Button
                fullWidth
                variant="outline"
                disabled={!['pending', 'confirmed', 'checked_in', 'completed'].includes(selectedBooking.status) || Number(selectedBooking.remainingAmount) <= 0}
                onClick={() => {
                  setPendingAction({ type: 'cash', title: 'Confirm cash payment', noteRequired: false })
                  setActionNote('')
                }}
              >
                Record remaining payment ({formatMoney(selectedBooking.remainingAmount)})
              </Button>
            </section>

            <details className="bookingDetailDisclosure">
              <summary>Invoice & transaction history</summary>
              <BillingDetails detail={selectedBookingDetail} loading={billingLoading} error={billingError} showProcessorDetails />
            </details>

            <details className="bookingDetailDisclosure">
              <summary>Edit booking services</summary>
              {!editable ? <p className="panelHint">Services can be changed only before check-in and the booked start time.</p> : (
                <div className="profileForm compactEditor">
                  {services.map(service => (
                    <FieldControl key={service.extraServiceId} label={`${service.serviceName} · ${formatMoney(service.unitPrice)}`}>
                      <input
                        name={`staff-service-${service.extraServiceId}`}
                        type="number"
                        min="0"
                        max={service.maxQuantityPerBooking || undefined}
                        value={serviceQuantities[service.extraServiceId] || ''}
                        onChange={event => onServiceQuantityChange(service.extraServiceId, event.target.value)}
                      />
                    </FieldControl>
                  ))}
                  <Button variant="light" onClick={onSaveServices}>Save services</Button>
                </div>
              )}
            </details>
          </>
        )}
      </InfoPanel>
      <Modal
        opened={Boolean(pendingAction)}
        onClose={() => !submitting && setPendingAction(null)}
        centered
        title={pendingAction?.title}
        closeButtonProps={{ 'aria-label': 'Close confirmation' }}
      >
        <Stack gap="md">
          <p className="panelHint">
            {pendingAction?.type === 'cash'
              ? `Record ${formatMoney(selectedBooking?.remainingAmount)} received in cash for ${selectedBooking?.bookingCode}?`
              : `Apply this status change to ${selectedBooking?.bookingCode}?`}
          </p>
          {pendingAction?.type === 'status' && (
            <Textarea
              label={pendingAction.noteRequired ? 'Operation note (required)' : 'Operation note (optional)'}
              value={actionNote}
              onChange={event => setActionNote(event.target.value)}
              placeholder={pendingAction.noteRequired ? 'Record what staff checked before marking no-show' : 'Add a short audit note'}
            />
          )}
          <div className="buttonRow noMargin">
            <Button onClick={confirmAction} loading={submitting} disabled={pendingAction?.noteRequired && !actionNote.trim()}>Confirm</Button>
            <Button variant="light" onClick={() => setPendingAction(null)} disabled={submitting}>Cancel</Button>
          </div>
        </Stack>
      </Modal>
    </div>
  )
}

function OperationFact({ label, value, warning = false }) {
  return (
    <div className="operationFact">
      <span>{label}</span>
      <strong className={warning ? 'warningText' : ''}>{value}</strong>
    </div>
  )
}
