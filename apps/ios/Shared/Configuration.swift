import Foundation

public enum AppConfiguration {
    public static func baseURL(bundle: Bundle = .main) -> URL {
        if let value = bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
           let url = URL(string: value) {
            return url
        }

        return URL(string: "http://127.0.0.1:3000")!
    }
}
