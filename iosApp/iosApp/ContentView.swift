import SwiftUI
import SharedUI

/// Hosts the shared Compose UI; everything on screen is drawn by it.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose handles the safe areas and the keyboard itself.
        ComposeView().ignoresSafeArea()
    }
}
