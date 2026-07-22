import { useEffect, useState } from 'react'
import { FieldControl, InfoPanel, PasswordField, WorkspaceHeader, WorkspaceTabs } from '../components/common'
import { DataList, MetricGrid } from '../components/data'
import { EmailVerificationPanel } from '../features/account/components'
import { BookingList } from '../features/operations/components'
import { BillingDetails } from '../features/payments/components'
import { passwordIssues } from '../features/auth/authRules'
import { formatMoney } from '../utils/format'

export function AccountPage({
  currentUser,
  userBookings,
  payments,
  membership,
  notifications,
  selectedBookingId,
  setSelectedBookingId,
  selectedBookingDetail,
  billingLoading,
  billingError,
  cancellationPreview,
  resendVerification,
  onSaveProfile,
  onChangePassword,
  onPreviewCancellation,
  onCancelBooking,
  onRequestRefund,
  onReschedule,
  availableSlots,
  services,
  fields,
  onUpdateBookingServices,
  onReportIssue,
  onStartBooking
}) {
  const [profileForm, setProfileForm] = useState(() => profileFromUser(currentUser))
  const [activePanel, setActivePanel] = useState('bookings')

  useEffect(() => {
    setProfileForm(profileFromUser(currentUser))
  }, [currentUser])

  const updateProfileField = (field, value) => {
    setProfileForm({ ...profileForm, [field]: value })
  }

  const accountPayments = payments
    .filter(payment => currentUser.role !== 'Customer' || userBookings.some(booking => booking.bookingCode === payment.bookingCode))
  const unreadNotifications = notifications.filter(item => !item.read).length

  const isCustomer = currentUser.role === 'Customer'

  return (
    <section id="account" className="section accountSection">
      {isCustomer ? (
        <>
          <WorkspaceHeader
            kicker="Customer workspace"
            title={currentUser.fullName}
            text="Manage profile details, bookings, payments, membership progress, and service messages from one place."
            status={{
              label: 'Account status',
              value: currentUser.emailVerified ? 'Email verified' : 'Email pending',
              tone: currentUser.emailVerified ? 'success' : 'warning'
            }}
            metrics={[
              { label: 'Bookings', value: userBookings.length },
              { label: 'Payments', value: accountPayments.length },
              { label: 'Membership', value: membership?.currentLevel || 'None' },
              { label: 'Unread', value: unreadNotifications }
            ]}
          />
          {!currentUser.emailVerified && (
            <div className="accountVerification">
              <EmailVerificationPanel
                email={currentUser.email}
                onResend={resendVerification}
              />
            </div>
          )}
          {currentUser.bookingRestricted && (
            <div className="accountVerification" style={{ borderLeftColor: '#ff6b6b', background: '#fff5f5' }}>
              <div className="verificationPanel">
                <h4 style={{ color: '#e53e3e', margin: '0 0 6px 0' }}>Booking Privileges Restricted</h4>
                <p style={{ color: '#4a5568', margin: 0, fontSize: '0.9rem' }}>
                  Your account has been restricted from creating bookings. Reason: <strong>{currentUser.restrictionReason || 'Restricted by admin.'}</strong>
                </p>
              </div>
            </div>
          )}
          <WorkspaceTabs
            value={activePanel}
            onChange={setActivePanel}
            ariaLabel="Customer account sections"
            items={[
              { value: 'bookings', label: 'Bookings' },
              { value: 'payments', label: 'Payments' },
              { value: 'membership', label: 'Membership' },
              { value: 'profile', label: 'Profile' },
              { value: 'messages', label: 'Messages' }
            ]}
          />
          <div className="roleGrid accountGrid">
            <InfoPanel title="Personal profile" className={activePanel === 'profile' ? '' : 'workspacePanelHidden'}>
              <div className="profileForm">
                <FieldControl label="Full name">
                  <input
                    value={profileForm.fullName}
                    onChange={event => updateProfileField('fullName', event.target.value)}
                  />
                </FieldControl>
                <FieldControl label="Phone">
                  <input
                    inputMode="tel"
                    value={profileForm.phone}
                    onChange={event => updateProfileField('phone', event.target.value)}
                  />
                </FieldControl>
                <FieldControl label="Address">
                  <textarea
                    value={profileForm.address}
                    onChange={event => updateProfileField('address', event.target.value)}
                  />
                </FieldControl>
                <button className="primaryButton" onClick={() => onSaveProfile(profileForm)}>Save profile</button>
              </div>
              <ChangePasswordForm onChangePassword={onChangePassword} />
            </InfoPanel>
            <InfoPanel title="Account details" className={activePanel === 'profile' ? '' : 'workspacePanelHidden'}>
              <div style={{ display: 'grid', gap: '14px', padding: '4px 0' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Role Level</span>
                  <strong style={{ fontSize: '0.9rem' }}>{currentUser.role}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Email Address</span>
                  <strong style={{ fontSize: '0.9rem' }}>{currentUser.email}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Verification</span>
                  <strong style={{ fontSize: '0.9rem', color: currentUser.emailVerified ? 'var(--green)' : 'var(--orange)' }}>
                    {currentUser.emailVerified ? 'Verified' : 'Pending'}
                  </strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '4px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Last Login</span>
                  <strong style={{ fontSize: '0.9rem' }}>
                    {currentUser.lastLoginAt ? new Date(currentUser.lastLoginAt).toLocaleString() : 'Never'}
                  </strong>
                </div>
              </div>
            </InfoPanel>
            {!userBookings.length ? (
              <InfoPanel title="No bookings yet" className={activePanel === 'bookings' ? 'accountEmptyState' : 'workspacePanelHidden'}>
                <p className="emptyText">Choose a field and available time to create your first booking.</p>
                <button className="primaryButton" onClick={onStartBooking}>Book a field</button>
              </InfoPanel>
            ) : (
              <>
                <InfoPanel title="My bookings" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
                  <BookingList bookings={userBookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
                </InfoPanel>
                <InfoPanel title="Invoice and payment status" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
                  <BillingDetails detail={selectedBookingDetail} loading={billingLoading} error={billingError} />
                  <BookingChangeActions
                    booking={selectedBookingDetail}
                    preview={cancellationPreview}
                    availableSlots={availableSlots}
                    onPreview={onPreviewCancellation}
                    onCancel={onCancelBooking}
                    onRefund={onRequestRefund}
                    onReschedule={onReschedule}
                  />
                </InfoPanel>
              </>
            )}
            <InfoPanel title="Payment history" className={activePanel === 'payments' ? '' : 'workspacePanelHidden'}>
              <DataList items={accountPayments
                .map(payment => ({
                  title: payment.paymentCode,
                  meta: `${payment.bookingCode} · ${payment.paymentMethod}`,
                  value: `${payment.status} · ${formatMoney(payment.amount)}`
                }))}
              />
            </InfoPanel>
            <InfoPanel title="Membership" className={activePanel === 'membership' ? '' : 'workspacePanelHidden'}>
              {membership ? (
                <MetricGrid metrics={[
                  ['Level', membership.currentLevel],
                  ['Completed', membership.completedBookingCount],
                  ['Discount', `${membership.discountPercent}%`],
                  ['To next', membership.bookingsToNextLevel]
                ]} />
              ) : <p className="emptyText">Login as customer to view membership.</p>}
            </InfoPanel>
            <InfoPanel title="Notifications" className={activePanel === 'messages' ? '' : 'workspacePanelHidden'}>
              <DataList items={notifications.map(item => ({
                title: item.title,
                meta: item.message,
                value: item.type
              }))} />
              <CustomerIssueForm bookings={userBookings} fields={fields} onSubmit={onReportIssue} />
            </InfoPanel>
            {userBookings.length > 0 && (
              <InfoPanel title="Booking add-ons" className={activePanel === 'bookings' ? '' : 'workspacePanelHidden'}>
                <BookingServiceEditor booking={selectedBookingDetail} services={services} onSave={onUpdateBookingServices} />
              </InfoPanel>
            )}
          </div>
        </>
      ) : (
        <>
          <WorkspaceHeader
            kicker={`${currentUser.role} workspace`}
            title={currentUser.fullName}
            text="Manage your personal profile and view administrative properties."
            status={{
              label: 'System access',
              value: `${currentUser.role} Active`,
              tone: 'success'
            }}
            metrics={[]}
          />
          <div className="roleGrid accountGrid" style={{ gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
            <InfoPanel title="Personal profile">
              <div className="profileForm">
                <FieldControl label="Full name">
                  <input
                    value={profileForm.fullName}
                    onChange={event => updateProfileField('fullName', event.target.value)}
                  />
                </FieldControl>
                <FieldControl label="Phone">
                  <input
                    inputMode="tel"
                    value={profileForm.phone}
                    onChange={event => updateProfileField('phone', event.target.value)}
                  />
                </FieldControl>
                <FieldControl label="Address">
                  <textarea
                    value={profileForm.address}
                    onChange={event => updateProfileField('address', event.target.value)}
                  />
                </FieldControl>
                <button className="primaryButton" onClick={() => onSaveProfile(profileForm)}>Save profile</button>
              </div>
              <ChangePasswordForm onChangePassword={onChangePassword} />
            </InfoPanel>
            <InfoPanel title="Account details">
              <div style={{ display: 'grid', gap: '14px', padding: '4px 0' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Role Level</span>
                  <strong style={{ fontSize: '0.9rem' }}>{currentUser.role}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Email Address</span>
                  <strong style={{ fontSize: '0.9rem' }}>{currentUser.email}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Verification</span>
                  <strong style={{ fontSize: '0.9rem', color: currentUser.emailVerified ? 'var(--green)' : 'var(--orange)' }}>
                    {currentUser.emailVerified ? 'Verified' : 'Pending'}
                  </strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '4px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Last Login</span>
                  <strong style={{ fontSize: '0.9rem' }}>
                    {currentUser.lastLoginAt ? new Date(currentUser.lastLoginAt).toLocaleString() : 'Never'}
                  </strong>
                </div>
              </div>
            </InfoPanel>
          </div>
        </>
      )}
    </section>
  )
}

function BookingServiceEditor({ booking, services = [], onSave }) {
  const [quantities, setQuantities] = useState({})
  useEffect(() => {
    setQuantities(Object.fromEntries((booking?.services || []).map(item => [item.serviceId, item.quantity])))
  }, [booking?.bookingId, booking?.services])
  if (!booking || !['pending', 'confirmed'].includes(booking.status)) {
    return <p className="emptyText">Add-ons can be changed only before check-in.</p>
  }
  return (
    <div className="profileForm">
      <p className="hintText">Prices, discounts, remaining balance, and invoice are recalculated when you save.</p>
      {services.filter(service => service.status === 'active').map(service => (
        <FieldControl key={service.extraServiceId} label={`${service.serviceName} · ${formatMoney(service.unitPrice)}`}>
          <input
            type="number"
            min="0"
            max={service.maxQuantityPerBooking || service.stockQuantity || 99}
            value={quantities[service.extraServiceId] || 0}
            onChange={event => setQuantities(current => ({ ...current, [service.extraServiceId]: Number(event.target.value) }))}
          />
        </FieldControl>
      ))}
      <button className="primaryButton" onClick={() => onSave(
        Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0)
          .map(([serviceId, quantity]) => ({ serviceId: Number(serviceId), quantity: Number(quantity) })),
        booking.bookingId
      )}>Save add-ons</button>
    </div>
  )
}

function CustomerIssueForm({ bookings = [], fields = [], onSubmit }) {
  const [form, setForm] = useState({ bookingId: '', fieldId: '', title: '', description: '' })
  async function submit() {
    if (!form.title.trim() || !form.description.trim()) return
    await onSubmit({
      bookingId: form.bookingId ? Number(form.bookingId) : null,
      fieldId: !form.bookingId && form.fieldId ? Number(form.fieldId) : null,
      title: form.title.trim(),
      description: form.description.trim()
    })
    setForm({ bookingId: '', fieldId: '', title: '', description: '' })
  }
  return (
    <div className="changePasswordSection">
      <hr className="sectionDivider" />
      <h4 className="sectionSubtitle">Report a field or booking issue</h4>
      <div className="profileForm">
        <FieldControl label="Related booking (optional)">
          <select value={form.bookingId} onChange={event => setForm({ ...form, bookingId: event.target.value, fieldId: '' })}>
            <option value="">No booking selected</option>
            {bookings.map(booking => <option key={booking.bookingId} value={booking.bookingId}>{booking.bookingCode} · {booking.fieldName}</option>)}
          </select>
        </FieldControl>
        {!form.bookingId && <FieldControl label="Field (optional)">
          <select value={form.fieldId} onChange={event => setForm({ ...form, fieldId: event.target.value })}>
            <option value="">General issue</option>
            {fields.map(field => <option key={field.fieldId} value={field.fieldId}>{field.fieldName}</option>)}
          </select>
        </FieldControl>}
        <FieldControl label="Issue title"><input value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} /></FieldControl>
        <FieldControl label="What happened?"><textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} /></FieldControl>
        <button className="primaryButton" disabled={!form.title.trim() || !form.description.trim()} onClick={submit}>Send issue report</button>
      </div>
    </div>
  )
}

function BookingChangeActions({ booking, preview, availableSlots, onPreview, onCancel, onRefund, onReschedule }) {
  const [slotId, setSlotId] = useState('')
  if (!booking || !['pending', 'confirmed', 'cancelled'].includes(booking.status)) return null
  const cancellable = booking.status === 'pending' || booking.status === 'confirmed'
  const cancellationReviewed = preview?.bookingId === booking.bookingId
  const existingRefund = (booking.refunds || []).find(refund => !['rejected', 'failed'].includes(refund.status))
  return (
    <div className="profileForm" style={{ marginTop: '1rem' }}>
      {cancellable && (
        <>
          {!cancellationReviewed && (
            <button className="secondaryButton" onClick={onPreview}>Review cancellation terms</button>
          )}
          {cancellationReviewed && (
            <div className="cancellationReview">
              <strong>Cancellation terms</strong>
              <p className="hintText">{preview.policy}: refund {formatMoney(preview.refundableAmount)}, fee {formatMoney(preview.cancellationFeeAmount)}.</p>
              <div className="buttonRow noMargin">
                <button className="ghostDarkButton" onClick={onPreview}>Refresh terms</button>
                <button className="dangerButton" onClick={onCancel}>Cancel booking</button>
              </div>
            </div>
          )}
          <FieldControl label="Reschedule to available slot">
            <select value={slotId} onChange={event => setSlotId(event.target.value)}>
              <option value="">Choose a new slot</option>
              {availableSlots.map(slot => <option key={slot.slotId} value={slot.slotId}>{slot.fieldName} · {slot.slotDate} · {slot.startTime}</option>)}
            </select>
          </FieldControl>
          <button className="secondaryButton" disabled={!slotId} onClick={() => onReschedule(slotId)}>Reschedule booking</button>
        </>
      )}
      {Number(booking.refundableAmount) > 0 && !existingRefund && (
        <button className="primaryButton" onClick={onRefund}>
          {booking.status === 'cancelled' ? 'Request cancellation refund' : 'Request reschedule refund'} ({formatMoney(booking.refundableAmount)})
        </button>
      )}
      {existingRefund && (
        <div className="refundRequestState" aria-live="polite">
          <strong>{existingRefund.refundCode}</strong>
          <span>Refund {String(existingRefund.status).replaceAll('_', ' ')} · {formatMoney(existingRefund.refundAmount)}</span>
          {existingRefund.gatewayMessage && <small>{existingRefund.gatewayMessage}</small>}
        </div>
      )}
    </div>
  )
}

function ChangePasswordForm({ onChangePassword }) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPasswords, setShowPasswords] = useState(false)
  const [errors, setErrors] = useState({})

  const handleSubmit = () => {
    const issues = {}
    if (!currentPassword) issues.currentPassword = 'Current password is required.'
    const newIssues = passwordIssues(newPassword)
    if (newIssues.length) issues.newPassword = newIssues[0]
    if (newPassword !== confirmPassword) issues.confirmPassword = 'Passwords do not match.'
    if (Object.keys(issues).length) {
      setErrors(issues)
      return
    }
    onChangePassword(currentPassword, newPassword, confirmPassword)
    setCurrentPassword('')
    setNewPassword('')
    setConfirmPassword('')
    setErrors({})
  }

  return (
    <div className="changePasswordSection">
      <hr className="sectionDivider" />
      <h4 className="sectionSubtitle">Change password</h4>
      <div className="profileForm">
        <PasswordField
          label="Current password"
          value={currentPassword}
          visible={showPasswords}
          error={errors.currentPassword}
          autoComplete="current-password"
          onToggle={() => setShowPasswords(!showPasswords)}
          onChange={setCurrentPassword}
        />
        <PasswordField
          label="New password"
          value={newPassword}
          visible={showPasswords}
          error={errors.newPassword}
          autoComplete="new-password"
          hint="At least 8 characters with uppercase, lowercase, number, and special character."
          onToggle={() => setShowPasswords(!showPasswords)}
          onChange={setNewPassword}
        />
        <PasswordField
          label="Confirm new password"
          value={confirmPassword}
          visible={showPasswords}
          error={errors.confirmPassword}
          autoComplete="new-password"
          onToggle={() => setShowPasswords(!showPasswords)}
          onChange={setConfirmPassword}
        />
        <button className="primaryButton" onClick={handleSubmit}>Change password</button>
      </div>
    </div>
  )
}

function profileFromUser(user) {
  return {
    fullName: user.fullName || '',
    phone: user.phone || '',
    address: user.address || '',
    avatarUrl: user.avatarUrl || ''
  }
}
