from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import re

from PIL import Image
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt
from docx.table import Table
from docx.text.paragraph import Paragraph


ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "SWP391_Project"
IMPORTS = ROOT / "docs_new_imports"
OUTPUT = PROJECT / "output" / "doc"
CLASS_RENDER = ROOT / "tmp" / "class_render_v17"
SEQUENCE_RENDER = ROOT / "tmp" / "sequence_render_v17"
SCREEN_FLOW = PROJECT / "docs" / "Screen Flow"
PACKAGE_IMAGE = PROJECT / "docs" / "generated" / "package" / "code-packages.png"

RDS_INPUT = IMPORTS / "RDS Document_G03_Final.docx"
SDS_INPUT = IMPORTS / "SDS Document_G03_Final.docx"
RDS_OUTPUT = OUTPUT / "RDS Document_G03_Final_Code_First_Aligned.docx"
SDS_OUTPUT = OUTPUT / "SDS Document_G03_Final_Code_First_Aligned.docx"


def iter_blocks(document: Document):
    for child in document.element.body.iterchildren():
        if child.tag.endswith("}p"):
            yield Paragraph(child, document)
        elif child.tag.endswith("}tbl"):
            yield Table(child, document)


def iter_all_paragraphs(document: Document):
    yield from document.paragraphs
    for table in document.tables:
        for row in table.rows:
            for cell in row.cells:
                yield from cell.paragraphs
                for nested in cell.tables:
                    for nested_row in nested.rows:
                        for nested_cell in nested_row.cells:
                            yield from nested_cell.paragraphs


def replace_across_runs(paragraph: Paragraph, old: str, new: str) -> int:
    count = 0
    while old in paragraph.text:
        full = "".join(run.text for run in paragraph.runs)
        start = full.find(old)
        if start < 0:
            break
        end = start + len(old)
        positions = []
        cursor = 0
        for index, run in enumerate(paragraph.runs):
            positions.append((index, cursor, cursor + len(run.text)))
            cursor += len(run.text)
        start_info = next((x for x in positions if x[1] <= start < x[2]), None)
        end_info = next((x for x in positions if x[1] < end <= x[2]), None)
        if start_info is None or end_info is None:
            break
        start_run, start_lo, _ = start_info
        end_run, end_lo, _ = end_info
        start_offset = start - start_lo
        end_offset = end - end_lo
        if start_run == end_run:
            text = paragraph.runs[start_run].text
            paragraph.runs[start_run].text = text[:start_offset] + new + text[end_offset:]
        else:
            first_text = paragraph.runs[start_run].text
            last_text = paragraph.runs[end_run].text
            paragraph.runs[start_run].text = first_text[:start_offset] + new
            for index in range(start_run + 1, end_run):
                paragraph.runs[index].text = ""
            paragraph.runs[end_run].text = last_text[end_offset:]
        count += 1
    return count


def replace_text(document: Document, replacements: list[tuple[str, str]]) -> None:
    for paragraph in iter_all_paragraphs(document):
        for old, new in replacements:
            replace_across_runs(paragraph, old, new)


def update_cover_metadata(document: Document) -> None:
    """Normalize final-submission metadata while preserving template styling."""
    replace_text(
        document,
        [
            ("Can Tho, May 2026", "Can Tho, July 2026"),
            ("Record of changeS", "Record of Changes"),
        ],
    )
    for paragraph in document.paragraphs:
        if paragraph.text.strip() == "af":
            for run in paragraph.runs:
                run.text = ""


def replace_xml_attribute_text(document: Document, replacements: list[tuple[str, str]]) -> None:
    """Keep non-visible image descriptions and other OOXML metadata aligned."""
    for element in document.element.iter():
        for attribute, value in list(element.attrib.items()):
            updated = value
            for old, new in replacements:
                updated = updated.replace(old, new)
            if updated != value:
                element.set(attribute, updated)


def has_drawing(paragraph: Paragraph) -> bool:
    return bool(paragraph._p.xpath(".//w:drawing | .//w:pict"))


def image_fit(path: Path, max_width: float = 6.35, max_height: float = 7.0):
    with Image.open(path) as image:
        width, height = image.size
    scale = min(max_width / width, max_height / height)
    return Inches(width * scale), Inches(height * scale)


def replace_image(paragraph: Paragraph, path: Path, max_width: float = 6.35, max_height: float = 7.0) -> None:
    for run in list(paragraph.runs):
        paragraph._p.remove(run._r)
    width, height = image_fit(path, max_width, max_height)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    # Some imported template picture paragraphs use compressed spacing. Reset it
    # so a tall replacement diagram cannot overlap the preceding subsection label.
    paragraph.paragraph_format.space_before = Pt(8)
    paragraph.paragraph_format.space_after = Pt(8)
    paragraph.add_run().add_picture(str(path), width=width, height=height)


def replace_drawing_after(
    document: Document,
    section_heading: str,
    marker: str,
    image_path: Path,
    max_height: float = 7.0,
) -> None:
    blocks = list(iter_blocks(document))
    in_section = False
    for index, block in enumerate(blocks):
        if isinstance(block, Paragraph) and block.style.name.startswith("Heading 2"):
            in_section = block.text.strip() == section_heading
        if not in_section or not isinstance(block, Paragraph) or block.text.strip() != marker:
            continue
        for candidate in blocks[index + 1 : index + 5]:
            if isinstance(candidate, Paragraph) and has_drawing(candidate):
                replace_image(candidate, image_path, max_height=max_height)
                return
        raise RuntimeError(f"Image not found after {section_heading!r} / {marker!r}")
    raise RuntimeError(f"Section not found: {section_heading!r}")


def set_update_fields(document: Document) -> None:
    settings = document.settings.element
    existing = settings.find(qn("w:updateFields"))
    if existing is None:
        existing = OxmlElement("w:updateFields")
        settings.append(existing)
    existing.set(qn("w:val"), "true")


