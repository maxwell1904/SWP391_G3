import { useMemo, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { FieldControl, SectionIntro } from '../components/common'
import {
  CheckoutSummary,
  PaymentOptionSelector,
  SelectedSlot,
  ServicePicker,
  SlotList
} from '../features/booking/components'
import { PayPalCheckout } from '../features/payments/components'
import { today } from '../utils/format'

export function BookingPage({
  searchDate,
  setSearchDate,
  fieldTypeFilter,
  setFieldTypeFilter,
  fieldFilter,
  setFieldFilter,
  fieldTypes,
  fields,
  canOperate,
  selectedCustomerId,
  setSelectedCustomerId,
  customers,
  slots,
  selectedSlotId,
  setSelectedSlotId,
  services,
  selectedServices,
  setSelectedServices,
  selectedSlot,
  promotionCode,
  setPromotionCode,
  checkout,
  checkoutLoading,
  checkoutError,
  paymentOption,
  setPaymentOption,
  paypalConfig,
  paypalConfigError,
  preparePayPalBooking,
  completePayPalPayment,
  cancelPayPalPayment,
  failPayPalPayment,
  createBooking,
  currentUser
}) {
  const isCustomer = currentUser?.role === 'Customer'
  const isAdministrator = currentUser?.role === 'Admin'
  const [customerQuery, setCustomerQuery] = useState('')
  const [customerMode, setCustomerMode] = useState('registered')
  const [guestContact, setGuestContact] = useState({ name: '', phone: '', email: '' })
  const filteredCustomers = useMemo(() => {
    const query = customerQuery.trim().toLowerCase()
    if (!query) return customers
    return customers.filter(customer => [customer.phone, customer.email, customer.fullName]
      .some(value => String(value || '').toLowerCase().includes(query)))
  }, [customerQuery, customers])
  const guestReady = guestContact.name.trim().length >= 2 && guestContact.phone.trim().length >= 7
  const walkInCustomerReady = customerMode === 'guest' ? guestReady : Boolean(selectedCustomerId)

  function chooseCustomerMode(mode) {
    setCustomerMode(mode)
    setSelectedCustomerId(null)
  }

  if (isAdministrator) {
    return (
      <section id="booking" className="section bookingSection">
        <SectionIntro
          kicker="Booking desk"
          title="Customer checkout is not available to administrators"
          text="Online checkout belongs to the customer journey. Walk-in bookings and cash collection are handled from the Staff workspace, while this account manages operations and configuration."
        />
      </section>
    )
  }

  return (
    <section id="booking" className="section bookingSection">
      <SectionIntro
        kicker={canOperate ? 'Walk-in booking' : 'Booking'}
        title={canOperate ? 'Create a booking at the venue' : 'Search, price, and reserve'}
        text={canOperate
          ? 'Use an existing account or record a first-time visitor by name and phone, then choose a slot and payment option.'
          : 'Pick a free slot, add match services, apply a promotion, and see the deposit before the booking is saved.'}
      />
      <div className="bookingLayout">
        <div className="bookingMain">
          {canOperate && (
            <section className="walkInIdentityPanel" aria-labelledby="walk-in-customer-heading">
              <div className="walkInIdentityIntro">
                <div>
                  <span>Customer details</span>
                  <h3 id="walk-in-customer-heading">Who is making this booking?</h3>
                </div>
                <div className="segmented" role="group" aria-label="Walk-in customer type">
                  <button type="button" className={customerMode === 'registered' ? 'active' : ''} onClick={() => chooseCustomerMode('registered')}>
                    Registered customer
                  </button>
                  <button type="button" className={customerMode === 'guest' ? 'active' : ''} onClick={() => chooseCustomerMode('guest')}>
                    First-time visitor
                  </button>
                </div>
              </div>

              {customerMode === 'registered' ? (
                <div className="customerLookupControl">
                  <FieldControl label="Phone or email">
                    <input
                      name="customerSearch"
                      type="search"
                      value={customerQuery}
                      placeholder="0901234567 or email@example.com"
                      onChange={event => setCustomerQuery(event.target.value)}
                    />
                  </FieldControl>
                  <FieldControl label="Matched customer">
                    <select name="customer" value={selectedCustomerId ?? ''} onChange={event => setSelectedCustomerId(event.target.value ? Number(event.target.value) : null)}>
                      <option value="">Choose matched customer</option>
                      {filteredCustomers.map(user => (
                        <option key={user.userId} value={user.userId}>{user.phone} · {user.fullName} · {user.email}</option>
                      ))}
                    </select>
                  </FieldControl>
                  {!filteredCustomers.length && (
                    <p className="hintText">No account found. Use “First-time visitor” to book without registration.</p>
                  )}
                </div>
              ) : (
                <div className="walkInGuestFields">
                  <FieldControl label="Guest name">
                    <input name="guestName" value={guestContact.name} placeholder="Nguyen Van An" onChange={event => setGuestContact(current => ({ ...current, name: event.target.value }))} />
                  </FieldControl>
                  <FieldControl label="Phone number">
                    <input name="guestPhone" type="tel" value={guestContact.phone} placeholder="0901234567" onChange={event => setGuestContact(current => ({ ...current, phone: event.target.value }))} />
                  </FieldControl>
                  <FieldControl label="Email (optional)">
                    <input name="guestEmail" type="email" value={guestContact.email} placeholder="guest@example.com" onChange={event => setGuestContact(current => ({ ...current, email: event.target.value }))} />
                  </FieldControl>
                  <p className="hintText">This records contact details on the booking only. It does not create a customer account or membership.</p>
                </div>
              )}
            </section>
          )}
          <div className="filterRow">
            <FieldControl label="Playing date">
              <input name="playingDate" type="date" min={today()} value={searchDate} onChange={event => setSearchDate(event.target.value)} />
            </FieldControl>
            <FieldControl label="Field type">
              <select name="fieldType" value={fieldTypeFilter} onChange={event => setFieldTypeFilter(event.target.value)}>
                <option value="">All</option>
                {fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}
              </select>
            </FieldControl>
            <FieldControl label="Field">
              <select name="field" value={fieldFilter} onChange={event => setFieldFilter(event.target.value)}>
                <option value="">All fields</option>
                {fields
                  .filter(field => !fieldTypeFilter || Number(field.fieldTypeId) === Number(fieldTypeFilter))
                  .map(field => <option key={field.fieldId} value={field.fieldId}>{field.fieldName}</option>)}
              </select>
            </FieldControl>
          </div>
          <div className="bookingStepHeader">
            <div><span>Step 1</span><h3>Choose a time</h3></div>
            <p>{slots.filter(slot => slot.available).length} available slot(s)</p>
          </div>
          <SlotList slots={slots} selectedSlotId={selectedSlotId} onSelect={setSelectedSlotId} />
          <ServicePicker
            services={services}
            selectedServices={selectedServices}
            setSelectedServices={setSelectedServices}
            disabled={!selectedSlot}
          />
        </div>
        <aside className="checkoutPanel">
          <div className="checkoutStepHeader">
            <span>Step 3</span>
            <h3>{canOperate ? 'Confirm walk-in booking' : 'Review & pay'}</h3>
          </div>
          <SelectedSlot slot={selectedSlot} />
          <FieldControl label="Promotion code">
            <input
              name="promotionCode"
              autoComplete="off"
              value={promotionCode}
              placeholder="Example: WELCOME10"
              onChange={event => setPromotionCode(event.target.value.toUpperCase())}
            />
          </FieldControl>
          <CheckoutSummary checkout={checkout} loading={checkoutLoading} error={checkoutError} />
          <PaymentOptionSelector checkout={checkout} value={paymentOption} onChange={setPaymentOption} allowPayLater={canOperate} />
          <div className="stackedActions">
            {canOperate ? (
              <button className="ghostDarkButton" disabled={!checkout || !walkInCustomerReady} onClick={() => createBooking('walk_in', paymentOption, customerMode === 'guest' ? guestContact : null)}>
                {paymentOption === 'pay_later'
                  ? 'Create walk-in booking — pay later'
                  : `Create walk-in & record ${paymentOption === 'full' ? 'full cash payment' : 'cash deposit'}`}
              </button>
            ) : isCustomer ? (
              <PayPalCheckout
                key={`${selectedSlotId}-${paymentOption}-${promotionCode}-${JSON.stringify(selectedServices)}`}
                config={paypalConfig}
                disabled={!checkout || currentUser.accountLocked}
                paymentOption={paymentOption}
                onPrepareBooking={preparePayPalBooking}
                onPaymentComplete={completePayPalPayment}
                onCancel={cancelPayPalPayment}
                onError={failPayPalPayment}
              />
            ) : (
              <button className="primaryButton wide" disabled={!checkout} onClick={() => createBooking('online', paymentOption)}>
                <CalendarDays size={18} />
                <span>Sign in to book</span>
              </button>
            )}
          </div>
          {isCustomer && paypalConfigError && <p className="errorText">{paypalConfigError}</p>}
          {!currentUser && <p className="hintText">You can browse prices now. Login or register is required before the booking is saved.</p>}
          {canOperate && !walkInCustomerReady && (
            <p className="hintText">{customerMode === 'guest' ? 'Enter the guest name and phone number before saving.' : 'Select the customer matched by phone or email.'}</p>
          )}
          {currentUser?.accountLocked && (
            <p className="errorText" style={{ marginTop: '0.5rem', color: 'var(--orange, #ff6b6b)' }}>
              Your account is locked: {currentUser.lockReason || 'Please check your email for details.'}
            </p>
          )}
        </aside>
      </div>
    </section>
  )
}
