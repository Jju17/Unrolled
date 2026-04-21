//
//  ContentView.swift
//  PicSwipe
//

import ComposableArchitecture
import SwiftUI

struct ContentView: View {
    var body: some View {
        SwipeScreen(
            store: Store(initialState: SwipeFeature.State()) {
                SwipeFeature()
            }
        )
    }
}

#Preview {
    ContentView()
}
