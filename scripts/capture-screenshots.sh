#!/bin/bash
#
# Capture screenshots for app store listings
# Requires: adb, Android device/emulator connected
#
# Usage: ./scripts/capture-screenshots.sh [device_id]
#

set -e

# Configuration
OUTPUT_DIR="fastlane/metadata/android/en-US/images/phoneScreenshots"
DEVICE=${1:-$(adb devices | grep -v "List" | head -1 | cut -f1)}
PACKAGE="io.cleansky.contactless"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}📱 CleanSky Screenshot Capture Tool${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check adb
if ! command -v adb &> /dev/null; then
    echo -e "${RED}❌ adb not found. Install Android SDK platform-tools${NC}"
    exit 1
fi

# Check device
if [ -z "$DEVICE" ]; then
    echo -e "${RED}❌ No device connected${NC}"
    echo "Connect a device or start an emulator:"
    echo "  emulator -avd Pixel_6_API_33"
    exit 1
fi

echo -e "Device: ${YELLOW}$DEVICE${NC}"

# Create output directory
mkdir -p "$OUTPUT_DIR"
echo -e "Output: ${YELLOW}$OUTPUT_DIR${NC}"
echo ""

# Function to capture screenshot
capture() {
    local name=$1
    local description=$2
    local filename="${name}.png"

    echo -e "${YELLOW}📸 $description${NC}"
    echo "   Press ENTER when ready..."
    read -r

    adb -s "$DEVICE" exec-out screencap -p > "$OUTPUT_DIR/$filename"
    echo -e "   ${GREEN}✓ Saved: $filename${NC}"
    echo ""
}

# Function to wait for user to navigate
wait_nav() {
    local screen=$1
    echo -e "${YELLOW}👆 Navigate to: $screen${NC}"
}

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Starting screenshot capture..."
echo "Follow the prompts to navigate the app."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Launch app
echo -e "${GREEN}🚀 Launching CleanSky...${NC}"
adb -s "$DEVICE" shell am start -n "$PACKAGE/.MainActivity" 2>/dev/null || true
sleep 2

# Screenshot sequence
wait_nav "Welcome screen (first launch)"
capture "01_welcome" "Welcome - Your keys, your funds"

wait_nav "Pay screen - Ready state (no pending request)"
capture "02_pay_ready" "Pay - Ready to pay"

wait_nav "Pay screen - Payment request received (simulate or use second device)"
capture "03_pay_request" "Pay - Payment request"

wait_nav "Pay screen - Success animation"
capture "04_pay_success" "Pay - Success"

wait_nav "Collect screen - Amount entry"
capture "05_collect_amount" "Collect - Enter amount"

wait_nav "Collect screen - Waiting for payment"
capture "06_collect_waiting" "Collect - Waiting"

wait_nav "History screen - With some transactions"
capture "07_history" "History - Transactions"

wait_nav "Settings screen - Main view"
capture "08_settings" "Settings"

wait_nav "Settings - Security section expanded"
capture "09_security" "Security settings"

wait_nav "Stealth Wallet screen (if enabled)"
capture "10_stealth_wallet" "Private Wallet"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✅ Screenshot capture complete!${NC}"
echo ""
echo "Files saved to: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"
echo ""
echo "Next steps:"
echo "1. Review screenshots and retake if needed"
echo "2. Copy to other language folders if using same images"
echo "3. Create feature graphic (1024x500)"
