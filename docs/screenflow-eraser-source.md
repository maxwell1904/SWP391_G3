## Guest Screen Flow

```text
textSize small
title Guest Screen Flow - Football Field Booking

LandingPage [shape: rectangle, label: "Landing Page"]
FieldListScreen [shape: rectangle, label: "Field List"]
FieldDetailScreen [shape: rectangle, label: "Field Detail"]
PromotionListScreen [shape: rectangle, label: "Promotion List"]
MembershipBenefitsScreen [shape: rectangle, label: "Membership Benefits"]
AvailabilityAssistantScreen [shape: rectangle, label: "Availability Assistant"]
LoginScreen [shape: rectangle, label: "Login Screen"]
RegisterScreen [shape: rectangle, label: "Register Account"]
VerifyEmailScreen [shape: rectangle, label: "Verify Email"]
ForgotPasswordScreen [shape: rectangle, label: "Forgot Password"]
ResetPasswordScreen [shape: rectangle, label: "Reset Password"]
RegisterSuccessModal [shape: oval, label: "Register Success"]
LoginFailedModal [shape: oval, label: "Login Failed"]
VerifySuccessModal [shape: oval, label: "Verify Email Success"]
ResetSuccessModal [shape: oval, label: "Reset Password Success"]
AssistantSuggestionModal [shape: oval, label: "Suggested Available Slots"]

LandingPage > FieldListScreen: Click "Fields"
FieldListScreen > FieldDetailScreen: Click Specific Field
FieldDetailScreen > LoginScreen: Click "Book Now"
LandingPage > PromotionListScreen: Click "Offers"
PromotionListScreen > FieldDetailScreen: Click Promotion Field
LandingPage > MembershipBenefitsScreen: Click "Membership"
LandingPage > AvailabilityAssistantScreen: Click "Find a Field"
AvailabilityAssistantScreen > AssistantSuggestionModal: Submit Filter Search
AssistantSuggestionModal > FieldListScreen: Browse Suggested Fields
AvailabilityAssistantScreen > LoginScreen: Ask Gemini While Signed Out
LandingPage > LoginScreen: Click "Login"
LoginScreen > RegisterScreen: Click "Register"
RegisterScreen > RegisterSuccessModal: Submit Valid Information
RegisterSuccessModal > LoginScreen: Click "Login Now"
RegisterSuccessModal > VerifyEmailScreen: Click Verification Link From Email
VerifyEmailScreen > VerifySuccessModal: Verification Token Valid
VerifySuccessModal > LandingPage: Continue To Home
LoginScreen > ForgotPasswordScreen: Click "Forgot Password"
ForgotPasswordScreen > ResetPasswordScreen: Submit Valid Reset Link
ResetPasswordScreen > ResetSuccessModal: Click "Save Password"
ResetSuccessModal > LoginScreen: Back To Login
LoginScreen > LoginFailedModal: Invalid Credentials
LoginFailedModal > LoginScreen: Close

title Define
Legend_Page [shape: rectangle, label: "Flat Page"]
Legend_Modal [shape: oval, label: "Modal / Popup"]
```

## Customer Screen Flow

