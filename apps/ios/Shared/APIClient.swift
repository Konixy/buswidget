import Foundation

public enum APIClientError: Error {
    case invalidURL
    case invalidResponse
    case serverError(Int)
    case decodingError
}

extension APIClientError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "The API URL is invalid."
        case .invalidResponse:
            return "The server response is invalid."
        case .serverError(let code):
            return "The API returned an error (\(code))."
        case .decodingError:
            return "The API response format is invalid."
        }
    }
}

public final class APIClient {
    private let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder

    public init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
        self.decoder = JSONDecoder()
    }

    public func searchStops(query: String, limit: Int = 20) async throws -> StopSearchResponse {
        var components = URLComponents(
            url: baseURL.appending(path: "/v1/rouen/stops/search"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "limit", value: String(limit))
        ]

        guard let url = components?.url else {
            throw APIClientError.invalidURL
        }

        return try await send(url: url)
    }

    public func departures(
        stopId: String,
        limit: Int = 8,
        maxMinutes: Int = 90,
        lines: [String]? = nil
    ) async throws -> StopDeparturesResponse {
        var components = URLComponents(
            url: baseURL.appending(path: "/v1/rouen/stops/\(stopId)/departures"),
            resolvingAgainstBaseURL: false
        )
        var queryItems = [
            URLQueryItem(name: "limit", value: String(limit)),
            URLQueryItem(name: "maxMinutes", value: String(maxMinutes))
        ]
        if let lines, !lines.isEmpty {
            let normalizedLines = Array(
                Set(
                    lines
                        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                )
            ).sorted()
            if !normalizedLines.isEmpty {
                queryItems.append(URLQueryItem(name: "lines", value: normalizedLines.joined(separator: ",")))
            }
        }
        components?.queryItems = queryItems

        guard let url = components?.url else {
            throw APIClientError.invalidURL
        }

        return try await send(url: url)
    }

    private func send<T: Decodable>(url: URL) async throws -> T {
        let (data, response) = try await session.data(from: url)

        guard let http = response as? HTTPURLResponse else {
            throw APIClientError.invalidResponse
        }

        guard (200..<300).contains(http.statusCode) else {
            throw APIClientError.serverError(http.statusCode)
        }

        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIClientError.decodingError
        }
    }
}