def set_table_rows(table: Table, rows: list[list[str]]) -> None:
    while len(table.rows) < len(rows):
        table.add_row()
    while len(table.rows) > len(rows):
        table._tbl.remove(table.rows[-1]._tr)
    for row_index, values in enumerate(rows):
        tr_properties = table.rows[row_index]._tr.get_or_add_trPr()
        if tr_properties.find(qn("w:cantSplit")) is None:
            tr_properties.append(OxmlElement("w:cantSplit"))
        for column_index, value in enumerate(values):
            table.cell(row_index, column_index).text = value


def update_package_tables(document: Document) -> None:
    descriptions = {
        "com.swp391.backend.controller": (
            "9 Java file(s): AccountController, ApiExceptionHandler, "
            "AvailabilityAssistantController, BookingController, FieldOperationController, "
            "ImageUploadController, PaymentController, PromotionReportController, and SystemController."
        ),
        "com.swp391.backend.service": (
            "16 Java file(s): AccountService, ApiException, AvailabilityAssistantService, "
            "BookingMaintenanceScheduler, BookingWorkflowService, DomainSupportService, "
            "FieldOperationService, ImageStorageService, PayPalCheckoutService, "
            "PaymentWorkflowService, PromotionReportService, SlotGenerationScheduler, "
            "SlotGenerationService, SystemQueryService, "
            "VerificationEmailDelivery, and VerificationEmailService."
        ),
    }
    for table in document.tables:
        if len(table.columns) != 3 or not table.rows:
            continue
        header = [cell.text.strip() for cell in table.rows[0].cells]
        if header[:3] != ["No", "Package", "Description"]:
            continue
        for row in table.rows[1:]:
            package = row.cells[1].text.strip()
            if package in descriptions:
                row.cells[2].text = descriptions[package]


def update_database_table_descriptions(document: Document) -> None:
    for table in document.tables:
        if len(table.columns) != 3 or not table.rows:
            continue
        header = [cell.text.strip() for cell in table.rows[0].cells]
        if header[:3] != ["No", "Table", "Description"]:
            continue
        for row in table.rows[1:]:
            name = row.cells[1].text.strip()
            if name == "app_user":
                row.cells[2].text = (
                    "Primary key: user_id. Stores Customer, Staff, and Admin identities. "
                    "status is the single persisted source of truth for active/inactive/locked access; "
                    "lock_reason explains an administrative Customer lock and auth_version revokes JWTs."
                )
            elif name == "field_type":
                row.cells[2].text = (
                    "Primary key: field_type_id. Normalizes reusable field-type name and player capacity "
                    "so multiple fields do not duplicate the same catalogue metadata."
                )
            elif name == "field_price":
                row.cells[2].text = (
                    "Primary key: field_price_id. Keeps effective weekday/weekend and time-band prices "
                    "separate from field identity so pricing can change over time without rewriting a field."
                )
            elif name == "system_setting":
                row.cells[2].text = (
                    "Primary key: setting_id. Stores administrator-editable deposit, refund, payment-timeout, "
                    "reminder, and automatic slot-generation policies as key/value configuration; "
                    "it is not redundant field data."
                )


