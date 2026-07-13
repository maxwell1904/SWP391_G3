import { useState } from 'react'
import { SectionIntro, FieldControl } from '../components/common'
import { formatMoney, tomorrow } from '../utils/format'

const PAGE_SIZE = 5

export function PromotionsPage({ promotions, currentUser, createPromotion, updatePromotion, fieldTypes = [], services = [], membershipLevels = [] }) {
  const isAdmin = currentUser?.role === 'Admin'
  const [isEditing, setIsEditing] = useState(false)
  const [editForm, setEditForm] = useState(emptyForm())
  const [page, setPage] = useState(1)

  const visiblePromotions = isAdmin ? promotions : promotions.filter(p => p.status === 'active')
  const totalPages = Math.max(1, Math.ceil(visiblePromotions.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const paged = visiblePromotions.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  function handleEdit(promo) {
    setEditForm({
      promotionId: promo.promotionId,
      promotionCode: promo.promotionCode,
      promotionName: promo.promotionName,
      description: promo.description || '',
      bannerUrl: promo.bannerUrl || '',
      discountType: promo.discountType || 'percent',
      discountValue: promo.discountValue || 0,
      maxDiscountAmount: promo.maxDiscountAmount || '',
      minBookingAmount: promo.minBookingAmount || '',
      usageLimit: promo.usageLimit || 100,
      startDate: promo.startDate || tomorrow(),
      endDate: promo.endDate || tomorrow(),
      status: promo.status || 'active',
      applicableFieldTypeId: promo.applicableFieldTypeId || '',
      applicableExtraServiceId: promo.applicableExtraServiceId || '',
      applicableMembershipLevelId: promo.applicableMembershipLevelId || ''
    })
    setIsEditing(true)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function handleNew() {
    setEditForm(emptyForm())
    setIsEditing(true)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function handleSave() {
    const payload = {
      ...editForm,
      discountValue: Number(editForm.discountValue),
      maxDiscountAmount: editForm.maxDiscountAmount ? Number(editForm.maxDiscountAmount) : null,
      minBookingAmount: editForm.minBookingAmount ? Number(editForm.minBookingAmount) : null,
      usageLimit: Number(editForm.usageLimit),
      applicableFieldTypeId: editForm.applicableFieldTypeId ? Number(editForm.applicableFieldTypeId) : null,
      applicableExtraServiceId: editForm.applicableExtraServiceId ? Number(editForm.applicableExtraServiceId) : null,
      applicableMembershipLevelId: editForm.applicableMembershipLevelId ? Number(editForm.applicableMembershipLevelId) : null
    }
    if (editForm.promotionId) {
      await updatePromotion(editForm.promotionId, payload)
    } else {
      await createPromotion(payload)
    }
    setIsEditing(false)
  }

  const updateField = (field, value) => setEditForm(prev => ({ ...prev, [field]: value }))

  function goToPage(p) {
    setPage(Math.max(1, Math.min(totalPages, p)))
    const el = document.getElementById('promotions')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <section id="promotions" className="section promotionSection">
      <SectionIntro
        kicker="Promotions"
        title="Active offers and membership benefits"
        text="Save on selected matches and unlock member discounts after completed bookings."
      />

      {isAdmin && (
        <div className="adminControls">
          {!isEditing && (
            <button className="primaryButton" onClick={handleNew}>+ Add New Promotion</button>
          )}

          {isEditing && (
            <div className="promoForm">
              <h3>{editForm.promotionId ? 'Edit Promotion' : 'New Promotion'}</h3>
              <div className="promoFormGrid">
                <FieldControl label="Code">
                  <input value={editForm.promotionCode} onChange={e => updateField('promotionCode', e.target.value)} placeholder="e.g. SUMMER20" />
                </FieldControl>
                <FieldControl label="Name">
                  <input value={editForm.promotionName} onChange={e => updateField('promotionName', e.target.value)} placeholder="Promotion name" />
                </FieldControl>
                <FieldControl label="Discount Type">
                  <select value={editForm.discountType} onChange={e => updateField('discountType', e.target.value)}>
                    <option value="percent">Percent (%)</option>
                    <option value="fixed_amount">Fixed Amount (USD)</option>
                  </select>
                </FieldControl>
                <FieldControl label={editForm.discountType === 'percent' ? 'Discount (%)' : 'Discount (USD)'}>
                  <input type="number" value={editForm.discountValue} onChange={e => updateField('discountValue', e.target.value)} min="0" />
                </FieldControl>
                <FieldControl label="Max Discount USD (optional)">
                  <input type="number" value={editForm.maxDiscountAmount} onChange={e => updateField('maxDiscountAmount', e.target.value)} min="0" placeholder="Leave blank for no cap" />
                </FieldControl>
                <FieldControl label="Min Booking USD (optional)">
                  <input type="number" value={editForm.minBookingAmount} onChange={e => updateField('minBookingAmount', e.target.value)} min="0" placeholder="Leave blank for any amount" />
                </FieldControl>
                <FieldControl label="Start Date">
                  <input type="date" value={editForm.startDate} onChange={e => updateField('startDate', e.target.value)} />
                </FieldControl>
                <FieldControl label="End Date">
                  <input type="date" value={editForm.endDate} onChange={e => updateField('endDate', e.target.value)} />
                </FieldControl>
                <FieldControl label="Usage Limit">
                  <input type="number" value={editForm.usageLimit} onChange={e => updateField('usageLimit', e.target.value)} min="1" />
                </FieldControl>
                <FieldControl label="Status">
                  <select value={editForm.status} onChange={e => updateField('status', e.target.value)}>
                    <option value="active">Active</option>
                    <option value="inactive">Inactive</option>
                  </select>
                </FieldControl>
                <FieldControl label="Condition: Field Type">
                  <select value={editForm.applicableFieldTypeId} onChange={e => updateField('applicableFieldTypeId', e.target.value)}>
                    <option value="">-- All Field Types --</option>
                    {fieldTypes.map(ft => (
                      <option key={ft.fieldTypeId} value={ft.fieldTypeId}>{ft.typeName}</option>
                    ))}
                  </select>
                </FieldControl>
                <FieldControl label="Condition: Extra Service">
                  <select value={editForm.applicableExtraServiceId} onChange={e => updateField('applicableExtraServiceId', e.target.value)}>
                    <option value="">-- All Services --</option>
                    {services.map(svc => (
                      <option key={svc.extraServiceId} value={svc.extraServiceId}>{svc.serviceName}</option>
                    ))}
                  </select>
                </FieldControl>
                <FieldControl label="Condition: Membership">
                  <select value={editForm.applicableMembershipLevelId} onChange={e => updateField('applicableMembershipLevelId', e.target.value)}>
                    <option value="">-- All Memberships --</option>
                    {membershipLevels.map(ml => (
                      <option key={ml.membershipLevelId} value={ml.membershipLevelId}>{ml.levelName}</option>
                    ))}
                  </select>
                </FieldControl>
                <div className="fullWidth">
                  <FieldControl label="Description">
                    <textarea value={editForm.description} onChange={e => updateField('description', e.target.value)} placeholder="Short description for customers" />
                  </FieldControl>
                </div>
              </div>
              <div className="buttonRow">
                <button className="primaryButton" onClick={handleSave}>Save</button>
                <button className="secondaryButton" onClick={() => setIsEditing(false)}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Summary line */}
      <p className="promoSummaryLine">
        Showing {paged.length} of {visiblePromotions.length} promotion{visiblePromotions.length !== 1 ? 's' : ''}
        {isAdmin && promotions.filter(p => p.status === 'inactive').length > 0 &&
          ` · ${promotions.filter(p => p.status === 'inactive').length} inactive`}
      </p>

      <div className="promoCardGrid">
        {paged.map(promotion => (
          <div
            key={promotion.promotionId}
            className={`promoCardFull ${promotion.status === 'inactive' ? 'inactive' : ''}`}
          >
            {/* Header row */}
            <div className="promoCardFullHeader">
              <span className="promoCodeBadge">{promotion.promotionCode}</span>
              {isAdmin && (
                <span className={`promoStatusPill ${promotion.status}`}>
                  {promotion.status}
                </span>
              )}
            </div>

            {/* Title + discount */}
            <div className="promoCardFullTitle">
              <div>
                <h3>{promotion.promotionName}</h3>
                {(promotion.applicableFieldTypeId || promotion.applicableExtraServiceId || promotion.applicableMembershipLevelId) && (
                  <div className="promoConditions" style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
                    {promotion.applicableFieldTypeId && <span className="promoConditionBadge" style={{ fontSize: '0.72rem', background: 'var(--faint)', padding: '0.15rem 0.5rem', borderRadius: '4px', color: 'var(--muted)' }}>Field Type Only</span>}
                    {promotion.applicableExtraServiceId && <span className="promoConditionBadge" style={{ fontSize: '0.72rem', background: 'var(--faint)', padding: '0.15rem 0.5rem', borderRadius: '4px', color: 'var(--muted)' }}>Service Only</span>}
                    {promotion.applicableMembershipLevelId && <span className="promoConditionBadge" style={{ fontSize: '0.72rem', background: 'var(--faint)', padding: '0.15rem 0.5rem', borderRadius: '4px', color: 'var(--muted)' }}>Membership Only</span>}
                  </div>
                )}
              </div>
              <span className="promoDiscountBig">
                {promotion.discountType === 'percent'
                  ? `${promotion.discountValue}% off`
                  : `${formatMoney(promotion.discountValue)} off`}
              </span>
            </div>

            {/* Description */}
            {promotion.description && (
              <p className="promoCardDesc">{promotion.description}</p>
            )}

            {/* Detail grid */}
            <dl className="promoDetailGrid">
              <div>
                <dt>Valid from</dt>
                <dd>{promotion.startDate}</dd>
              </div>
              <div>
                <dt>Valid until</dt>
                <dd>{promotion.endDate}</dd>
              </div>
              {promotion.usageLimit && (
                <div>
                  <dt>Usage limit</dt>
                  <dd>{promotion.usedCount ?? 0} / {promotion.usageLimit} used</dd>
                </div>
              )}
              {promotion.minBookingAmount && (
                <div>
                  <dt>Min booking</dt>
                  <dd>{formatMoney(promotion.minBookingAmount)}</dd>
                </div>
              )}
              {promotion.maxDiscountAmount && (
                <div>
                  <dt>Max discount</dt>
                  <dd>{formatMoney(promotion.maxDiscountAmount)}</dd>
                </div>
              )}
            </dl>

            {/* Admin actions */}
            {isAdmin && (
              <div className="promoCardActions">
                <button className="ghostDarkButton" onClick={() => handleEdit(promotion)}>
                  Edit
                </button>
              </div>
            )}
          </div>
        ))}

        {paged.length === 0 && (
          <p className="emptyText" style={{ gridColumn: '1/-1' }}>No promotions available right now.</p>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="promoPagination">
          <button
            className="promoPaginationBtn"
            onClick={() => goToPage(safePage - 1)}
            disabled={safePage === 1}
            aria-label="Previous page"
          >
            ‹
          </button>

          {Array.from({ length: totalPages }, (_, i) => i + 1).map(p => (
            <button
              key={p}
              className={`promoPaginationBtn ${p === safePage ? 'active' : ''}`}
              onClick={() => goToPage(p)}
              aria-label={`Page ${p}`}
              aria-current={p === safePage ? 'page' : undefined}
            >
              {p}
            </button>
          ))}

          <button
            className="promoPaginationBtn"
            onClick={() => goToPage(safePage + 1)}
            disabled={safePage === totalPages}
            aria-label="Next page"
          >
            ›
          </button>
        </div>
      )}
    </section>
  )
}

function emptyForm() {
  return {
    promotionCode: '',
    promotionName: '',
    description: '',
    bannerUrl: '',
    discountType: 'percent',
    discountValue: 10,
    maxDiscountAmount: '',
    minBookingAmount: '',
    usageLimit: 100,
    startDate: tomorrow(),
    endDate: tomorrow(),
    status: 'active',
    applicableFieldTypeId: '',
    applicableExtraServiceId: '',
    applicableMembershipLevelId: ''
  }
}
