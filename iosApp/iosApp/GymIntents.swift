import AppIntents
import SharedUI

// Siri and Shortcuts actions. They run in the background against the same database as the app,
// through the shared GymAssistant; nothing here opens the app or deletes data.

private var assistant: GymAssistant { GymAssistant_iosKt.gymAssistant() }

private func rupees(_ amount: Int64) -> String { GymAssistant_iosKt.rupees(amount: amount) }

private func say(_ text: String) -> IntentDialog { IntentDialog(stringLiteral: text) }

/// "4 Dec" from a Kotlin date.
private func shortDate(_ date: Kotlinx_datetimeLocalDate) -> String {
    let components = DateComponents(year: Int(date.year), month: Int(date.month.ordinal) + 1, day: Int(date.day))
    guard let day = Calendar.current.date(from: components) else { return "" }
    return day.formatted(.dateTime.day().month(.abbreviated))
}

/// "Ravi is active until 4 Dec and owes ₹500."
private func standing(of member: MemberSummary) -> String {
    let end = member.latestEndDate.map(shortDate) ?? ""
    let status: String
    switch member.status {
    case .active: status = "is active until \(end)"
    case .expiringSoon: status = "ends soon, on \(end)"
    case .expired: status = "expired on \(end)"
    default: status = "has no membership"
    }
    let dues = member.amountDue > 0 ? "owes \(rupees(member.amountDue))" : "owes nothing"
    return "\(member.name) \(status) and \(dues)."
}

// MARK: - Member

struct MemberEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Member"
    static let defaultQuery = MemberQuery()

    let id: String
    let name: String
    let phone: String?

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)", subtitle: phone.map { "\($0)" })
    }
}

extension MemberEntity {
    init(_ member: MemberSummary) {
        self.init(id: member.memberId, name: member.name, phone: member.phone)
    }
}

/// Lets Siri find a member by the name or phone number the owner says.
struct MemberQuery: EntityStringQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [MemberEntity] {
        var found: [MemberEntity] = []
        for id in identifiers {
            if let member = try await assistant.member(memberId: id) {
                found.append(MemberEntity(member))
            }
        }
        return found
    }

    @MainActor
    func entities(matching string: String) async throws -> [MemberEntity] {
        try await assistant.findMembers(query: string, limit: GymAssistant.companion.MAX_RESULTS).members.map(MemberEntity.init)
    }

    @MainActor
    func suggestedEntities() async throws -> [MemberEntity] {
        try await assistant.findMembers(query: "", limit: GymAssistant.companion.MAX_RESULTS).members.map(MemberEntity.init)
    }
}

// MARK: - Read-only intents

struct GymSummaryIntent: AppIntent {
    static let title: LocalizedStringResource = "Gym Summary"
    static let description = IntentDescription("Active, expiring and expired members, dues, and money collected this month.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let s = try await assistant.summary()
        return .result(dialog: say(
            "\(s.activeCount) active members, \(s.expiringSoon.count) expiring soon and \(s.expiredCount) expired. "
                + "\(rupees(s.totalDue)) is due from \(s.membersWithDuesCount) members. "
                + "\(rupees(s.revenueThisMonth)) collected this month."
        ))
    }
}

struct MemberStatusIntent: AppIntent {
    static let title: LocalizedStringResource = "Check Member"
    static let description = IntentDescription("A member's membership status, end date and dues.")

    @Parameter(title: "Member", requestValueDialog: "Which member?")
    var member: MemberEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Check \(\.$member)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let summary = try await assistant.member(memberId: member.id) else {
            return .result(dialog: say("\(member.name) isn't in the gym's members any more."))
        }
        return .result(dialog: say(standing(of: summary)))
    }
}

enum MemberStatusFilter: String, AppEnum {
    case expiringSoon, expired, withDues

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Member Status"
    static let caseDisplayRepresentations: [MemberStatusFilter: DisplayRepresentation] = [
        .expiringSoon: "expiring soon",
        .expired: "expired",
        .withDues: "dues",
    ]

    var filter: MemberFilter {
        switch self {
        case .expiringSoon: return .expiringSoon
        case .expired: return .expired
        case .withDues: return .withDues
        }
    }
}

struct MembersByStatusIntent: AppIntent {
    static let title: LocalizedStringResource = "Members by Status"
    static let description = IntentDescription("Members expiring soon, expired, or with dues, most urgent first.")

    @Parameter(title: "Status", requestValueDialog: "Expiring soon, expired, or with dues?")
    var status: MemberStatusFilter

