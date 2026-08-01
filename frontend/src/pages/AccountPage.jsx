import { useEffect, useState } from 'react'
import { FieldControl, InfoPanel, PasswordField, WorkspaceHeader, WorkspaceTabs } from '../components/common'
import { DataList, MetricGrid } from '../components/data'
import { EmailVerificationPanel } from '../features/account/components'
import { CustomerBookingWorkspace } from '../features/operations/components/CustomerBookingWorkspace'
import { IssueCaseList } from '../features/support/components'
import { passwordIssues } from '../features/auth/authRules'
import { formatDate, formatDateTime, formatMoney, formatTime, humanizeStatus, paymentMethodLabel } from '../utils/format'
import { useWorkspaceTab } from '../hooks/useWorkspaceTab'

const accountTabs = ['bookings', 'payments', 'membership', 'profile', 'messages']

export function AccountPage({
  currentUser,
  userBookings,
  payments,
  membership,
  notifications,
  issues,
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
  services,
  fields,
  onUpdateBookingServices,
  onReportIssue,
  onStartBooking
}) {
  const [profileForm, setProfileForm] = useState(() => profileFromUser(currentUser))
  const [activePanel, setActivePanel] = useWorkspaceTab('bookings', accountTabs)

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
          {currentUser.accountLocked && (
            <div className="accountVerification" style={{ borderLeftColor: '#ff6b6b', background: '#fff5f5' }}>
              <div className="verificationPanel">
                <h4 style={{ color: '#e53e3e', margin: '0 0 6px 0' }}>Account Locked</h4>
                <p style={{ color: '#4a5568', margin: 0, fontSize: '0.9rem' }}>
                  Your account is locked. Please check your email for details. Reason: <strong>{currentUser.lockReason || 'Locked by admin.'}</strong>
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
          {activePanel === 'bookings' && (
            <CustomerBookingWorkspace
              bookings={userBookings}
              selectedBookingId={selectedBookingId}
              onSelectBooking={setSelectedBookingId}
              selectedBooking={selectedBookingDetail}
              billingLoading={billingLoading}
              billingError={billingError}
              cancellationPreview={cancellationPreview}
              services={services}
              onPreviewCancellation={onPreviewCancellation}
              onCancelBooking={onCancelBooking}
              onRequestRefund={onRequestRefund}
              onReschedule={onReschedule}
              onUpdateBookingServices={onUpdateBookingServices}
              onStartBooking={onStartBooking}
            />
          )}
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
            <InfoPanel title="Payment history" className={activePanel === 'payments' ? '' : 'workspacePanelHidden'}>
              <DataList items={accountPayments
                .map(payment => ({
                  title: payment.paymentCode,
                  meta: [
                    payment.bookingCode,
                    payment.fieldName,
                    payment.slotDate ? `${formatDate(payment.slotDate)} ${formatTime(payment.startTime)}` : null,
                    paymentMethodLabel(payment.paymentMethod),
                    payment.paidAt ? `Paid ${formatDateTime(payment.paidAt)}` : null
                  ].filter(Boolean).join(' · '),
                  value: `${humanizeStatus(payment.status)} · ${formatMoney(payment.amount)}`,
                  wrapMeta: true
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
              <hr className="sectionDivider" />
              <h4 className="sectionSubtitle">My reported issues</h4>
              <IssueCaseList issues={issues} />
              <CustomerIssueForm bookings={userBookings} fields={fields} onSubmit={onReportIssue} />
            </InfoPanel>
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

function CustomerIssueForm({ bookings = [], fields = [], onSubmit }) {
  const [form, setForm] = useState({ bookingId: '', fieldId: '', title: '', description: '' })
  async function submit() {
    if (!form.title.trim() || !form.description.trim()) return
    const saved = await onSubmit({
      bookingId: form.bookingId ? Number(form.bookingId) : null,
      fieldId: !form.bookingId && form.fieldId ? Number(form.fieldId) : null,
      title: form.title.trim(),
      description: form.description.trim()
    })
    if (!saved) return
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

function ChangePasswordForm({ onChangePassword }) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPasswords, setShowPasswords] = useState(false)
  const [errors, setErrors] = useState({})

  const handleSubmit = async () => {
    const issues = {}
    if (!currentPassword) issues.currentPassword = 'Current password is required.'
    const newIssues = passwordIssues(newPassword)
    if (newIssues.length) issues.newPassword = newIssues[0]
    if (newPassword !== confirmPassword) issues.confirmPassword = 'Passwords do not match.'
    if (Object.keys(issues).length) {
      setErrors(issues)
      return
    }
    const result = await onChangePassword(currentPassword, newPassword, confirmPassword)
    if (!result) return
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
    address: user.address || ''
  }
}
