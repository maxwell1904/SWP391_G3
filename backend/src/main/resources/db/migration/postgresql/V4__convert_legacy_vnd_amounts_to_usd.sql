-- GoalZone now uses USD as the single business and settlement currency.
-- Existing classroom data was seeded in VND at a fixed 25,000 VND/USD rate.
-- A clean database reaches this migration before the demo seeder runs, so
-- these updates affect only previously seeded/imported legacy rows.

update field_price set price = round(price / 25000, 2) where price >= 1000;
update extra_service set unit_price = round(unit_price / 25000, 2) where unit_price >= 1000;
update promotion
set discount_value = round(discount_value / 25000, 2)
where discount_type = 'fixed_amount' and discount_value >= 1000;
update promotion set max_discount_amount = round(max_discount_amount / 25000, 2) where max_discount_amount >= 1000;
update promotion set min_booking_amount = round(min_booking_amount / 25000, 2) where min_booking_amount >= 1000;

update booking set
    field_price_amount = round(field_price_amount / 25000, 2),
    service_total_amount = round(service_total_amount / 25000, 2),
    promotion_discount_amount = round(promotion_discount_amount / 25000, 2),
    membership_discount_amount = round(membership_discount_amount / 25000, 2),
    total_amount = round(total_amount / 25000, 2),
    deposit_amount = round(deposit_amount / 25000, 2),
    paid_amount = round(paid_amount / 25000, 2),
    remaining_amount = round(remaining_amount / 25000, 2),
    cancellation_fee_amount = round(cancellation_fee_amount / 25000, 2),
    refundable_amount = round(refundable_amount / 25000, 2)
where greatest(field_price_amount, service_total_amount, total_amount, paid_amount) >= 1000;

update booking_service set
    unit_price = round(unit_price / 25000, 2),
    line_total = round(line_total / 25000, 2)
where greatest(unit_price, line_total) >= 1000;
update booking_promotion set discount_amount = round(discount_amount / 25000, 2) where discount_amount >= 1000;
update payment set amount = round(amount / 25000, 2), currency = 'USD' where amount >= 1000;
update payment set currency = 'USD' where amount < 1000 and (currency is null or currency <> 'USD');
update invoice set
    field_amount = round(field_amount / 25000, 2),
    service_amount = round(service_amount / 25000, 2),
    discount_amount = round(discount_amount / 25000, 2),
    total_amount = round(total_amount / 25000, 2),
    paid_amount = round(paid_amount / 25000, 2),
    remaining_amount = round(remaining_amount / 25000, 2),
    refund_amount = round(refund_amount / 25000, 2)
where greatest(field_amount, service_amount, total_amount, paid_amount, refund_amount) >= 1000;
update refund set refund_amount = round(refund_amount / 25000, 2) where refund_amount >= 1000;
