import SwiftUI

enum LineColorPalette {
    static func badgeBackground(hex: String?) -> Color {
        guard let (red, green, blue) = rgb(from: hex) else {
            return Color.secondary.opacity(0.18)
        }
        return Color(red: red, green: green, blue: blue)
    }

    static func badgeForeground(hex: String?) -> Color {
        guard let (red, green, blue) = rgb(from: hex) else {
            return .primary
        }
        let luminance = (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
        return luminance > 0.62 ? .black : .white
    }

    private static func rgb(from hex: String?) -> (Double, Double, Double)? {
        guard var normalized = hex?.trimmingCharacters(in: .whitespacesAndNewlines), !normalized.isEmpty else {
            return nil
        }
        if normalized.hasPrefix("#") {
            normalized.removeFirst()
        }
        guard normalized.count == 6, let value = UInt64(normalized, radix: 16) else {
            return nil
        }

        let red = Double((value & 0xFF0000) >> 16) / 255
        let green = Double((value & 0x00FF00) >> 8) / 255
        let blue = Double(value & 0x0000FF) / 255
        return (red, green, blue)
    }
}

struct LineBadge: View {
    let line: String
    let colorHex: String?
    var font: Font = .caption
    var horizontalPadding: CGFloat = 8
    var verticalPadding: CGFloat = 3
    var minWidth: CGFloat = 26

    var body: some View {
        Text(line)
            .font(font.weight(.semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
            .frame(minWidth: minWidth)
            .foregroundStyle(LineColorPalette.badgeForeground(hex: colorHex))
            .background(LineColorPalette.badgeBackground(hex: colorHex), in: Capsule())
    }
}
