import Foundation
import FoundationModels
import SharedUI

/// Apple's on-device model (iOS 26+, Apple Intelligence iPhones) for the Home brief.
/// Elsewhere, or when it isn't ready, it answers nil and the app uses its own wording.
final class AppleOnDeviceModel: NSObject, OnDeviceModel {
    func generate(instructions: String, prompt: String, onResult: @escaping (String?) -> Void) {
        guard #available(iOS 26, *) else {
            onResult(nil)
            return
        }
        Task {
            let reply = await Self.respond(instructions: instructions, prompt: prompt)
            await MainActor.run { onResult(reply) }
        }
    }

    @available(iOS 26, *)
    private static func respond(instructions: String, prompt: String) async -> String? {
        guard case .available = SystemLanguageModel.default.availability else { return nil }
        let session = LanguageModelSession(instructions: instructions)
        do {
            let options = GenerationOptions(temperature: 0.3, maximumResponseTokens: 160)
            return try await session.respond(to: prompt, options: options).content
        } catch {
            return nil
        }
    }
}