def update_slot_use_case_descriptions(document: Document) -> None:
    catalogue = {
        "UC-12": (
            "Generate missing bookable slots from active Admin rules, then search the requested date and optional "
            "field type, calculate current field price, and label each row as available, booked, or blocked."
        ),
        "UC-15": (
            "Admin configures opening time, closing time, slot duration, and rolling horizon. Staff preserves "
            "maintenance, private-event, VIP/internal-use, and other exceptions through Block/Unblock."
        ),
        "UC-16": (
            "Ensure the selected date is materialized from Admin slot rules, then display every field slot with "
            "available, blocked, or booking-derived operational status."
        ),
    }
    detailed = {
        "UC-12": {
            "Primary Actor:": "Guest or Customer",
            "Description:": catalogue["UC-12"],
            "Preconditions:": (
                "PRE-1: At least one active field exists and the four slot-generation rules are active.\n"
                "PRE-2: No authentication is required for ordinary slot search."
            ),
            "Postconditions:": (
                "POST-1: Missing non-overlapping slots for the requested date are materialized idempotently.\n"
                "POST-2: Matching rows are displayed with price and availability; an available row may continue to checkout."
            ),
            "Normal Flow:": (
                "A. Search available fields by date/time\n"
                "1. The visitor chooses a date and optional field type/field.\n"
                "2. The frontend requests GET /api/slots/search.\n"
                "3. The backend reads opening time, closing time, duration, and generation horizon.\n"
                "4. Missing slots are generated for every active field without overlapping blocked/booked rows.\n"
                "5. The service derives booking state and price, filters by field type, and returns the daily list.\n"
                "6. The UI enables only rows with available=true."
            ),
            "Alternative Flow:": (
                "A1: No date supplied\n1. The service defaults to tomorrow and generates it on demand.\n"
                "A2: Existing exception\n1. A booked or blocked row is preserved and returned as unavailable.\n"
                "A3: Daily scheduler\n1. The midnight job extends the rolling calendar before any customer search."
            ),
            "Exceptions:": (
                "EXC-1: Past dates are rejected.\n"
                "EXC-2: Dates beyond the configured generation horizon are rejected.\n"
                "EXC-3: Invalid or non-divisible schedule rules are rejected when Admin saves them.\n"
                "EXC-4: A database or network error prevents results from refreshing."
            ),
            "Other Information:": (
                "Implementation rule: generation is idempotent and synchronized in the application instance; "
                "the database unique/exclusion constraints remain the final overlap guard."
            ),
            "Assumptions:": (
                "The venue uses one global operating schedule for all active fields; field-specific hours may be "
                "introduced later without changing the booking lifecycle."
            ),
        },
        "UC-15": {
            "Primary Actor:": "Admin and Staff",
            "Secondary Actors:": "GoalZone slot-generation scheduler and booking database",
            "Trigger:": (
                "Admin changes automatic availability rules, or Staff blocks/unblocks an exceptional field period."
            ),
            "Description:": catalogue["UC-15"],
            "Preconditions:": (
                "PRE-1: Admin is authenticated to edit slot-generation rules, or Staff is authenticated to manage exceptions.\n"
                "PRE-2: The target field exists; Block requires date, start, end, and reason."
            ),
            "Postconditions:": (
                "POST-1: A valid rule change rebuilds unbooked auto-generated availability while preserving bookings and blocks.\n"
                "POST-2: Block affects every overlapping slot without an active booking; Unblock restores one blocked row.\n"
                "POST-3: Customer search immediately reflects the resulting calendar."
            ),
            "Normal Flow:": (
                "A. Configure automatic availability\n"
                "1. Admin opens Policies and edits opening time, closing time, duration, or horizon.\n"
                "2. The backend validates the combined rule set.\n"
                "3. Unbooked auto-generated rows in the rolling window are rebuilt; booked and blocked rows are preserved.\n"
                "B. Block an exception\n"
                "1. Staff opens Field schedule and chooses a field, date, time range, and reason.\n"
                "2. The backend rejects the request if any overlapping slot has an active booking.\n"
                "3. Every overlapping slot is marked blocked; if none exists, a future exception row is created.\n"
                "4. Calendar and public availability refresh."
            ),
            "Alternative Flow:": (
                "A1: Unblock exception\n1. Staff selects Unblock and the backend clears reason/note and restores availability.\n"
                "A2: Future private event\n1. Staff may create a blocked exception before that date enters the rolling horizon; "
                "later generation skips its range."
            ),
            "Exceptions:": (
                "EXC-1: Rule time must use HH:mm; opening must precede closing.\n"
                "EXC-2: Duration must be 30-360 minutes in 30-minute steps and divide the operating window exactly.\n"
                "EXC-3: Horizon must be 1-90 days.\n"
                "EXC-4: Past blocks, missing reasons, invalid ranges, and ranges containing active bookings are rejected."
            ),
            "Other Information:": (
                "Block/Unblock is intentionally retained for maintenance, weather, special events, VIP/internal use, "
                "and other operational exceptions. Manual Add Slot is not part of the normal workflow."
            ),
            "Assumptions:": (
                "Only unbooked rows originally generated by the system are replaceable when rules change."
            ),
        },
        "UC-16": {
            "Description:": catalogue["UC-16"],
            "Preconditions:": (
                "PRE-1: The caller has Staff or Admin backend authority; Staff owns the current React calendar.\n"
                "PRE-2: Slot-generation rules are valid for the requested date."
            ),
            "Postconditions:": (
                "POST-1: The requested date exists in the rolling calendar and the operator sees available, blocked, and booked states.\n"
                "POST-2: Viewing does not change bookings or block exceptions."
            ),
            "Normal Flow:": (
                "A. View field operation calendar\n"
                "1. Staff opens Field schedule or changes its date.\n"
                "2. The frontend requests GET /api/operations/calendar?date=... .\n"
                "3. The backend materializes any missing slots from Admin rules.\n"
                "4. It loads slots and bookings, sorts by field/time, and derives operationalStatus.\n"
                "5. The UI displays field, time, booking/customer or block reason, and status."
            ),
            "Alternative Flow:": (
                "A1: Date omitted\n1. The backend defaults to today and generates remaining non-elapsed slots.\n"
                "A2: No active fields\n1. The calendar returns an empty list."
            ),
            "Exceptions:": (
                "EXC-1: Unauthenticated and Customer callers are forbidden.\n"
                "EXC-2: Past or beyond-horizon operation dates are rejected by the generation rules."
            ),
            "Other Information:": (
                "Implementation rule: booking status takes precedence over slot status in the operational display; "
                "Block/Unblock remains adjacent to this calendar."
            ),
            "Assumptions:": "The calendar is a daily operations view rather than a month-grid planning engine.",
        },
    }
    for table in document.tables:
        if not table.rows:
            continue
        header = [cell.text.strip() for cell in table.rows[0].cells]
        if header[:4] == ["ID", "Feature", "Use Case", "Use Case Description"]:
            for row in table.rows[1:]:
                uc_id = row.cells[0].text.strip()
                if uc_id in catalogue:
                    row.cells[3].text = catalogue[uc_id]
            continue
        first_row = " | ".join(cell.text.strip() for cell in table.rows[0].cells)
        uc_id = next((candidate for candidate in detailed if candidate in first_row), None)
        if uc_id is None:
            continue
        values = detailed[uc_id]
        for row in table.rows[1:]:
            label = row.cells[0].text.strip()
            if label not in values:
                continue
            for cell in row.cells[1:]:
                cell.text = values[label]


