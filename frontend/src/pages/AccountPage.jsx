import { useEffect, useState } from 'react'
import { FieldControl, InfoPanel, WorkspaceHeader } from '../components/common'
import { DataList, MetricGrid } from '../components/data'
import { EmailVerificationPanel } from '../features/account/components'
import { BookingList } from '../features/operations/components'
import { formatMoney } from '../utils/format'

export function AccountPage({
  currentUser,
  userBookings,
  payments,
  membership,
  notifications,
  selectedBookingId,
  setSelectedBookingId,
  resendVerification,
  onSaveProfile
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

  return (
    <section id="account" className="section accountSection">
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
            <button className="secondaryButton" onClick={() => onSaveProfile(profileForm)}>Save profile</button>
          </div>
        </InfoPanel>
        <InfoPanel title="My bookings">
          <BookingList bookings={userBookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
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
    </section>
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
