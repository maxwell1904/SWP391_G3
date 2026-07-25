# GoalZone diagram index

Updated: 2026-07-25

The final requirements contain UC-01 through UC-63. Source filenames created
before the final renumbering may retain their historical numeric prefix; the
use-case title inside the final RDS/SDS is authoritative.

## Account diagrams

| Final UC | Use case | Class diagram | Sequence diagram |
| --- | --- | --- | --- |
| UC-01 | Register account | `Class Diagram/register.puml` | `Sequence/Register-and-Verify-Email.puml` |
| UC-02 | Login | `Class Diagram/login.puml` | `Sequence/Login.puml` |
| UC-03 | Logout | `Class Diagram/logout.puml` | `Sequence/UC-03-logout.puml` |
| UC-04 | Manage personal profile | `Class Diagram/manage-personal-profile.puml` | `Sequence/UC-04-manage-personal-profile.puml` |
| UC-05 | Change password | `Class Diagram/change-password.puml` | `Sequence/UC-05-change-password.puml` |
| UC-06 | Forgot/reset password | `Class Diagram/forgot-reset-password.puml` | `Sequence/UC-06-forgot-reset-password.puml` |
| UC-07 | Manage Customer accounts | `Class Diagram/manage-customer-accounts.puml` | `Sequence/UC-07-manage-customer-accounts.puml` |
| UC-07 supplemental | Lock/Unlock design | `Class Diagram/lock-unlock-customer-account.puml` | `Sequence/Lock-Unlock-Customer-Account.puml` |
| UC-08 | Manage Staff accounts | `Class Diagram/manage-staff-accounts.puml` | `Sequence/UC-08-manage-staff-accounts.puml` |
| UC-09 | View Customer activity status | `Class Diagram/view-customer-activity-status.puml` | `Sequence/UC-09-view-customer-activity-status.puml` |

## Remaining ranges

| Final range | Area | Source folders |
| --- | --- | --- |
| UC-10 - UC-22 | Field, Slot, Service, and Issue Management | `Class Diagram/`, `Sequence/` |
| UC-23 - UC-36 | Booking Lifecycle | `Class Diagram/`, `Sequence/` |
| UC-37 - UC-49 | Payment Gateway, Refund, and Invoice | `Class Diagram/`, `Sequence/` |
| UC-50 - UC-61 | Promotion, Membership, Notification, and Report | `Class Diagram/`, `Sequence/` |
| UC-62 - UC-63 | Availability assistant and suggestions | `Class Diagram/`, `Sequence/` |

There are 64 class and 64 sequence source diagrams because UC-07 intentionally
has one supplemental Lock/Unlock design pair. The obsolete booking-restriction
diagram is not part of the repository.