def update_rds() -> None:
    document = Document(RDS_INPUT)
    update_cover_metadata(document)
    replacements = [
        (
            "A lock stores status `locked`, `account_locked = true`, and the required reason.",
            "A lock stores status `locked` as the single persisted access state together with the required reason.",
        ),
        ("status/account_locked/lock_reason/auth_version", "status/lock_reason/auth_version"),
        ("`accountLocked` is false", "`status` is `active`"),
        (
            "Rule-based discovery page that ranks live available slots and continues a selected result into real booking.",
            "Availability page where a signed-in Customer may ask Gemini and visitors may filter deterministic live ranked slots; a selected result continues into real booking.",
        ),
        (
            "UC-62 and UC-63 are implemented as a single rule-based discovery-to-booking journey while retaining separate traceability identifiers.",
            "UC-62 is the optional configured Gemini question flow; UC-63 is the independent deterministic live-ranking and booking-handoff flow.",
        ),
        (
            "UC-62/63 use an explainable rule-based assistant over live data; external LLM/RAG integration remains optional and is not required for classroom delivery.",
            "UC-62 requires a configured Gemini API key for its external smoke test; UC-63 remains fully testable and authoritative without external AI.",
        ),
        ("Ask smart assistant for available fields", "Ask availability assistant"),
        ("Flyway V11", "Flyway V19"),
        ("Flyway V15", "Flyway V19"),
        ("Flyway V16", "Flyway V19"),
        ("Flyway V17", "Flyway V19"),
        ("Flyway V18", "Flyway V19"),
    ]
    replace_text(document, replacements)
    replace_xml_attribute_text(
        document,
        [
            ("Flyway V1 through V11", "Flyway V1 through V19"),
            ("Flyway V1 through V15", "Flyway V1 through V19"),
            ("Flyway V1 through V16", "Flyway V1 through V19"),
            ("Flyway V1 through V17", "Flyway V1 through V19"),
            ("Flyway V1 through V18", "Flyway V1 through V19"),
            ("UC-64", "UC-63"),
        ],
    )
    update_slot_use_case_descriptions(document)

    # Update final use-case catalogue rows that changed after the imported draft.
    for table in document.tables:
        if not table.rows:
            continue
        header = [cell.text.strip() for cell in table.rows[0].cells]
        if header[:4] == ["ID", "Feature", "Use Case", "Use Case Description"]:
            for row in table.rows[1:]:
                uc_id = row.cells[0].text.strip()
                if uc_id == "UC-08":
                    row.cells[3].text = (
                        "An Admin creates and maintains Staff identity/status. The Staff member receives "
                        "a one-hour email invitation and privately chooses the password; Admin cannot set or replace it."
                    )
                elif uc_id == "UC-62":
                    row.cells[1].text = "Ask availability assistant"
                    row.cells[2].text = "Ask availability assistant"
                    row.cells[3].text = (
                        "A signed-in Customer asks Gemini to interpret a natural-language field request. "
                        "All fields, times, prices, promotions, and ranked results remain grounded in live GoalZone data."
                    )
                elif uc_id == "UC-63":
                    row.cells[3].text = (
                        "Deterministically ranks live available slots with explanations and transfers the selected "
                        "slot/date into normal checkout, where availability is revalidated."
                    )

    # Replace the single stale Guest-only diagram with four code-aligned flows.
    body = document.element.body
    blocks = list(body.iterchildren())
    start = next(
        i for i, element in enumerate(blocks)
        if element.tag.endswith("}p")
        and Paragraph(element, document).text.strip() == "2.1 Screens Flow"
    )
    end = next(
        i for i, element in enumerate(blocks)
        if element.tag.endswith("}p")
        and Paragraph(element, document).text.strip() == "2.2 Screen Descriptions"
    )
    for element in blocks[start + 1 : end]:
        body.remove(element)
    target = next(p for p in document.paragraphs if p.text.strip() == "2.2 Screen Descriptions")
    flow_specs = [
        ("Guest’s Screen Flow", SCREEN_FLOW / "guest-screen-flow.png", 5.1, 7.25),
        ("Customer’s Screen Flow", SCREEN_FLOW / "customer-screen-flow.png", 5.7, 7.15),
        ("Staff’s Screen Flow", SCREEN_FLOW / "staff-screen-flow.png", 6.35, 6.8),
        ("Admin’s Screen Flow", SCREEN_FLOW / "admin-screen-flow.png", 6.35, 6.8),
    ]
    for index, (title, image, max_width, max_height) in enumerate(flow_specs):
        heading = document.add_paragraph(title, style="Heading 3")
        heading.paragraph_format.keep_with_next = True
        if index:
            heading.paragraph_format.page_break_before = True
        target._p.addprevious(heading._p)
        picture = document.add_paragraph()
        picture.alignment = WD_ALIGN_PARAGRAPH.CENTER
        width, height = image_fit(image, max_width, max_height)
        picture.add_run().add_picture(str(image), width=width, height=height)
        target._p.addprevious(picture._p)
    target.paragraph_format.page_break_before = True

    # Screen description and authorization are corrected against actual routes/roles.
    for table in document.tables:
        if not table.rows:
            continue
        header = [cell.text.strip() for cell in table.rows[0].cells]
        if header[:4] == ["#", "Feature", "Screen", "Description"]:
            for row in table.rows[1:]:
                if row.cells[2].text.strip() == "Availability Assistant":
                    row.cells[3].text = (
                        "Guest/Customer discovery page with deterministic live slot filters. A signed-in Customer "
                        "may additionally ask grounded Gemini; selected results continue into ordinary checkout."
                    )
                elif row.cells[2].text.strip() == "Booking and Refund Policies":
                    row.cells[3].text = (
                        "Admin policy workspace for deposit, cancellation/refund, payment timeout, reminder, and "
                        "automatic slot opening/closing/duration/horizon rules."
                    )
        if header[:5] == ["Screen", "Guest", "Customer", "Staff", "Admin"]:
            access = {
                "Landing Page": {"Guest", "Customer"},
                "Login Screen": {"Guest", "Customer", "Staff", "Admin"},
                "Register Account": {"Guest"},
                "Forgot/Reset Password": {"Guest"},
                "Profile / Edit Profile": {"Customer", "Staff", "Admin"},
                "Customer Management": {"Admin"},
                "Staff Management": {"Admin"},
                "Field List": {"Guest", "Customer", "Staff", "Admin"},
                "Field Detail": {"Guest", "Customer", "Staff", "Admin"},
                "Search Available Fields": {"Guest", "Customer", "Staff"},
                "Field Management": {"Admin"},
                "Field Pricing Management": {"Admin"},
                "Extra Service Selection": {"Guest", "Customer", "Staff"},
                "Extra Service Management": {"Admin"},
                "Unavailable Slot Management": {"Staff"},
                "Issue Report / Issue Management": {"Customer", "Staff", "Admin"},
                "Online Booking": {"Customer"},
                "Walk-in Booking": {"Staff"},
                "Booking Detail": {"Customer", "Staff", "Admin"},
                "My Bookings": {"Customer"},
                "Booking Calendar": {"Staff"},
                "Reschedule Booking": {"Customer", "Staff"},
                "Cancellation Preview": {"Customer", "Staff"},
                "Checkout Summary": {"Guest", "Customer", "Staff"},
                "Payment Sandbox": {"Customer"},
                "Payment History": {"Customer", "Staff", "Admin"},
                "Invoice Detail": {"Customer", "Staff", "Admin"},
                "Refund Management": {"Customer", "Staff", "Admin"},
                "Booking and Refund Policies": {"Admin"},
                "Promotion List": {"Guest", "Customer", "Staff", "Admin"},
                "Promotion Management": {"Admin"},
                "Membership Benefits": {"Guest", "Customer", "Staff", "Admin"},
                "Membership Progress": {"Customer"},
                "Membership Rule Management": {"Admin"},
                "Notification List": {"Customer", "Staff", "Admin"},
                "Revenue Report": {"Admin"},
                "Booking Report": {"Admin"},
                "Customer Account state Report": {"Admin"},
                "Availability Assistant": {"Guest", "Customer"},
            }
            for row in table.rows[1:]:
                allowed = access.get(row.cells[0].text.strip(), set())
                for column, role in enumerate(["Guest", "Customer", "Staff", "Admin"], start=1):
                    row.cells[column].text = "X" if role in allowed else ""

    update_package_tables(document)
    update_database_table_descriptions(document)
    set_update_fields(document)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    document.save(RDS_OUTPUT)


