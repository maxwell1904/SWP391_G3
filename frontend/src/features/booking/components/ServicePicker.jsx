import { formatMoney } from '../../../utils/format'

export function ServicePicker({ services, selectedServices, setSelectedServices }) {
  return (
    <div className="serviceBlock">
      <div className="bookingStepHeader">
        <div><span>Step 2</span><h3>Add services</h3></div>
        <p>Optional</p>
      </div>
      <div className="serviceGrid">
        {services.map(service => (
          <label key={service.extraServiceId} className="serviceOption">
            <span>
              <strong>{service.serviceName}</strong>
              <small>{formatMoney(service.unitPrice)} / {service.unitName}</small>
            </span>
            <input
              type="number"
              min="0"
              max={service.maxQuantityPerBooking || 5}
              value={selectedServices[service.extraServiceId] || 0}
              onChange={event => setSelectedServices({
                ...selectedServices,
                [service.extraServiceId]: event.target.value
              })}
            />
          </label>
        ))}
      </div>
    </div>
  )
}
