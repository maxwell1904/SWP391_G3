import { Button } from '@mantine/core'
import { Wrench } from 'lucide-react'
import { FieldControl, InfoPanel, WorkspaceHeader } from '../components/common'
import { DataList } from '../components/data'
import { BookingList, SelectedBooking } from '../features/operations/components'
import { BillingDetails } from '../features/payments/components'
import { formatMoney } from '../utils/format'

export function StaffPage({
  bookings,
  selectedBookingId,
  setSelectedBookingId,
  selectedBooking,
  selectedBookingDetail,
  billingLoading,
  billingError,
  updateBooking,
  capturePayment,
  issueDraft,
  setIssueDraft,
  createIssue,
  issues,
  createRefund,
  refunds
}) {
  const pendingBookings = bookings.filter(booking => booking.status === 'pending').length
  const activeIssues = issues.filter(issue => issue.status === 'open' || issue.status === 'in_progress').length

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
      <div className="roleGrid operationGrid">
        <InfoPanel title="Booking calendar">
          <BookingList bookings={bookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
        </InfoPanel>
        <InfoPanel title="Lifecycle actions">
          <SelectedBooking booking={selectedBooking} />
          <div className="actionGrid">
            <Button onClick={() => updateBooking('confirmed')}>Confirm</Button>
            <Button color="red" variant="light" onClick={() => updateBooking('rejected')}>Reject</Button>
            <Button variant="light" onClick={() => updateBooking('checked_in')}>Check-in</Button>
            <Button variant="light" onClick={() => updateBooking('completed')}>Complete</Button>
            <Button color="red" variant="light" onClick={() => updateBooking('cancelled')}>Cancel</Button>
            <Button color="yellow" variant="light" onClick={() => updateBooking('no_show')}>No-show</Button>
            <Button
              className="wideAction"
              variant="outline"
              disabled={!selectedBooking || Number(selectedBooking.remainingAmount) <= 0}
              onClick={() => capturePayment('remaining')}
            >
              Remaining payment
            </Button>
          </div>
        </InfoPanel>
        <InfoPanel title="Invoice and payment status">
          <BillingDetails detail={selectedBookingDetail} loading={billingLoading} error={billingError} />
        </InfoPanel>
        <InfoPanel title="Issue report">
          <FieldControl label="Title">
            <input value={issueDraft.title} onChange={event => setIssueDraft({ ...issueDraft, title: event.target.value })} />
          </FieldControl>
          <FieldControl label="Description">
            <textarea value={issueDraft.description} onChange={event => setIssueDraft({ ...issueDraft, description: event.target.value })} />
          </FieldControl>
          <Button className="secondaryButton" onClick={createIssue}>
            <Wrench size={18} />
            <span>Save issue</span>
          </Button>
          <DataList items={issues.map(issue => ({
            title: issue.title,
            meta: `${issue.reporter} · ${issue.bookingCode || issue.fieldName || 'general'}`,
            value: issue.status
          }))} />
        </InfoPanel>
        <InfoPanel title="Refunds">
          <Button className="secondaryButton" onClick={createRefund}>Process refund</Button>
          <DataList items={refunds.map(refund => ({
            title: refund.refundCode,
            meta: `${refund.bookingCode} · ${refund.refundReason || 'support case'}`,
            value: `${refund.status} · ${formatMoney(refund.refundAmount)}`
          }))} />
        </InfoPanel>
      </div>
    </section>
  )
}
