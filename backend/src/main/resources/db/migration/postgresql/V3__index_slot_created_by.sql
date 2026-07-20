-- Covers staff calendar and audit joins through Slot.createdBy.
-- Kept as a separate migration because V1/V2 may already be applied.
create index idx_slot_created_by on slot (created_by) where created_by is not null;
