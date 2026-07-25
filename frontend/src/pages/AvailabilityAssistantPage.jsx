import { useState } from 'react'
import { ArrowUp, Sparkles } from 'lucide-react'
import { FieldControl, SectionIntro } from '../components/common'
import api from '../services/api'
import { formatMoney, tomorrow } from '../utils/format'

export function AvailabilityAssistantPage({ fieldTypes, currentUser, navigatePage }) {
  const [form, setForm] = useState({ date: tomorrow(), preferredTime: '', fieldTypeId: '', maxPrice: '' })
  const [suggestions, setSuggestions] = useState([])
  const [message, setMessage] = useState('Tell me when and what kind of field you want. I will rank available options with reasons.')
  const [question, setQuestion] = useState('')
  const [aiLoading, setAiLoading] = useState(false)
  const [assistantReply, setAssistantReply] = useState('')

  const update = (key, value) => setForm(current => ({ ...current, [key]: value }))
  async function findSlots() {
    try {
      setAssistantReply('')
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

  async function askAssistant() {
    if (!question.trim()) return
    if (!currentUser) {
      setAssistantReply('Sign in as a customer before asking the AI assistant.')
      setMessage('')
      return
    }
    try {
      setAiLoading(true)
      setAssistantReply('')
      setMessage('')
      const response = await api.post('/assistant/availability', { question: question.trim() })
      const criteria = response.data.criteria || {}
      setForm(current => ({
        ...current,
        date: criteria.date || current.date,
        preferredTime: criteria.preferredTime || '',
        fieldTypeId: criteria.fieldTypeId ? String(criteria.fieldTypeId) : '',
        maxPrice: criteria.maxPrice ?? ''
      }))
      setSuggestions(response.data.suggestions || [])
      setAssistantReply(response.data.answer || 'I checked the live availability for you.')
    } catch (error) {
      setAssistantReply(error.response?.data?.error || 'The AI assistant could not answer right now. You can still use the filters below.')
    } finally {
      setAiLoading(false)
    }
  }

  function bookSuggestedSlot(slot) {
    sessionStorage.setItem('goalzoneSuggestedSlot', JSON.stringify({ slotId: slot.slotId, slotDate: slot.slotDate }))
    navigatePage('booking')
  }

  return (
    <section className="section limitWidth">
      <SectionIntro kicker="Smart availability" title="Find the right field" text="Ask Gemini in English, then get recommendations from live availability, your budget, and eligible promotions." />
      <div className="assistantAskCard">
        <div className="assistantAskHeading">
          <div><p className="eyebrow">Gemini availability assistant</p><h2>Describe your ideal game</h2></div>
          <span className="assistantLiveBadge">Live slot data</span>
        </div>
        <p className="assistantSubheading">Use plain English — time, field size, budget, and promotions are enough.</p>
        <div className="assistantComposer">
          <span className="assistantSparkle" aria-hidden="true"><Sparkles size={20} strokeWidth={2.3} /></span>
          <textarea id="assistant-question" aria-label="Ask Gemini for a field" value={question} onChange={e => setQuestion(e.target.value)} maxLength="500" rows="1" placeholder="Find a 5-a-side field tomorrow around 7 PM for under $25..." />
          <button className="assistantSendButton" type="button" onClick={askAssistant} disabled={aiLoading || !question.trim()} aria-label="Ask Gemini">
            {aiLoading ? <span className="assistantLoadingDot" /> : <ArrowUp size={20} strokeWidth={2.8} />}
          </button>
        </div>
        <div className="assistantPromptRow">
          <span>Try asking</span>
          <button className="assistantExample" type="button" onClick={() => setQuestion('Find me a 5-a-side field tomorrow around 7 PM for under $25, with any eligible promotion.')}>5-a-side tomorrow at 7 PM</button>
          <button className="assistantExample" type="button" onClick={() => setQuestion('Show me the most affordable field available tomorrow evening.')}>Cheapest field tomorrow evening</button>
        </div>
        <p className="assistantHint">Gemini understands your request; the displayed fields, times, prices, and promotions always come from GoalZone&apos;s live booking data.</p>
      </div>
      <div className="promoForm">
        <div className="promoFormGrid">
          <FieldControl label="Date"><input type="date" value={form.date} onChange={e => update('date', e.target.value)} min={tomorrow()} /></FieldControl>
          <FieldControl label="Preferred time"><input type="time" value={form.preferredTime} onChange={e => update('preferredTime', e.target.value)} /></FieldControl>
          <FieldControl label="Field type"><select value={form.fieldTypeId} onChange={e => update('fieldTypeId', e.target.value)}><option value="">Any type</option>{fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}</select></FieldControl>
          <FieldControl label="Maximum price (USD)"><input type="number" min="0" value={form.maxPrice} onChange={e => update('maxPrice', e.target.value)} /></FieldControl>
        </div>
        <button className="primaryButton" type="button" onClick={findSlots}>Find best slots</button>
      </div>
      {assistantReply && <div className="assistantResponse"><span aria-hidden="true"><Sparkles size={18} /></span><p>{assistantReply.replaceAll('**', '')}</p></div>}
      {message && <p className="emptyText">{message}</p>}
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
