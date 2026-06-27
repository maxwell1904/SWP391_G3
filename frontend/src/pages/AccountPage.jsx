import { useEffect, useState } from 'react'
import { FieldControl, InfoPanel, PasswordField, WorkspaceHeader } from '../components/common'
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
  resendVerification,
  onSaveProfile,
  onChangePassword
}) {
  const [profileForm, setProfileForm] = useState(() => profileFromUser(currentUser))

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
          <div className="roleGrid accountGrid">
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
            <InfoPanel title="My bookings">
              <BookingList bookings={userBookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
            </InfoPanel>
            <InfoPanel title="Invoice and payment status">
              <BillingDetails detail={selectedBookingDetail} loading={billingLoading} error={billingError} />
            </InfoPanel>
            <InfoPanel title="Payment history">
              <DataList items={accountPayments
                .map(payment => ({
                  title: payment.paymentCode,
                  meta: `${payment.bookingCode} · ${payment.paymentMethod}`,
                  value: `${payment.status} · ${formatMoney(payment.amount)}`
                }))}
              />
            </InfoPanel>
            <InfoPanel title="Membership">
              {membership ? (
                <MetricGrid metrics={[
                  ['Level', membership.currentLevel],
                  ['Completed', membership.completedBookingCount],
                  ['Discount', `${membership.discountPercent}%`],
                  ['To next', membership.bookingsToNextLevel]
                ]} />
              ) : <p className="emptyText">Login as customer to view membership.</p>}
            </InfoPanel>
            <InfoPanel title="Notifications">
              <DataList items={notifications.map(item => ({
                title: item.title,
                meta: item.message,
                value: item.type
              }))} />
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
