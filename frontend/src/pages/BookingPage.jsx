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
  fieldTypes,
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
  return (
    <section id="booking" className="section bookingSection">
      <SectionIntro
        kicker="Booking"
        title="Search, price, and reserve"
        text="Pick a free slot, add match services, apply a promotion, and see the deposit before the booking is saved."
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
            {canOperate && (
              <FieldControl label="Customer">
                <select value={selectedCustomerId} onChange={event => setSelectedCustomerId(Number(event.target.value))}>
                  {customers.map(user => <option key={user.userId} value={user.userId}>{user.fullName}</option>)}
                </select>
              </FieldControl>
            )}
          </div>
          <SlotList slots={slots} selectedSlotId={selectedSlotId} onSelect={setSelectedSlotId} />
          <ServicePicker services={services} selectedServices={selectedServices} setSelectedServices={setSelectedServices} />
        </div>
        <aside className="checkoutPanel">
          <h3>Checkout preview</h3>
          <SelectedSlot slot={selectedSlot} />
          <FieldControl label="Promotion code">
            <input
              value={promotionCode}
              placeholder="WELCOME10"
              onChange={event => setPromotionCode(event.target.value.toUpperCase())}
            />
          </FieldControl>
          <CheckoutSummary checkout={checkout} loading={checkoutLoading} error={checkoutError} />
          <PaymentOptionSelector checkout={checkout} value={paymentOption} onChange={setPaymentOption} />
          <div className="stackedActions">
            {canOperate ? (
              <button className="ghostDarkButton" disabled={!checkout} onClick={() => createBooking('walk_in', paymentOption)}>
                Create and record {paymentOption === 'full' ? 'full payment' : 'deposit'}
              </button>
            ) : currentUser ? (
              <PayPalCheckout
                key={`${selectedSlotId}-${paymentOption}-${promotionCode}-${JSON.stringify(selectedServices)}`}
                config={paypalConfig}
                disabled={!checkout || currentUser.bookingRestricted}
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
          {paypalConfigError && <p className="errorText">{paypalConfigError}</p>}
          {!currentUser && <p className="hintText">You can browse prices now. Login or register is required before the booking is saved.</p>}
          {currentUser?.bookingRestricted && (
            <p className="errorText" style={{ marginTop: '0.5rem', color: 'var(--orange, #ff6b6b)' }}>
              Your account is restricted from booking: {currentUser.restrictionReason || 'Booking restricted by admin.'}
            </p>
          )}
        </aside>
      </div>
    </section>
  )
}
