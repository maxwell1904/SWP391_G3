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
          ? 'Choose the customer and an available slot, then record the cash payment at the counter.'
          : 'Pick a free slot, add match services, apply a promotion, and see the deposit before the booking is saved.'}
      />
      <div className="bookingLayout">
        <div className="bookingMain">
          <div className="filterRow">
            <FieldControl label="Playing date">
              <input type="date" value={searchDate} onChange={event => setSearchDate(event.target.value)} />
            </FieldControl>
            <FieldControl label="Field type">
              <select value={fieldTypeFilter} onChange={event => setFieldTypeFilter(event.target.value)}>
                <option value="">All</option>
                {fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}
              </select>
            </FieldControl>
            <FieldControl label="Field">
              <select value={fieldFilter} onChange={event => setFieldFilter(event.target.value)}>
                <option value="">All fields</option>
                {fields
                  .filter(field => !fieldTypeFilter || Number(field.fieldTypeId) === Number(fieldTypeFilter))
                  .map(field => <option key={field.fieldId} value={field.fieldId}>{field.fieldName}</option>)}
              </select>
            </FieldControl>
            {canOperate && (
              <FieldControl label="Customer">
                <select value={selectedCustomerId} onChange={event => setSelectedCustomerId(Number(event.target.value))}>
                  {customers.map(user => <option key={user.userId} value={user.userId}>{user.fullName}</option>)}
                </select>
              </FieldControl>
            )}
          </div>
          <div className="bookingStepHeader">
            <div><span>Step 1</span><h3>Choose a time</h3></div>
            <p>{slots.filter(slot => slot.available).length} available slot(s)</p>
          </div>
          <SlotList slots={slots} selectedSlotId={selectedSlotId} onSelect={setSelectedSlotId} />
          <ServicePicker services={services} selectedServices={selectedServices} setSelectedServices={setSelectedServices} />
        </div>
        <aside className="checkoutPanel">
          <h3>{canOperate ? 'Walk-in payment' : 'Checkout preview'}</h3>
          <SelectedSlot slot={selectedSlot} />
          <FieldControl label="Promotion code">
            <input
              value={promotionCode}
              placeholder="e.g. WELCOME10"
              onChange={event => setPromotionCode(event.target.value.toUpperCase())}
            />
          </FieldControl>
          <CheckoutSummary checkout={checkout} loading={checkoutLoading} error={checkoutError} />
          <PaymentOptionSelector checkout={checkout} value={paymentOption} onChange={setPaymentOption} />
          <div className="stackedActions">
            {canOperate ? (
              <button className="ghostDarkButton" disabled={!checkout} onClick={() => createBooking('walk_in', paymentOption)}>
                Create walk-in & record {paymentOption === 'full' ? 'full cash payment' : 'cash deposit'}
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