```text
textSize small
title Customer Screen Flow - Football Field Booking

LandingPage [shape: rectangle, label: "Landing Page"]
FieldListScreen [shape: rectangle, label: "Field List"]
FieldDetailScreen [shape: rectangle, label: "Field Detail"]
BookingScreen [shape: rectangle, label: "Booking Page"]
CheckoutSummarySection [shape: rectangle, label: "Checkout Summary"]
PayPalSandboxScreen [shape: rectangle, label: "PayPal Sandbox"]
CustomerWorkspaceScreen [shape: rectangle, label: "Customer Workspace"]
MyBookingsPanel [shape: rectangle, label: "My Bookings"]
BookingDetailPanel [shape: rectangle, label: "Booking Detail / Invoice"]
PaymentHistoryPanel [shape: rectangle, label: "Payment History"]
MembershipPanel [shape: rectangle, label: "Membership Progress"]
ProfilePanel [shape: rectangle, label: "Profile"]
MessagesPanel [shape: rectangle, label: "Notifications & Report Issue"]
BookingAddOnsPanel [shape: rectangle, label: "Booking Add-ons"]
CancellationPreviewModal [shape: rectangle, label: "Cancellation Terms (Inline)"]
RefundRequestModal [shape: rectangle, label: "Refund Request Action"]
RescheduleModal [shape: rectangle, label: "Reschedule / Add-ons"]
IssueSubmittedModal [shape: oval, label: "Issue Submitted"]
BookingCreatedModal [shape: oval, label: "Booking Created"]

LandingPage > FieldListScreen: Click "Fields"
FieldListScreen > FieldDetailScreen: Click Specific Field
FieldDetailScreen > BookingScreen: Click "Book Now"
BookingScreen > CheckoutSummarySection: Select Date, Slot, Services, Promotion
CheckoutSummarySection > PayPalSandboxScreen: Choose Deposit / Full Payment
PayPalSandboxScreen > BookingCreatedModal: Payment Captured Successfully
BookingCreatedModal > CustomerWorkspaceScreen: Open "My Bookings"
LandingPage > CustomerWorkspaceScreen: Login As Customer
CustomerWorkspaceScreen > MyBookingsPanel: Default Tab
MyBookingsPanel > BookingDetailPanel: Select Booking
BookingDetailPanel > CancellationPreviewModal: Click "Preview Cancellation"
CancellationPreviewModal > BookingDetailPanel: Return To Booking Detail
BookingDetailPanel > RefundRequestModal: Click "Request Refund"
RefundRequestModal > BookingDetailPanel: Submit Refund Request
BookingDetailPanel > RescheduleModal: Click "Reschedule"
RescheduleModal > BookingDetailPanel: Save New Slot
BookingDetailPanel > BookingAddOnsPanel: Update Services Before Check-in
CustomerWorkspaceScreen > PaymentHistoryPanel: Open "Payments" Tab
CustomerWorkspaceScreen > MembershipPanel: Open "Membership" Tab
CustomerWorkspaceScreen > ProfilePanel: Open "Profile" Tab
CustomerWorkspaceScreen > MessagesPanel: Open "Messages" Tab
MessagesPanel > IssueSubmittedModal: Submit Field / Service Issue
IssueSubmittedModal > MessagesPanel: Close

title Define
Legend_Page [shape: rectangle, label: "Flat Page"]
Legend_Modal [shape: oval, label: "Modal / Popup"]
```

## Staff Screen Flow

```text
textSize small
title Staff Screen Flow - Football Field Booking

LoginScreen [shape: rectangle, label: "Login Screen"]
StaffWorkspaceScreen [shape: rectangle, label: "Staff Workspace"]
BookingsPanel [shape: rectangle, label: "Bookings"]
BookingCalendarPanel [shape: rectangle, label: "Booking Calendar"]
LifecycleActionsPanel [shape: rectangle, label: "Lifecycle Actions"]
BillingStatusPanel [shape: rectangle, label: "Invoice and Payment Status"]
BookingServicesPanel [shape: rectangle, label: "Edit Booking Services"]
FieldSchedulePanel [shape: rectangle, label: "Field Schedule"]
BlockSlotForm [shape: rectangle, label: "Block / Unblock Slot Form"]
IssuesPanel [shape: rectangle, label: "Issue Report"]
RefundsPanel [shape: rectangle, label: "Refunds"]
WalkInBookingScreen [shape: rectangle, label: "Walk-in Booking Page"]
BookingUpdatedModal [shape: oval, label: "Booking Status Updated"]
SlotBlockedModal [shape: oval, label: "Slot Blocked"]
SlotUnblockedModal [shape: oval, label: "Slot Unblocked"]
IssueSavedModal [shape: oval, label: "Issue Saved"]
RefundProcessedModal [shape: oval, label: "Refund Processed"]

LoginScreen > StaffWorkspaceScreen: Login As Staff
StaffWorkspaceScreen > BookingsPanel: Default Tab
BookingsPanel > BookingCalendarPanel: View Booking Queue
BookingCalendarPanel > LifecycleActionsPanel: Select Booking
LifecycleActionsPanel > BookingUpdatedModal: Confirm / Reject / Check-in / Complete / Cancel / No-show
BookingUpdatedModal > LifecycleActionsPanel: Close
LifecycleActionsPanel > BillingStatusPanel: View Invoice and Payment Status
LifecycleActionsPanel > BookingServicesPanel: Edit Services Before Check-in
StaffWorkspaceScreen > WalkInBookingScreen: Click "Create Walk-in Booking"
WalkInBookingScreen > StaffWorkspaceScreen: Save Walk-in Booking
StaffWorkspaceScreen > FieldSchedulePanel: Open "Field Schedule" Tab
FieldSchedulePanel > BlockSlotForm: Enter Block Information
BlockSlotForm > SlotBlockedModal: Save Blocked Slot
SlotBlockedModal > FieldSchedulePanel: Close
FieldSchedulePanel > SlotUnblockedModal: Click "Unblock"
SlotUnblockedModal > FieldSchedulePanel: Close
StaffWorkspaceScreen > IssuesPanel: Open "Issues & Refunds" Tab
IssuesPanel > IssueSavedModal: Submit / Resolve Issue
IssueSavedModal > IssuesPanel: Close
IssuesPanel > RefundsPanel: Review Refund Requests
RefundsPanel > RefundProcessedModal: Approve / Reject / Process Refund
RefundProcessedModal > RefundsPanel: Close

title Define
Legend_Page [shape: rectangle, label: "Flat Page"]
Legend_Modal [shape: oval, label: "Modal / Popup"]
```

