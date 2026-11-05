import SwiftUI
import SharedUI
import UserNotifications

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor private var appDelegate: AppDelegate

    init() {
        OnDeviceModel_iosKt.installOnDeviceModel(model: AppleOnDeviceModel())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Shows the morning update as a banner even when the app is open at that moment.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
