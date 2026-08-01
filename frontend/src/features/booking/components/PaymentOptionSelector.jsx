import { formatMoney } from '../../../utils/format'

export function PaymentOptionSelector({ checkout, value, onChange, allowPayLater = false }) {
  if (!checkout) return null

  const options = [
    {
      value: 'deposit',
      title: 'Pay deposit',
      amount: checkout.depositAmount,
      detail: `${formatMoney(checkout.remainingAfterDeposit)} remains for venue payment`
    },
    {
      value: 'full',
      title: 'Pay in full',
      amount: checkout.totalAmount,
      detail: 'No remaining balance after this payment'
    },
    ...(allowPayLater ? [{
      value: 'pay_later',
      title: 'Pay later at venue',
      amount: 0,
      detail: `${formatMoney(checkout.totalAmount)} remains unpaid; booking stays pending`
    }] : [])
  ]

  return (
    <fieldset className="paymentChoice">
      <legend>Payment option</legend>
      <div className="paymentChoiceGrid">
        {options.map(option => (
          <label className={value === option.value ? 'paymentOption selected' : 'paymentOption'} key={option.value}>
            <input
              type="radio"
              name="paymentOption"
              value={option.value}
              checked={value === option.value}
              onChange={event => onChange(event.target.value)}
            />
            <span>
              <strong>{option.title}</strong>
              <small>{option.detail}</small>
            </span>
            <em>{formatMoney(option.amount)}</em>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
