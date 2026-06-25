import { ArrowRight } from 'lucide-react'
import { FieldControl, SearchIcon } from '../components/common'

const heroImage = 'https://images.unsplash.com/photo-1556056504-5c7696c4c28d?auto=format&fit=crop&w=2200&q=85'

export function HomePage({ searchDate, setSearchDate, fieldTypeFilter, setFieldTypeFilter, fieldTypes, navigatePage }) {
  return (
    <section id="home" className="hero" style={{ '--hero-image': `url(${heroImage})` }}>
      <div className="heroContent">
        <p className="venueLabel">Football field booking for local venues</p>
        <h1>GoalZone</h1>
        <p className="heroCopy">Book a field, add match services, pay the deposit, and manage the booking from one place.</p>
        <div className="heroActions">
          <button className="primaryButton" onClick={() => navigatePage('booking')}>
            <span>Book a field</span>
            <ArrowRight size={18} />
          </button>
          <button className="ghostButton" onClick={() => navigatePage('fields')}>View fields</button>
        </div>
      </div>
      <div className="availabilityStrip" aria-label="Quick availability search">
        <FieldControl label="Date">
          <input type="date" value={searchDate} onChange={event => setSearchDate(event.target.value)} />
        </FieldControl>
        <FieldControl label="Field type">
          <select value={fieldTypeFilter} onChange={event => setFieldTypeFilter(event.target.value)}>
            <option value="">All field types</option>
            {fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}
          </select>
        </FieldControl>
        <button className="stripButton" onClick={() => navigatePage('booking')}>
          <SearchIcon />
          <span>Find slots</span>
        </button>
      </div>
    </section>
  )
}