## Admin Screen Flow

```text
textSize small
title Admin Screen Flow - Football Field Booking

LoginScreen [shape: rectangle, label: "Login Screen"]
AdminConsoleScreen [shape: rectangle, label: "Admin Console"]
OverviewPanel [shape: rectangle, label: "Overview"]
FieldsPricingPanel [shape: rectangle, label: "Fields & Pricing"]
FieldEditorPanel [shape: rectangle, label: "Field Editor"]
PriceRulePanel [shape: rectangle, label: "Price Rule Management"]
ServicesPanel [shape: rectangle, label: "Services"]
ServiceEditorPanel [shape: rectangle, label: "Extra Service Editor"]
PeopleAccessPanel [shape: rectangle, label: "People & Access"]
CustomerDetailPanel [shape: rectangle, label: "Customer Detail View"]
CustomerActivityPanel [shape: rectangle, label: "Customer Activity Status"]
StaffAccountPanel [shape: rectangle, label: "Staff Account Management"]
BookingsBillingPanel [shape: rectangle, label: "Bookings & Billing"]
IssueAuditPanel [shape: rectangle, label: "Issue Audit"]
PoliciesPanel [shape: rectangle, label: "Policies"]
ReportsPanel [shape: rectangle, label: "Revenue / Booking Reports"]
FieldSavedModal [shape: oval, label: "Field Saved"]
PriceRuleSavedModal [shape: oval, label: "Price Rule Saved"]
ServiceSavedModal [shape: oval, label: "Service Saved"]
CustomerLockedModal [shape: oval, label: "Customer Locked / Unlocked"]
StaffSavedModal [shape: oval, label: "Staff Account Saved"]
PolicySavedModal [shape: oval, label: "Policy Saved"]

LoginScreen > AdminConsoleScreen: Login As Admin
AdminConsoleScreen > OverviewPanel: Default Tab
AdminConsoleScreen > FieldsPricingPanel: Open "Fields & Pricing"
FieldsPricingPanel > FieldEditorPanel: Select Existing Field Or Create New Field
FieldEditorPanel > FieldSavedModal: Save Field
FieldSavedModal > FieldsPricingPanel: Close
FieldsPricingPanel > PriceRulePanel: Select Price Rule Section
PriceRulePanel > PriceRuleSavedModal: Add / Edit Price Rule
PriceRuleSavedModal > PriceRulePanel: Close
AdminConsoleScreen > ServicesPanel: Open "Services"
ServicesPanel > ServiceEditorPanel: Select Existing Service Or Create New Service
ServiceEditorPanel > ServiceSavedModal: Save Service
ServiceSavedModal > ServicesPanel: Close
AdminConsoleScreen > PeopleAccessPanel: Open "People & Access"
PeopleAccessPanel > CustomerDetailPanel: Click "View" On Customer
CustomerDetailPanel > CustomerActivityPanel: Review Bookings, Completed Count, Issues
PeopleAccessPanel > CustomerLockedModal: Click "Lock" On Customer
CustomerLockedModal > PeopleAccessPanel: Submit Reason And Close
PeopleAccessPanel > PeopleAccessPanel: Click "Unlock" On Customer
PeopleAccessPanel > StaffAccountPanel: Create / Update Staff Account
StaffAccountPanel > StaffSavedModal: Save Staff Account
StaffSavedModal > PeopleAccessPanel: Close
AdminConsoleScreen > BookingsBillingPanel: Open "Bookings & Billing"
BookingsBillingPanel > CustomerActivityPanel: Review Booking Invoice Details
AdminConsoleScreen > IssueAuditPanel: Open "Issue Audit"
AdminConsoleScreen > PoliciesPanel: Open "Policies"
PoliciesPanel > PolicySavedModal: Save Booking / Refund / Automatic Slot Rule
PolicySavedModal > PoliciesPanel: Close
OverviewPanel > ReportsPanel: Review Date-filtered Revenue, Bookings, Membership, Customer Activity

title Define
Legend_Page [shape: rectangle, label: "Flat Page"]
Legend_Modal [shape: oval, label: "Modal / Popup"]
```
