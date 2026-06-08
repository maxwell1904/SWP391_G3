import { CalendarDays } from 'lucide-react'
import { FieldControl, SectionIntro } from '../components/common'
import {
  CheckoutSummary,
  SelectedSlot,
  ServicePicker,
  SlotList
} from '../features/booking/components'

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
          <div className="stackedActions">
            {canOperate ? (
              <button className="ghostDarkButton" onClick={() => createBooking('walk_in')}>Create walk-in booking</button>
            ) : (
              <button className="primaryButton wide" onClick={() => createBooking('online')}>
                <CalendarDays size={18} />
                <span>{currentUser ? 'Reserve field' : 'Sign in to book'}</span>
              </button>
            )}
          </div>
          {!currentUser && <p className="hintText">You can browse prices now. Login or register is required before the booking is saved.</p>}
        </aside>
      </div>
    </section>
  )
}
