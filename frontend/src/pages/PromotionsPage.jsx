import { SectionIntro } from '../components/common'
import { formatMoney } from '../utils/format'

export function PromotionsPage({ promotions }) {
  return (
    <section id="promotions" className="section promotionSection">
      <SectionIntro
        kicker="Promotions"
        title="Active offers and membership benefits"
        text="Save on selected matches and unlock member discounts after completed bookings."
      />
      <div className="promoGrid">
        {promotions.map(promotion => (
          <article className="promoCard" key={promotion.promotionId}>
            <span>{promotion.promotionCode}</span>
            <h3>{promotion.promotionName}</h3>
            <p>{promotion.description}</p>
            <strong>{promotion.discountType === 'percent' ? `${promotion.discountValue}% off` : `${formatMoney(promotion.discountValue)} off`}</strong>
          </article>
        ))}
      </div>
    </section>
  )
}