def update_sds_assistant_specs(document: Document) -> None:
    blocks = list(iter_blocks(document))
    active = False
    pending_table_for = None
    for block in blocks:
        if isinstance(block, Paragraph) and block.style.name.startswith("Heading 2"):
            active = block.text.strip().startswith("62. ")
            if active:
                block.text = "62. Ask availability assistant"
        if not active:
            continue
        if isinstance(block, Paragraph):
            name = block.text.strip()
            renames = {
                "FieldOperationController": "AvailabilityAssistantController",
                "FieldOperationService": "AvailabilityAssistantService",
                "SlotRepository": "FieldOperationService",
                "BookingRepository": "AvailabilityAssistantPage",
            }
            if name in renames:
                block.text = renames[name]
                pending_table_for = renames[name]
        elif isinstance(block, Table) and pending_table_for:
            tables = {
                "AvailabilityAssistantController": [
                    ["No", "Method", "Description"],
                    [
                        "01",
                        "askForAvailability(request : ApiRequests.AssistantAvailability) : Object",
                        "Exposes POST /api/assistant/availability and delegates the authenticated Customer question.",
                    ],
                ],
                "AvailabilityAssistantService": [
                    ["No", "Method", "Description"],
                    [
                        "01",
                        "answerAvailabilityQuestion(request : AssistantAvailability) : Map<String, Object>",
                        "Validates the question/key, obtains verified ranked slots, and returns answer, criteria, and suggestions.",
                    ],
                    [
                        "02",
                        "authenticatedCustomerId() : Long",
                        "Requires the current authenticated role to be Customer.",
                    ],
                    [
                        "03",
                        "parseIntent(question : String, fieldTypes : List<Map>) : SearchIntent",
                        "Uses constrained Gemini JSON to extract date, preferred time, known field type, and maximum price.",
                    ],
                    [
                        "04",
                        "callGemini(prompt : String, jsonResponse : boolean) : String",
                        "Calls the configured Gemini model and converts provider/configuration failures into safe API errors.",
                    ],
                    [
                        "05",
                        "writeAnswer(question : String, criteria : Map, suggestions : List) : String",
                        "Asks Gemini to phrase only the verified GoalZone availability in at most 75 words.",
                    ],
                ],
                "FieldOperationService": [
                    ["No", "Method", "Description"],
                    [
                        "01",
                        "fieldTypes() : List<Map<String, Object>>",
                        "Returns the allowed field-type identifiers supplied to Gemini as a closed set.",
                    ],
                    [
                        "02",
                        "suggestSlots(date, preferredTime, fieldTypeId, maxPrice, customerId) : List<Map>",
                        "Authoritatively filters and ranks live availability; Gemini cannot create or alter a result.",
                    ],
                ],
                "AvailabilityAssistantPage": [
                    ["No", "Method", "Description"],
                    [
                        "01",
                        "askAssistant() : Promise<void>",
                        "Submits a Customer question and renders the grounded answer, criteria, and ranked suggestions.",
                    ],
                    [
                        "02",
                        "findSlots() : Promise<void>",
                        "Runs the deterministic UC-63 filter path without requiring Gemini.",
                    ],
                ],
            }
            set_table_rows(block, tables[pending_table_for])
            pending_table_for = None


def insert_spec_after(
    document: Document,
    anchor: Table,
    name: str,
    rows: list[list[str]],
) -> Table:
    heading_xml = deepcopy(anchor._tbl.getprevious())
    heading = Paragraph(heading_xml, document)
    heading.clear()
    heading.add_run(name).bold = True
    heading.paragraph_format.space_before = Inches(0.16)
    heading.paragraph_format.space_after = Inches(0.05)
    heading.paragraph_format.keep_with_next = True
    clone = deepcopy(anchor._tbl)
    anchor._tbl.addnext(heading_xml)
    heading._p.addnext(clone)
    table = Table(clone, document)
    set_table_rows(table, rows)
    return table


