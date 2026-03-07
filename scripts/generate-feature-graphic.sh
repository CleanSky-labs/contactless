#!/bin/bash
#
# Generate feature graphic (1024x500) for app stores
# Requires: ImageMagick (convert command)
#
# Usage: ./scripts/generate-feature-graphic.sh
#

set -e

OUTPUT_DIR="fastlane/metadata/android/en-US/images"
ICON_PATH="icon.png"

# Colors (CleanSky brand)
BG_COLOR="#E3F2FD"       # Light blue (Pay mode)
PRIMARY="#1976D2"        # Blue
TEXT_COLOR="#0D47A1"     # Dark blue

echo "🎨 Generating Feature Graphic..."

# Check ImageMagick
if ! command -v convert &> /dev/null; then
    echo "❌ ImageMagick not found."
    echo ""
    echo "Install with:"
    echo "  Ubuntu/Debian: sudo apt install imagemagick"
    echo "  macOS: brew install imagemagick"
    echo ""
    echo "Or create manually with these specs:"
    echo "  Size: 1024x500 pixels"
    echo "  Background: $BG_COLOR"
    echo "  Logo: Left side"
    echo "  Text: CleanSky - Contactless Crypto Payments"
    echo "  Tagline: Self-custody • NFC • 70+ languages"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Generate feature graphic
convert -size 1024x500 xc:"$BG_COLOR" \
    -gravity West \
    \( "$ICON_PATH" -resize 200x200 \) -geometry +80+0 -composite \
    -gravity Center \
    -font "DejaVu-Sans-Bold" -pointsize 48 -fill "$TEXT_COLOR" \
    -annotate +50+0 "CleanSky" \
    -font "DejaVu-Sans" -pointsize 28 -fill "$PRIMARY" \
    -annotate +50+50 "Contactless Crypto Payments" \
    -font "DejaVu-Sans" -pointsize 18 -fill "#757575" \
    -annotate +50+100 "🔒 Self-custody   📱 NFC   🌐 70+ languages" \
    "$OUTPUT_DIR/featureGraphic.png"

echo "✅ Feature graphic saved to: $OUTPUT_DIR/featureGraphic.png"

# Generate for other languages (copy)
for lang in es-ES pt-BR fr-FR ar fa ru-RU tr-TR zh-CN hi-IN; do
    mkdir -p "fastlane/metadata/android/$lang/images"
    cp "$OUTPUT_DIR/featureGraphic.png" "fastlane/metadata/android/$lang/images/"
done

echo "✅ Copied to all language folders"
