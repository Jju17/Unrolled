//
//  UnrolledApp.swift
//  Unrolled
//

import SwiftUI

@main
struct UnrolledApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