def update_sds_slot_generation_specs(document: Document) -> None:
    sections = {
        "12. Search available fields by date/time": {
            "FieldOperationService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "searchSlots(date : LocalDate, fieldTypeId : Long) : List<Map<String, Object>>",
                    "Ensures the requested day is materialized, then returns active-field slots with booking state, availability, and calculated USD price.",
                ],
            ],
            "SlotRepository": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "findBySlotDate(date : LocalDate) : List<Slot>",
                    "Loads the materialized slots for the requested date.",
                ],
                [
                    "02",
                    "findByField_FieldIdAndSlotDate(fieldId : Long, date : LocalDate) : List<Slot>",
                    "Supports overlap-safe materialization for one field and date.",
                ],
            ],
        },
        "15. Manage unavailable slots": {
            "FieldOperationService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "blockSlot(request : ApiRequests.SlotBlock) : Map<String, Object>",
                    "Blocks every overlapping generated slot, rejects active-booking conflicts, or records a future manual exception outside the current horizon.",
                ],
                [
                    "02",
                    "unblockSlot(slotId : Long) : Map<String, Object>",
                    "Returns a blocked exception to available without recreating the operating calendar manually.",
                ],
            ],
            "SlotRepository": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "findByField_FieldIdAndSlotDate(fieldId : Long, date : LocalDate) : List<Slot>",
                    "Loads every same-day row so Block can cover all overlapping generated periods.",
                ],
                [
                    "02",
                    "findBySlotDateBetweenOrderBySlotDateAscStartTimeAsc(from : LocalDate, to : LocalDate) : List<Slot>",
                    "Loads the rolling window for a safe Admin-rule rebuild.",
                ],
            ],
        },
        "16. View field operation calendar": {
            "FieldOperationService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "operationCalendar(date : LocalDate) : List<Map<String, Object>>",
                    "Materializes the requested date, then merges slots and bookings into available, blocked, booked, and operational states.",
                ],
            ],
        },
    }
    current_section = None
    pending_name = None
    tables_by_section: dict[str, dict[str, Table]] = {name: {} for name in sections}
    for block in list(iter_blocks(document)):
        if isinstance(block, Paragraph) and block.style.name.startswith("Heading 2"):
            current_section = block.text.strip() if block.text.strip() in sections else None
            pending_name = None
            continue
        if current_section is None:
            continue
        if isinstance(block, Paragraph):
            name = block.text.strip()
            pending_name = name if name in sections[current_section] else None
        elif isinstance(block, Table) and pending_name:
            set_table_rows(block, sections[current_section][pending_name])
            tables_by_section[current_section][pending_name] = block
            pending_name = None

    slot_generation_rows = [
        ["No", "Method", "Description"],
        [
            "01",
            "ensureDate(date : LocalDate) : int",
            "Materializes missing non-elapsed slots for every active field from the persisted opening, closing, and duration rules.",
        ],
        [
            "02",
            "rebuildRollingWindow() : int",
            "Rebuilds only unbooked available auto-generated rows and preserves bookings, Block exceptions, and manual audit rows.",
        ],
        [
            "03",
            "validateRuleChange(key : String, value : String) : void",
            "Validates opening/closing order, 30-minute duration steps, divisible operating windows, and the 1–90 day horizon.",
        ],
    ]
    search_anchor = tables_by_section["12. Search available fields by date/time"]["FieldOperationService"]
    insert_spec_after(document, search_anchor, "SlotGenerationService", slot_generation_rows)

    manage_anchor = tables_by_section["15. Manage unavailable slots"]["FieldOperationService"]
    policy_anchor = insert_spec_after(
        document,
        manage_anchor,
        "PromotionReportService",
        [
            ["No", "Method", "Description"],
            [
                "01",
                "updateSetting(key : String, value : String) : Map<String, Object>",
                "Lets Admin persist one automatic-slot rule after validating the combined rule set, then safely rebuilds the rolling window.",
            ],
        ],
    )
    insert_spec_after(document, policy_anchor, "SlotGenerationService", slot_generation_rows)

    calendar_anchor = tables_by_section["16. View field operation calendar"]["FieldOperationService"]
    insert_spec_after(document, calendar_anchor, "SlotGenerationService", slot_generation_rows[:2])


def update_sds_membership_rule_specs(document: Document) -> None:
    active = False
    pending_name = None
    membership_repository_table = None
    for block in list(iter_blocks(document)):
        if isinstance(block, Paragraph) and block.style.name.startswith("Heading 2"):
            active = block.text.strip() == "54. Manage membership rules"
            pending_name = None
            continue
        if not active:
            continue
        if isinstance(block, Paragraph):
            name = block.text.strip()
            pending_name = name if name in {
                "PromotionReportService",
                "MembershipLevelRepository",
            } else None
        elif isinstance(block, Table) and pending_name:
            if pending_name == "PromotionReportService":
                set_table_rows(
                    block,
                    [
                        ["No", "Method", "Description"],
                        [
                            "01",
                            "createMembershipLevel(request : ApiRequests.MembershipLevelUpsert) : Map<String, Object>",
                            "Validates and persists a new level, then recalculates every customer's existing membership assignment.",
                        ],
                        [
                            "02",
                            "updateMembershipLevel(id : Long, request : ApiRequests.MembershipLevelUpsert) : Map<String, Object>",
                            "Updates an existing rule and recalculates assignments without creating duplicate customer-membership rows.",
                        ],
                    ],
                )
            else:
                membership_repository_table = block
            pending_name = None

    if membership_repository_table is not None:
        insert_spec_after(
            document,
            membership_repository_table,
            "DomainSupportService",
            [
                ["No", "Method", "Description"],
                [
                    "01",
                    "refreshAllMembershipAssignments() : void",
                    "Re-evaluates all Customer accounts after an Admin changes a membership rule.",
                ],
                [
                    "02",
                    "updateMembershipProgress(customerId : Long, increment : int) : CustomerMembership",
                    "Updates the customer's single membership row in place and falls back to the first active tier when no threshold currently qualifies.",
                ],
                [
                    "03",
                    "attachDefaultMembership(customer : AppUser) : CustomerMembership",
                    "Returns the existing customer membership when present; otherwise inserts exactly one default assignment.",
                ],
            ],
        )


