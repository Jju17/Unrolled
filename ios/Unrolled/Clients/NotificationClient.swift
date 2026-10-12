//
//  NotificationClient.swift
//  Unrolled
//

import ComposableArchitecture
import Foundation
import UserNotifications

/// Local daily reminder. Nothing leaves the device.
struct NotificationClient: Sendable {
    var cancelReminder: @Sendable () -> Void
    var requestAuthorization: @Sendable () async -> Bool
    var scheduleDailyReminder: @Sendable (_ hour: Int, _ goal: Int) -> Void
}

extension NotificationClient: DependencyKey {
    static let reminderIdentifier = "dailyReminder"

    static var liveValue: NotificationClient {
        NotificationClient(
            cancelReminder: {
                UNUserNotificationCenter.current()
                    .removePendingNotificationRequests(withIdentifiers: [reminderIdentifier])
            },
            requestAuthorization: {
                let granted = try? await UNUserNotificationCenter.current()
                    .requestAuthorization(options: [.alert, .sound])
                return granted ?? false
            },
            scheduleDailyReminder: { hour, goal in
                let content = UNMutableNotificationContent()
                content.title = String(localized: "Unrolled")
                content.body = String(localized: "Your gallery is waiting: \(goal) photos today?")
                var components = DateComponents()
                components.hour = hour
                components.minute = 0
                let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
                let request = UNNotificationRequest(
                    identifier: reminderIdentifier, content: content, trigger: trigger
                )
                let center = UNUserNotificationCenter.current()
                center.removePendingNotificationRequests(withIdentifiers: [reminderIdentifier])
                center.add(request)
            }
        )
    }

    static var testValue: NotificationClient {
        NotificationClient(
            cancelReminder: {},
            requestAuthorization: { true },
            scheduleDailyReminder: { _, _ in }
        )
    }

    static var previewValue: NotificationClient { testValue }
}

extension DependencyValues {
    var notifications: NotificationClient {
        get { self[NotificationClient.self] }
        set { self[NotificationClient.self] = newValue }
    }
}
