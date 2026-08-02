import { Badge, Button } from '@mantine/core'
import { useEffect, useState } from 'react'
import { Wrench } from 'lucide-react'
import { FieldControl, InfoPanel, WorkspaceHeader, WorkspaceTabs } from '../components/common'
import { StaffBookingWorkspace } from '../features/operations/components/StaffBookingWorkspace'
import { IssueCaseList, RefundCaseList } from '../features/support/components'
import { today, tomorrow } from '../utils/format'
import api from '../services/api'
import { useWorkspaceTab } from '../hooks/useWorkspaceTab'
import { useAutoDismiss } from '../hooks/useAutoDismiss'

const staffTabs = ['bookings', 'schedule', 'support']

export function StaffPage({
  bookings,
  selectedBookingId,
  setSelectedBookingId,
  selectedBooking,
  selectedBookingDetail,
  cancellationPreview,
  billingLoading,
  billingError,
  updateBooking,
  previewCancellation,
  rescheduleBooking,
  capturePayment,
  issueDraft,
  setIssueDraft,
  createIssue,
  issues,
  refunds,
  updateRefund,
  services,
  fields,
  refreshAll,
  loadSlots,
  updateBookingServices,
  updateIssue,
  onCreateWalkIn
}) {
  const [rescheduleSlotId, setRescheduleSlotId] = useState('')
  const [rescheduleDate, setRescheduleDate] = useState(tomorrow)
  const [rescheduleSlots, setRescheduleSlots] = useState([])
  const [rescheduleLoading, setRescheduleLoading] = useState(false)
  const [rescheduleError, setRescheduleError] = useState('')
  const [calendarDate, setCalendarDate] = useState(today)
  const [operationSlots, setOperationSlots] = useState([])
  const [operationNotice, setOperationNotice] = useState('')
  const [blockForm, setBlockForm] = useState({ fieldId: '', startTime: '06:00', endTime: '08:00', blockReason: '', blockNote: '' })
  const [bookingServices, setBookingServices] = useState({})
  const [resolutionNotes, setResolutionNotes] = useState({})
  const [activePanel, setActivePanel] = useWorkspaceTab('bookings', staffTabs)
  const pendingBookings = bookings.filter(booking => booking.status === 'pending').length
  const activeIssues = issues.filter(issue => issue.status === 'open' || issue.status === 'in_progress').length

  useAutoDismiss(operationNotice, () => setOperationNotice(''), 6000)

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
  }, [selectedBookingDetail?.bookingId, selectedBookingDetail?.services])

  useEffect(() => {
    setRescheduleSlotId('')
    setRescheduleDate(tomorrow())
  }, [selectedBooking?.bookingId])

  useEffect(() => {
    if (!selectedBooking || !rescheduleDate) return undefined
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
  }, [selectedBooking?.bookingId, rescheduleDate])

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
        slotDate: calendarDate
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
      {activePanel === 'bookings' && (
        <StaffBookingWorkspace
          bookings={bookings}
          selectedBookingId={selectedBookingId}
          onSelectBooking={setSelectedBookingId}
          selectedBooking={selectedBooking}
          selectedBookingDetail={selectedBookingDetail}
          cancellationPreview={cancellationPreview}
          billingLoading={billingLoading}
          billingError={billingError}
          canTransition={canTransition}
          hasReachedBookingTime={hasReachedBookingTime}
          onUpdateBooking={updateBooking}
          onPreviewCancellation={previewCancellation}
          rescheduleSlotId={rescheduleSlotId}
          onRescheduleSlotChange={setRescheduleSlotId}
          rescheduleDate={rescheduleDate}
          onRescheduleDateChange={value => {
            setRescheduleDate(value)
            setRescheduleSlotId('')
          }}
          rescheduleLoading={rescheduleLoading}
          rescheduleError={rescheduleError}
          availableSlots={rescheduleSlots}
          onReschedule={rescheduleBooking}
          onCaptureRemaining={() => capturePayment('remaining')}
          services={services}
          serviceQuantities={bookingServices}
          onServiceQuantityChange={(serviceId, quantity) => setBookingServices(current => ({ ...current, [serviceId]: quantity }))}
          onSaveServices={() => updateBookingServices(serviceSelections())}
          onCreateWalkIn={onCreateWalkIn}
        />
      )}
      <div className="roleGrid operationGrid">
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
            <FieldControl label="Note (optional)"><input value={blockForm.blockNote} onChange={event => setBlockForm(form => ({ ...form, blockNote: event.target.value }))} placeholder="Work order or staff note" /></FieldControl>
          </div>
          <Button variant="light" onClick={blockSlot}>Block slot</Button>
        </InfoPanel>
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
          <IssueCaseList
            issues={issues}
            resolutionNotes={resolutionNotes}
            onNoteChange={(issueId, value) => setResolutionNotes(notes => ({ ...notes, [issueId]: value }))}
            onStart={issue => updateIssue(issue.issueId, 'in_progress', '')}
            onResolve={(issue, note) => updateIssue(issue.issueId, 'resolved', note)}
            onReject={(issue, note) => updateIssue(issue.issueId, 'rejected', note)}
          />
        </InfoPanel>
        <InfoPanel title="Refunds" className={activePanel === 'support' ? '' : 'workspacePanelHidden'}>
          <p className="panelHint">Customers submit refund requests. Staff verify the case, approve or reject it, then complete the cash or PayPal return.</p>
          <RefundCaseList refunds={refunds} onUpdate={updateRefund} />
        </InfoPanel>
      </div>
    </section>
  )
}

function canTransition(current, next) {
  const transitions = {
    pending: ['cancelled'],
    confirmed: ['checked_in', 'cancelled', 'no_show'],
    checked_in: ['completed']
  }
  return Boolean(current && transitions[current]?.includes(next))
}

function hasReachedBookingTime(booking, offsetMinutes = 0) {
  if (!booking?.slotDate || !booking?.startTime) return false
  const start = new Date(`${booking.slotDate}T${String(booking.startTime).slice(0, 8)}`)
  if (Number.isNaN(start.getTime())) return false
  return Date.now() >= start.getTime() + offsetMinutes * 60_000
}