def update_sds_financial_specs(document: Document) -> None:
    sections = {
        "37. View checkout summary": {
            "BookingWorkflowService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "previewCheckout(request : ApiRequests.PromotionApply) : Map<String, Object>",
                    "Calculates the USD subtotal, caps one promotion at that subtotal, applies membership only when stacking is allowed, and derives deposit from the non-negative final total.",
                ],
            ],
            "DomainSupportService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "calculatePromotionDiscount(code, slot, baseAmount, services, customer) : BigDecimal",
                    "Validates eligibility and caps the promotion between zero and the eligible field-plus-service subtotal.",
                ],
                [
                    "02",
                    "calculateMembershipDiscount(customer, baseAmount, source) : BigDecimal",
                    "Applies the eligible online membership rate to a non-negative post-promotion balance.",
                ],
                [
                    "03",
                    "calculateDeposit(total : BigDecimal) : BigDecimal",
                    "Applies deposit.default_percent to the final non-negative USD total.",
                ],
            ],
        },
        "40. Capture/confirm online payment": {
            "PayPalCheckoutService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "captureOrder(bookingId, orderId, request) : Map<String, Object>",
                    "Uses a stable PayPal request identity, reconciles an ambiguous timeout by reading the order, validates exact USD capture, stores provider fee/net, updates gross paid balance, and regenerates the invoice once.",
                ],
            ],
        },
        "46. Process online refund": {
            "PayPalCheckoutService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "refundCapture(payment, refundAmount, requestId, existingRefundId) : RefundResult",
                    "Uses PayPal Payments v2 with a stable request id, polls an existing provider refund when present, and returns provider truth without inventing completion.",
                ],
            ],
            "PaymentWorkflowService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "updateRefundStatus(refundId, request) : Map<String, Object>",
                    "Maps only provider COMPLETED to completion, preserves booking paidAmount as gross collected, records refund separately, and synchronizes payment and invoice refund state.",
                ],
            ],
        },
        "47. Manage refund requests": {
            "PaymentWorkflowService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "createRefund(request : ApiRequests.RefundCreate) : Map<String, Object>",
                    "Validates Customer ownership and reserves only requested, approved, or processing capacity so completed partial refunds are not subtracted twice.",
                ],
                [
                    "02",
                    "updateRefundStatus(refundId, request) : Map<String, Object>",
                    "Validates operator transitions and completes either a PayPal provider refund or an explicit manual cash refund.",
                ],
                [
                    "03",
                    "refunds() : List<Map<String, Object>>",
                    "Returns newest-first requests with Staff/Admin scope or Customer ownership filtering.",
                ],
            ],
        },
        "51. Manage promotion campaigns": {
            "PromotionReportService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "createPromotion(request : ApiRequests.PromotionUpsert) : Map<String, Object>",
                    "Validates dates, positive discount, non-negative caps/minimum/usage limit, applicability, and stackable policy before creating a campaign.",
                ],
                [
                    "02",
                    "updatePromotion(id, request) : Map<String, Object>",
                    "Applies the same validation while updating campaign eligibility, dates, limits, status, and membership-stacking policy.",
                ],
            ],
        },
        "52. Apply promotion to booking": {
            "DomainSupportService": [
                ["No", "Method", "Description"],
                [
                    "01",
                    "calculatePromotionDiscount(code, slot, baseAmount, services, customer) : BigDecimal",
                    "Validates all campaign conditions and caps either percent or fixed discount at the non-negative subtotal.",
                ],
                [
                    "02",
                    "promotionAllowsMembershipStacking(code : String) : boolean",
                    "Treats stackable as permission to apply the Customer membership discount after the promotion.",
                ],
                [
                    "03",
                    "saveBookingPromotion(booking, code, discount) : void",
                    "Snapshots the applied code/discount and consumes campaign usage once when the booking is created.",
                ],
            ],
        },
    }
    current_section = None
    pending_name = None
    for block in list(iter_blocks(document)):
        if isinstance(block, Paragraph) and block.style.name.startswith("Heading 2"):
            current_section = block.text.strip() if block.text.strip() in sections else None
            pending_name = None
            continue
        if current_section is None:
            continue
        if isinstance(block, Paragraph):
            name = block.text.strip()
            pending_name = name if name in sections[current_section] else None
        elif isinstance(block, Table) and pending_name:
            set_table_rows(block, sections[current_section][pending_name])
            pending_name = None


