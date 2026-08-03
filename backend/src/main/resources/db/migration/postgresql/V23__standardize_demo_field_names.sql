-- Keep demo field labels short and consistent with common Vietnamese venue
-- naming: the number indicates team size and the letter identifies the field.
-- Only known demo labels are reconciled; user-created field names are untouched.

update field
set field_name = 'Field 5A', updated_at = now()
where field_name in ('Emerald Five', 'Pitch A');

update field
set field_name = 'Field 7A', updated_at = now()
where field_name in ('Riverside Seven', 'Pitch B');

update field
set field_name = 'Field 11A', updated_at = now()
where field_name in ('Championship Arena', 'Main Field 11A');

update field
set field_name = 'Field 5D', updated_at = now()
where field_name in ('Training Court', 'Training Field');

update field
set field_name = 'Field 5C', updated_at = now()
where field_name in ('Covered Field 5B', 'Covered Field 5C');

update field
set field_name = 'Field 11B', updated_at = now()
where field_name = 'Main Field 11B';
