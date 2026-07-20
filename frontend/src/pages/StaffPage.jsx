import { Badge, Button } from '@mantine/core'
import { useEffect, useState } from 'react'
import { Wrench } from 'lucide-react'
import { FieldControl, InfoPanel, WorkspaceHeader, WorkspaceTabs } from '../components/common'
import { DataList } from '../components/data'
import { BookingList, SelectedBooking } from '../features/operations/components'
import { BillingDetails } from '../features/payments/components'
import { formatMoney, today } from '../utils/format'
import api from '../services/api'

export function StaffPage({
  bookings,
  selectedBookingId,
  setSelectedBookingId,
  selectedBooking,
  selectedBookingDetail,
  billingLoading,
  billingError,
  updateBooking,
  rescheduleBooking,
  availableSlots,
  capturePayment,
  issueDraft,
  setIssueDraft,
  createIssue,
  issues,
  createRefund,
  refunds,
  updateRefund,
  services,
  fields,
  currentUser,
  refreshAll,
  loadSlots,
  updateBookingServices,
  updateIssue,
  navigatePage
}) {
  const [rescheduleSlotId, setRescheduleSlotId] = useState('')
  const [calendarDate, setCalendarDate] = useState(today)
  const [operationSlots, setOperationSlots] = useState([])
  const [operationNotice, setOperationNotice] = useState('')
  const [blockForm, setBlockForm] = useState({ fieldId: '', startTime: '06:00', endTime: '08:00', blockReason: '', blockNote: '' })
  const [bookingServices, setBookingServices] = useState({})
  const [resolutionNotes, setResolutionNotes] = useState({})
  const [customerActivity, setCustomerActivity] = useState(null)
  const [customerActivityNotice, setCustomerActivityNotice] = useState('Select a booking to review its customer history.')
  const [activePanel, setActivePanel] = useState('bookings')
  const pendingBookings = bookings.filter(booking => booking.status === 'pending').length
  const activeIssues = issues.filter(issue => issue.status === 'open' || issue.status === 'in_progress').length

  useEffect(() => {
    let cancelled = false
    api.get('/operations/calendar', { params: { date: calendarDate } })
      .then(response => { if (!cancelled) setOperationSlots(response.data) })
      .catch(error => { if (!cancelled) setOperationNotice(error.response?.data?.error || 'Could not load the operation calendar.') })
    return () => { cancelled = true }
  }, [calendarDate])

  useEffect(() => {
    const next = {}
    selectedBookingDetail?.services?.forEach(service => { next[service.serviceId] = service.quantity })
    setBookingServices(next)
  }, [selectedBookingDetail?.bookingId])

  useEffect(() => {
    const customerId = selectedBooking?.customerId
    if (!customerId) {
      setCustomerActivity(null)
      setCustomerActivityNotice('Select a booking to review its customer history.')
      return undefined
    }
    let cancelled = false
    setCustomerActivity(null)
    setCustomerActivityNotice('Loading customer history…')
    api.get(`/account/users/${customerId}/activity`)
      .then(response => {
        if (!cancelled) {
          setCustomerActivity(response.data)
          setCustomerActivityNotice('')
        }
      })
      .catch(error => {
        if (!cancelled) setCustomerActivityNotice(error.response?.data?.error || 'Could not load customer history.')
      })
    return () => { cancelled = true }
  }, [selectedBooking?.customerId])

  async function refreshOperations(message) {
    setOperationNotice(message)
    const response = await api.get('/operations/calendar', { params: { date: calendarDate } })
    setOperationSlots(response.data)
    await Promise.all([refreshAll?.(), loadSlots?.()])
  }

  async function blockSlot() {
    if (!blockForm.fieldId || !blockForm.blockReason.trim()) {
      setOperationNotice('Choose a field and enter a block reason.')
      return
    }
    try {
      await api.post('/slots/block', {
        ...blockForm,
        fieldId: Number(blockForm.fieldId),
        slotDate: calendarDate,
        createdById: currentUser?.userId
      })
      setBlockForm(form => ({ ...form, blockReason: '', blockNote: '' }))
      await refreshOperations('Slot blocked and removed from availability.')
    } catch (error) {
      setOperationNotice(error.response?.data?.error || 'Could not block this slot.')
    }
  }

  async function unblockSlot(slotId) {
    try {
      await api.put(`/slots/${slotId}/unblock`)
      await refreshOperations('Slot restored to availability.')
    } catch (error) {
      setOperationNotice(error.response?.data?.error || 'Could not unblock this slot.')
    }
  }

  function serviceSelections() {
    return Object.entries(bookingServices)
      .filter(([, quantity]) => Number(quantity) > 0)
      .map(([serviceId, quantity]) => ({ serviceId: Number(serviceId), quantity: Number(quantity) }))
  }

  return (
    <section id="staff" className="section staffSection">
      <WorkspaceHeader
        kicker="Staff workspace"
        title="Daily field operation"
        text="Review the booking queue, move bookings through the venue lifecycle, log issues, and process refund cases."
        status={{
          label: 'Selected',
          value: selectedBooking ? selectedBooking.status : 'No booking',
          tone: selectedBooking?.status === 'confirmed' ? 'success' : 'warning'
        }}
        metrics={[
          { label: 'Bookings', value: bookings.length },
          { label: 'Pending', value: pendingBookings },
          { label: 'Issues', value: activeIssues },
          { label: 'Refunds', value: refunds.length }
        ]}
      />
      <WorkspaceTabs
        value={activePanel}
        onChange={setActivePanel}
        ariaLabel="Staff workspace sections"
        items={[
          { value: 'bookings', label: 'Bookings' },
          { value: 'schedule', label: 'Field schedule' },
          { value: 'support', label: 'Issues & refunds' }
        ]}
      />
      <div className="roleGrid operationGrid">
        {activePanel === 'bookings' && bookings.length === 0 && (
          <InfoPanel title="No bookings scheduled" className="workspaceEmptyPanel">
            <p className="panelHint">There are no bookings in the current queue. Create a walk-in booking or check the field schedule while you wait for an online booking.</p>
            <div className="buttonRow">
              <Button onClick={() => navigatePage?.('booking')}>Create walk-in booking</Button>
              <Button variant="light" onClick={() => setActivePanel('schedule')}>View field schedule</Button>
            </div>
          </InfoPanel>
        )}
        {bookings.length > 0 && <InfoPanel title="Booking calendar" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
          <BookingList bookings={bookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
        </InfoPanel>}
        <InfoPanel title="Operational slot calendar" className={activePanel === 'schedule' ? '' : 'workspacePanelHidden'}>
          <FieldControl label="Date">
            <input type="date" value={calendarDate} onChange={event => setCalendarDate(event.target.value)} />
          </FieldControl>
          <p className="panelHint">{operationNotice || 'Booked slots show their booking state; blocked slots can be restored.'}</p>
          <div className="customerAdminList">
            {operationSlots.map(slot => (
              <div className="customerAdminRow" key={slot.slotId}>
                <span>
                  <strong>{slot.fieldName} · {String(slot.startTime).slice(0, 5)}–{String(slot.endTime).slice(0, 5)}</strong>
                  <small>{slot.booking ? `${slot.booking.bookingCode} · ${slot.booking.customer}` : slot.blockReason || 'Available'}</small>
                </span>
                <span className="buttonRow">
                  <Badge color={slot.operationalStatus === 'available' ? 'green' : slot.operationalStatus === 'blocked' ? 'red' : 'blue'}>{slot.operationalStatus}</Badge>
                  {slot.status === 'blocked' && <Button size="xs" variant="light" color="green" onClick={() => unblockSlot(slot.slotId)}>Unblock</Button>}
                </span>
              </div>
            ))}
          </div>
          <div className="adminFormGrid compact">
            <FieldControl label="Field">
              <select value={blockForm.fieldId} onChange={event => setBlockForm(form => ({ ...form, fieldId: event.target.value }))}>
                <option value="">Choose field</option>
                {fields.map(field => <option key={field.fieldId} value={field.fieldId}>{field.fieldName}</option>)}
              </select>
            </FieldControl>
            <FieldControl label="Start"><input type="time" value={blockForm.startTime} onChange={event => setBlockForm(form => ({ ...form, startTime: event.target.value }))} /></FieldControl>
            <FieldControl label="End"><input type="time" value={blockForm.endTime} onChange={event => setBlockForm(form => ({ ...form, endTime: event.target.value }))} /></FieldControl>
            <FieldControl label="Reason"><input value={blockForm.blockReason} onChange={event => setBlockForm(form => ({ ...form, blockReason: event.target.value }))} placeholder="Maintenance" /></FieldControl>
          </div>
          <Button variant="light" onClick={blockSlot}>Block slot</Button>
        </InfoPanel>
        {bookings.length > 0 && <InfoPanel title="Lifecycle actions" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
          <SelectedBooking booking={selectedBooking} />
          <div className="actionGrid">
            <Button disabled={!canTransition(selectedBooking?.status, 'confirmed')} onClick={() => updateBooking('confirmed')}>Confirm</Button>
            <Button disabled={!canTransition(selectedBooking?.status, 'rejected')} color="red" variant="light" onClick={() => updateBooking('rejected')}>Reject</Button>
            <Button disabled={!canTransition(selectedBooking?.status, 'checked_in')} variant="light" onClick={() => updateBooking('checked_in')}>Check-in</Button>
            <Button disabled={!canTransition(selectedBooking?.status, 'completed')} variant="light" onClick={() => updateBooking('completed')}>Complete</Button>
            <Button disabled={!canTransition(selectedBooking?.status, 'cancelled')} color="red" variant="light" onClick={() => updateBooking('cancelled')}>Cancel</Button>
            <Button disabled={!canTransition(selectedBooking?.status, 'no_show')} color="yellow" variant="light" onClick={() => updateBooking('no_show')}>No-show</Button>
            <select disabled={!['pending', 'confirmed'].includes(selectedBooking?.status)} value={rescheduleSlotId} onChange={event => setRescheduleSlotId(event.target.value)} aria-label="New slot for reschedule">
              <option value="">Reschedule to…</option>
              {availableSlots.map(slot => <option key={slot.slotId} value={slot.slotId}>{slot.fieldName} · {slot.slotDate} · {slot.startTime}</option>)}
            </select>
            <Button variant="light" disabled={!rescheduleSlotId || !['pending', 'confirmed'].includes(selectedBooking?.status)} onClick={() => rescheduleBooking(rescheduleSlotId)}>Reschedule</Button>
            <Button
              className="wideAction"
              variant="outline"
              disabled={!selectedBooking || Number(selectedBooking.remainingAmount) <= 0}
              onClick={() => capturePayment('remaining')}
            >
              Remaining payment
            </Button>
          </div>
        </InfoPanel>}
        {bookings.length > 0 && <InfoPanel title="Invoice and payment status" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
          <BillingDetails detail={selectedBookingDetail} loading={billingLoading} error={billingError} />
        </InfoPanel>}
        {bookings.length > 0 && <InfoPanel title="Customer booking activity" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
          {!customerActivity ? <p className="panelHint">{customerActivityNotice}</p> : (
            <>
              <p className="panelHint"><strong>{customerActivity.user.fullName}</strong> · {customerActivity.bookingCount} recent booking(s), {customerActivity.completedBookingCount} completed.</p>
              <DataList items={customerActivity.bookings.map(booking => ({
                title: `${booking.bookingCode} · ${booking.fieldName}`,
                meta: `${booking.slotDate} · ${String(booking.startTime).slice(0, 5)}`,
                value: booking.status
              }))} />
            </>
          )}
        </InfoPanel>}
        {bookings.length > 0 && <InfoPanel title="Edit booking services (before check-in)" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
          {!selectedBooking ? <p className="panelHint">Select a booking first.</p> : (
            <>
              {services.map(service => (
                <FieldControl key={service.extraServiceId} label={`${service.serviceName} · ${formatMoney(service.unitPrice)}`}>
                  <input
                    type="number"
                    min="0"
                    max={service.maxQuantityPerBooking || undefined}
                    value={bookingServices[service.extraServiceId] || ''}
                    disabled={!['pending', 'confirmed'].includes(selectedBooking.status)}
                    onChange={event => setBookingServices(current => ({ ...current, [service.extraServiceId]: event.target.value }))}
                  />
                </FieldControl>
              ))}
              <Button
                variant="light"
                disabled={!['pending', 'confirmed'].includes(selectedBooking.status)}
                onClick={() => updateBookingServices(serviceSelections())}
              >Save services</Button>
            </>
          )}
        </InfoPanel>}
        <InfoPanel title="Issue report" className={activePanel === 'support' ? '' : 'workspacePanelHidden'}>
          <FieldControl label="Title">
            <input value={issueDraft.title} onChange={event => setIssueDraft({ ...issueDraft, title: event.target.value })} />
          </FieldControl>
          <FieldControl label="Description">
            <textarea value={issueDraft.description} onChange={event => setIssueDraft({ ...issueDraft, description: event.target.value })} />
          </FieldControl>
          <Button className="secondaryButton" disabled={!issueDraft.title.trim() || !issueDraft.description.trim()} onClick={createIssue}>
            <Wrench size={18} />
            <span>Save issue</span>
          </Button>
          <DataList items={issues.map(issue => ({ title: issue.title, meta: `${issue.reporter} · ${issue.bookingCode || issue.fieldName || 'general'}`, value: issue.status }))} />
          {issues.filter(issue => issue.status === 'open' || issue.status === 'in_progress').map(issue => (
            <div className="buttonRow" key={`issue-actions-${issue.issueId}`}>
              <input
                value={resolutionNotes[issue.issueId] || ''}
                onChange={event => setResolutionNotes(notes => ({ ...notes, [issue.issueId]: event.target.value }))}
                placeholder="Resolution note"
                aria-label={`Resolution note for ${issue.title}`}
              />
              <Button size="xs" variant="light" onClick={() => updateIssue(issue.issueId, 'in_progress', '')}>Start</Button>
              <Button size="xs" color="green" onClick={() => updateIssue(issue.issueId, 'resolved', resolutionNotes[issue.issueId] || '')}>Resolve</Button>
            </div>
          ))}
        </InfoPanel>
        <InfoPanel title="Refunds" className={activePanel === 'support' ? '' : 'workspacePanelHidden'}>
          <Button className="secondaryButton" disabled={!selectedBooking || Number(selectedBooking.refundableAmount) <= 0} onClick={createRefund}>Request refund for selected booking</Button>
          <DataList items={refunds.map(refund => ({
            title: refund.refundCode,
            meta: `${refund.bookingCode} · ${refund.paymentMethod === 'paypal_sandbox' ? 'PayPal' : 'Cash'} · ${refund.gatewayMessage || refund.refundReason || 'support case'}`,
            value: `${refund.status} · ${formatMoney(refund.refundAmount)}`
          }))} />
          {refunds.filter(refund => refund.status === 'requested').map(refund => (
            <div className="buttonRow" key={`actions-${refund.refundId}`}>
              <Button size="xs" color="green" onClick={() => updateRefund(refund, 'approved')}>Approve {refund.refundCode}</Button>
              <Button size="xs" color="red" variant="light" onClick={() => updateRefund(refund, 'rejected')}>Reject</Button>
            </div>
          ))}
          {refunds.filter(refund => refund.status === 'approved').map(refund => (
            <Button key={`complete-${refund.refundId}`} size="xs" variant="light" onClick={() => updateRefund(refund, refund.paymentMethod === 'paypal_sandbox' ? 'processing' : 'completed')}>
              {refund.paymentMethod === 'paypal_sandbox' ? `Send ${refund.refundCode} to PayPal` : `Record cash refund ${refund.refundCode}`}
            </Button>
          ))}
          {refunds.filter(refund => refund.paymentMethod === 'paypal_sandbox' && (refund.status === 'processing' || refund.status === 'failed')).map(refund => (
            <Button key={`retry-${refund.refundId}`} size="xs" variant="light" onClick={() => updateRefund(refund, 'processing')}>Retry/check {refund.refundCode}</Button>
          ))}
        </InfoPanel>
      </div>
    </section>
  )
}

function canTransition(current, next) {
  const transitions = {
    pending: ['confirmed', 'rejected', 'cancelled'],
    confirmed: ['checked_in', 'cancelled', 'no_show'],
    checked_in: ['completed']
  }
  return Boolean(current && transitions[current]?.includes(next))
}