    static var parameterSummary: some ParameterSummary {
        Summary("Members with status \(\.$status)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let list = try await assistant.members(filter: status.filter, limit: GymAssistant.companion.MAX_RESULTS)
        guard list.totalCount > 0 else {
            switch status {
            case .expiringSoon: return .result(dialog: "No memberships are ending soon.")
            case .expired: return .result(dialog: "No members have expired.")
            case .withDues: return .result(dialog: "Nobody owes anything.")
            }
        }
        let names = list.members.map { member -> String in
            switch status {
            case .withDues: return "\(member.name) (\(rupees(member.amountDue)))"
            default: return "\(member.name) (\(member.latestEndDate.map(shortDate) ?? ""))"
            }
        }
        let more = Int(list.totalCount) - names.count
        let heading: String
        switch status {
        case .expiringSoon: heading = "\(list.totalCount) expiring soon"
        case .expired: heading = "\(list.totalCount) expired"
        case .withDues: heading = "\(list.totalCount) with dues"
        }
        return .result(dialog: say("\(heading): \(names.joined(separator: ", "))\(more > 0 ? ", and \(more) more" : "")."))
    }
}

struct ListPlansIntent: AppIntent {
    static let title: LocalizedStringResource = "List Plans"
    static let description = IntentDescription("The membership plans the gym sells, with duration and price.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let plans = try await assistant.plans()
        guard !plans.isEmpty else { return .result(dialog: "No plans have been set up yet.") }
        let lines = plans.map { plan -> String in
            let unit = plan.durationUnit == .months ? "month" : "day"
            let duration = "\(plan.durationValue) \(unit)\(plan.durationValue == 1 ? "" : "s")"
            return "\(plan.name): \(duration), \(rupees(plan.defaultPrice))"
        }
        return .result(dialog: say(lines.joined(separator: ". ") + "."))
    }
}

// MARK: - Record payment

enum PaymentModeOption: String, AppEnum {
    case cash, upi, other

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Payment Mode"
    static let caseDisplayRepresentations: [PaymentModeOption: DisplayRepresentation] = [
        .cash: "cash",
        .upi: "UPI",
        .other: "other",
    ]

    var mode: PaymentMode {
        switch self {
        case .cash: return .cash
        case .upi: return .upi
        case .other: return .other
        }
    }
}

struct RecordPaymentIntent: AppIntent {
    static let title: LocalizedStringResource = "Record Payment"
    static let description = IntentDescription("Records money a member paid towards what they owe, oldest dues first.")

    @Parameter(title: "Member", requestValueDialog: "Who paid?")
    var member: MemberEntity

    @Parameter(title: "Amount (₹)", inclusiveRange: (1, 10_000_000), requestValueDialog: "How many rupees?")
    var amount: Int

    @Parameter(title: "Mode", default: .cash)
    var mode: PaymentModeOption

    static var parameterSummary: some ParameterSummary {
        Summary("Record ₹\(\.$amount) from \(\.$member) by \(\.$mode)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let amount = Int64(self.amount)
        let modeName = mode == .upi ? "UPI" : mode.rawValue
        try await requestConfirmation(result: .result(dialog: say("Record \(rupees(amount)) from \(member.name) by \(modeName)?")))

        let outcome = try await assistant.recordPayment(memberId: member.id, amount: amount, mode: mode.mode)
        switch outcome {
        case let recorded as PaymentOutcomeRecorded:
            let left = recorded.remainingDue > 0 ? "\(rupees(recorded.remainingDue)) is still due." : "Nothing more is due."
            return .result(dialog: say("Recorded \(rupees(recorded.amount)) from \(recorded.memberName). \(left)"))
        case let tooMuch as PaymentOutcomeMoreThanDue:
            let owes = tooMuch.amountDue > 0 ? "owes only \(rupees(tooMuch.amountDue))" : "doesn't owe anything"
            return .result(dialog: say("\(tooMuch.memberName) \(owes), so nothing was recorded."))
        case is PaymentOutcomeMemberNotFound:
            return .result(dialog: say("\(member.name) isn't in the gym's members any more."))
        default:
            return .result(dialog: "The amount must be more than zero.")
        }
    }
}

// MARK: - Siri phrases

struct GymShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: GymSummaryIntent(),
            phrases: [
                "How is \(.applicationName) doing",
                "\(.applicationName) summary",
            ],
            shortTitle: "Gym Summary",
            systemImageName: "chart.bar"
        )
        AppShortcut(
            intent: MemberStatusIntent(),
            phrases: [
                "Check a member in \(.applicationName)",
                "Look up a member in \(.applicationName)",
            ],
            shortTitle: "Check Member",
            systemImageName: "person.crop.circle"
        )
        AppShortcut(
            intent: MembersByStatusIntent(),
            phrases: [
                "Who has \(\.$status) in \(.applicationName)",
                "Show \(\.$status) members in \(.applicationName)",
                "Who needs follow-up in \(.applicationName)",
            ],
            shortTitle: "Members by Status",
            systemImageName: "person.2"
        )
        AppShortcut(
            intent: ListPlansIntent(),
            phrases: ["List plans in \(.applicationName)"],
            shortTitle: "List Plans",
            systemImageName: "list.bullet"
        )
        AppShortcut(
            intent: RecordPaymentIntent(),
            phrases: [
                "Record a payment in \(.applicationName)",
                "Add a payment in \(.applicationName)",
            ],
            shortTitle: "Record Payment",
            systemImageName: "indianrupeesign.circle"
        )
    }
}
