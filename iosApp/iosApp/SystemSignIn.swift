import AuthenticationServices
import CryptoKit
import SharedUI

/// Apple's and Google's sign-in sheets for the shared sign-in screen. Each hands back the ID token and
/// the random nonce it was asked for with; the token carries the nonce's hash, which Supabase checks.
final class SystemSignIn: NSObject, NativeIdentitySignIn {
    /// The sheet being shown, kept alive until it answers.
    private var pending: AnyObject?

    func signInWithApple(onResult: @escaping (String?, String?, String?) -> Void) {
        let nonce = randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        let sheet = AppleSheet(nonce: nonce) { [weak self] token, nonce, error in
            self?.pending = nil
            onResult(token, nonce, error)
        }
        controller.delegate = sheet
        controller.presentationContextProvider = sheet
        pending = sheet
        controller.performRequests()
    }

    /// Google's own sign-in page in the system's web sheet, with PKCE, as Google's iOS SDK does it.
    func signInWithGoogle(clientId: String, onResult: @escaping (String?, String?, String?) -> Void) {
        // An iOS client's redirect scheme is its ID reversed: com.googleusercontent.apps.<id>.
        let scheme = clientId.split(separator: ".").reversed().joined(separator: ".")
        let redirect = "\(scheme):/oauth2redirect"
        let nonce = randomNonce()
        let verifier = randomNonce()
        let state = randomNonce()
        var page = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        page.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirect),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: base64URL(Data(SHA256.hash(data: Data(verifier.utf8))))),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "nonce", value: sha256(nonce)),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "prompt", value: "select_account"),
        ]
        let finish: (String?, String?, String?) -> Void = { [weak self] token, nonce, error in
            DispatchQueue.main.async {
                self?.pending = nil
                onResult(token, nonce, error)
            }
        }
        let session = ASWebAuthenticationSession(url: page.url!, callbackURLScheme: scheme) { url, error in
            if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                return finish(nil, nil, nil)
            }
            let answer = url.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems }
            guard let code = answer?.first(where: { $0.name == "code" })?.value,
                  answer?.first(where: { $0.name == "state" })?.value == state else {
                return finish(nil, nil, error?.localizedDescription ?? "Google didn't sign in")
            }
            Self.exchange(code: code, clientId: clientId, redirect: redirect, verifier: verifier) { token, error in
                finish(token, token == nil ? nil : nonce, error)
            }
        }
        session.presentationContextProvider = self
        pending = session
        session.start()
    }

    /// Trades the one-time code for the tokens; an iOS client proves itself with the PKCE verifier, not a secret.
    private static func exchange(
        code: String, clientId: String, redirect: String, verifier: String,
        done: @escaping (String?, String?) -> Void
    ) {
        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var form = URLComponents()
        form.queryItems = [
            URLQueryItem(name: "code", value: code),
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirect),
            URLQueryItem(name: "grant_type", value: "authorization_code"),
            URLQueryItem(name: "code_verifier", value: verifier),
        ]
        request.httpBody = form.percentEncodedQuery?.data(using: .utf8)
        URLSession.shared.dataTask(with: request) { data, _, error in
            let json = data.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
            if let token = json?["id_token"] as? String {
                done(token, nil)
            } else {
                done(nil, error?.localizedDescription ?? (json?["error"] as? String) ?? "No token from Google")
            }
        }.resume()
    }
}

extension SystemSignIn: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { keyWindow() }
}

/// Answers Apple's sheet once.
private final class AppleSheet: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private let nonce: String
    private let onResult: (String?, String?, String?) -> Void

    init(nonce: String, onResult: @escaping (String?, String?, String?) -> Void) {
        self.nonce = nonce
        self.onResult = onResult
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let data = credential.identityToken, let token = String(data: data, encoding: .utf8) else {
            return onResult(nil, nil, "Apple gave no token")
        }
        onResult(token, nonce, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if (error as? ASAuthorizationError)?.code == .canceled {
            onResult(nil, nil, nil)
        } else {
            onResult(nil, nil, error.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor { keyWindow() }
}

private func keyWindow() -> UIWindow {
    UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap(\.windows)
        .first(where: \.isKeyWindow) ?? UIWindow()
}

private func randomNonce() -> String {
    var bytes = [UInt8](repeating: 0, count: 32)
    _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
    return bytes.map { String(format: "%02x", $0) }.joined()
}

/// Lowercase hex, which is how Supabase hashes the nonce to compare it.
private func sha256(_ value: String) -> String {
    SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
}

private func base64URL(_ data: Data) -> String {
    data.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}
