import { useState } from 'react'
import { FieldControl, SectionIntro } from '../components/common'
import api from '../services/api'
import { formatMoney, tomorrow } from '../utils/format'

export function AvailabilityAssistantPage({ fieldTypes, currentUser, navigatePage }) {
  const [form, setForm] = useState({ date: tomorrow(), preferredTime: '', fieldTypeId: '', maxPrice: '' })
  const [suggestions, setSuggestions] = useState([])
  const [message, setMessage] = useState('Tell me when and what kind of field you want. I will rank available options with reasons.')

  const update = (key, value) => setForm(current => ({ ...current, [key]: value }))
  async function findSlots() {
    try {
      const params = { date: form.date }
      if (form.preferredTime) params.preferredTime = form.preferredTime
      if (form.fieldTypeId) params.fieldTypeId = form.fieldTypeId
      if (form.maxPrice) params.maxPrice = form.maxPrice
      if (currentUser?.role === 'Customer') params.customerId = currentUser.userId
      const response = await api.get('/slots/suggestions', { params })
      setSuggestions(response.data)
      setMessage(response.data.length ? `I found ${response.data.length} available option(s), ranked for your request.` : 'No matching slots found. Try a different time, type, or budget.')
    } catch (error) {
      setMessage(error.response?.data?.error || 'Could not find available slots.')
    }
  }

  function bookSuggestedSlot(slot) {
    sessionStorage.setItem('goalzoneSuggestedSlot', JSON.stringify({ slotId: slot.slotId, slotDate: slot.slotDate }))
    navigatePage('booking')
  }

  return (
    <section className="section limitWidth">
      <SectionIntro kicker="Smart availability" title="Find the right field" text="Rule-based suggestions from live availability, your preferred time, budget, and eligible promotions." />
      <div className="promoForm">
        <div className="promoFormGrid">
          <FieldControl label="Date"><input type="date" value={form.date} onChange={e => update('date', e.target.value)} min={tomorrow()} /></FieldControl>
          <FieldControl label="Preferred time"><input type="time" value={form.preferredTime} onChange={e => update('preferredTime', e.target.value)} /></FieldControl>
          <FieldControl label="Field type"><select value={form.fieldTypeId} onChange={e => update('fieldTypeId', e.target.value)}><option value="">Any type</option>{fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}</select></FieldControl>
          <FieldControl label="Maximum price (USD)"><input type="number" min="0" value={form.maxPrice} onChange={e => update('maxPrice', e.target.value)} /></FieldControl>
        </div>
        <button className="primaryButton" type="button" onClick={findSlots}>Find best slots</button>
      </div>
      <p className="emptyText">{message}</p>
      <div className="promoCardGrid">
        {suggestions.map(slot => <article key={slot.slotId} className="promoCardFull">
          <div className="promoCardFullTitle"><div><h3>{slot.fieldName}</h3><p className="promoEligibility">{slot.fieldType} · {slot.slotDate} · {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}</p></div><span className="promoDiscountBig">{formatMoney(slot.price)}</span></div>
          <ul>{slot.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
          {slot.eligiblePromotionCodes.length > 0 && <p className="promoEligibility">Promotion codes: {slot.eligiblePromotionCodes.join(', ')}</p>}
          <button type="button" className="primaryButton" onClick={() => bookSuggestedSlot(slot)}>Book this slot</button>
        </article>)}
      </div>
    </section>
  )
}