def update_sds() -> None:
    document = Document(SDS_INPUT)
    update_cover_metadata(document)
    replacements = [
        ("status, account_locked,", "status,"),
        ("'active', false, 0, false", "'active', 0, false"),
        ("status = 'locked', account_locked = true, lock_reason", "status = 'locked', lock_reason"),
        ("status = 'active', account_locked = false, lock_reason", "status = 'active', lock_reason"),
        ("Ask smart assistant for available fields", "Ask availability assistant"),
        (
            "Implementation note: Ranking and promotion eligibility are calculated in Java; no chatbot/RAG table is required.",
            "Implementation note: Gemini interprets and phrases UC-62 only when GEMINI_API_KEY is configured. UC-63 ranking and promotion eligibility remain authoritative Java logic over live data; no chatbot/RAG table is required.",
        ),
        (
            "Implementation note: The API accepts a date and field type; time is represented by the returned slot start_time/end_time rather than a separate time request parameter.",
            "Implementation note: Missing availability is first materialized from the Admin opening, closing, duration, and horizon rules. The API then accepts a date and field type; time is represented by returned start_time/end_time values.",
        ),
        (
            "Implementation note: Blocking is rejected when an active booking already occupies the exact persisted slot.",
            "Implementation note: Block covers every overlapping generated slot and is rejected when any overlap has an active booking. A future exception may also be recorded before that date enters the rolling horizon.",
        ),
        (
            "Implementation note: The product target is PayPal Sandbox, not live-money certification. Mock mode exists for local automated tests.",
            "Implementation note: The product target is PayPal Sandbox, not live-money certification. PayPal receives the exact USD booking balance; processor fee/net are provider-returned merchant values, not customer tax. Mock mode exists only for automated tests.",
        ),
        (
            "Implementation note: Only provider COMPLETED capture is accepted as paid outside mock mode.",
            "Implementation note: Only provider COMPLETED capture is accepted as paid. Capture uses a stable PayPal-Request-Id and reads the order after an ambiguous timeout before changing the local gross-paid ledger.",
        ),
        (
            "Implementation note: SecurityConfig allows promotion mutations only for Admin. The scope supports one applied code per booking; stacking is out of scope even though the schema retains stackable.",
            "Implementation note: SecurityConfig allows promotion mutations only for Admin. One code is supported per booking; stackable explicitly controls whether the membership discount may also apply after the capped promotion.",
        ),
        (
            "Implementation note: Only PayPal provider COMPLETED becomes completed. Real buyer/refund confirmation remains an external sandbox smoke check, not live-money certification.",
            "Implementation note: Only PayPal provider COMPLETED becomes completed. paidAmount remains gross collected, refunds are recorded separately, and the stable provider request id makes retries safe.",
        ),
        (
            "Implementation note: This is a date-focused operations calendar, not a month-grid scheduling engine.",
            "Implementation note: The requested date is materialized before display. This remains a date-focused operations calendar, not a month-grid scheduling engine.",
        ),
        ("Flyway V11", "Flyway V19"),
        ("Flyway V15", "Flyway V19"),
        ("Flyway V16", "Flyway V19"),
        ("Flyway V17", "Flyway V19"),
        ("Flyway V18", "Flyway V19"),
    ]
    replace_text(document, replacements)
    replace_xml_attribute_text(
        document,
        [
            ("Flyway V1 through V11", "Flyway V1 through V19"),
            ("Flyway V1 through V15", "Flyway V1 through V19"),
            ("Flyway V1 through V16", "Flyway V1 through V19"),
            ("Flyway V1 through V17", "Flyway V1 through V19"),
            ("Flyway V1 through V18", "Flyway V1 through V19"),
            ("UC-64", "UC-63"),
        ],
    )
    update_sds_assistant_specs(document)
    update_sds_slot_generation_specs(document)
    update_sds_membership_rule_specs(document)
    update_sds_financial_specs(document)
    update_package_tables(document)
    update_database_table_descriptions(document)

    replace_drawing_after(
        document,
        "1. Code Packages",
        "1. Code Packages",
        PACKAGE_IMAGE,
        max_height=6.8,
    )

    class_images = {
        "1. Register account": "register.png",
        "2. Login": "login.png",
        "5. Change password": "change-password.png",
        "6. Forgot/reset password": "forgot-reset-password.png",
        "7. Manage customer accounts": "manage-customer-accounts.png",
        "9. View customer activity status": "view-customer-activity-status.png",
        "12. Search available fields by date/time": "search-available-fields.png",
        "15. Manage unavailable slots": "manage-unavailable-slots.png",
        "16. View field operation calendar": "view-field-operation-calendar.png",
        "23. Create online booking": "create-online-booking.png",
        "28. Confirm booking": "confirm-booking.png",
        "29. Reject booking request": "reject-booking.png",
        "32. Cancel booking": "cancel-booking.png",
        "30. Reschedule booking": "reschedule-booking.png",
        "20. Update extra services before check-in": "update-extra-services-before-check-in.png",
        "31. Preview cancellation fee/refund": "preview-cancellation-fee-refund.png",
        "34. Complete booking": "complete-booking.png",
        "37. View checkout summary": "view-checkout-summary.png",
        "38. Choose payment option": "choose-payment-option.png",
        "39. Pay deposit/full amount via online payment sandbox": "40.pay-online-sandbox.png",
        "40. Capture/confirm online payment": "41.capture-online-payment.png",
        "46. Process online refund": "process-online-refund.png",
        "52. Apply promotion to booking": "apply-promotion-to-booking.png",
        "41. Confirm remaining payment": "confirm-remaining-payment.png",
        "42. Handle failed/expired payment": "handle-failed-expired-payment.png",
        "54. Manage membership rules": "manage-membership-rules.png",
        "62. Ask availability assistant": "ask-smart-assistant-for-available-fields.png",
    }
    for heading, filename in class_images.items():
        replace_drawing_after(document, heading, "a. Class Diagram", CLASS_RENDER / filename)
    replace_drawing_after(
        document,
        "7. Manage customer accounts",
        "e.1 Class Diagram",
        CLASS_RENDER / "lock-unlock-customer-account.png",
    )

    replace_drawing_after(document, "2. Login", "c. Sequence Diagram", SEQUENCE_RENDER / "Login.png")
    replace_drawing_after(
        document,
        "7. Manage customer accounts",
        "e.3 Sequence Diagram",
        SEQUENCE_RENDER / "Lock-Unlock-Customer-Account.png",
    )
    replace_drawing_after(
        document,
        "62. Ask availability assistant",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-62-ask-availability-assistant.png",
    )
    replace_drawing_after(
        document,
        "12. Search available fields by date/time",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-13-search-available-fields-by-date-time.png",
    )
    replace_drawing_after(
        document,
        "15. Manage unavailable slots",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-16-manage-unavailable-slots.png",
    )
    replace_drawing_after(
        document,
        "16. View field operation calendar",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-17-view-field-operation-calendar.png",
    )
    replace_drawing_after(
        document,
        "54. Manage membership rules",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-55-manage-membership-rules.png",
    )
    replace_drawing_after(
        document,
        "31. Preview cancellation fee/refund",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-32-preview-cancellation-fee-refund.png",
    )
    replace_drawing_after(
        document,
        "40. Capture/confirm online payment",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-41-capture-confirm-online-payment.png",
    )
    replace_drawing_after(
        document,
        "46. Process online refund",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-47-process-online-refund.png",
    )
    replace_drawing_after(
        document,
        "52. Apply promotion to booking",
        "c. Sequence Diagram",
        SEQUENCE_RENDER / "UC-53-apply-promotion-to-booking.png",
    )

    set_update_fields(document)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    document.save(SDS_OUTPUT)


if __name__ == "__main__":
    update_rds()
    update_sds()
    print(RDS_OUTPUT)
    print(SDS_OUTPUT)
