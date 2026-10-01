# Lone Rider iOS / CarPlay v0.1

This is the first native iOS implementation of Lone Rider, aligned with the Android app and prepared for CarPlay.

## Included
- SwiftUI iPhone app with Home, Games, Stats and Settings
- Quick Trivia, Guess WHO, Spelling Bee and Family Battle shells
- microphone + Speech framework permission flow
- TTS -> STT voice loop for spoken questions and answers
- CarPlay template scene with voice-game launcher
- XcodeGen project definition
- unsigned iOS Simulator CI build

## Local build on macOS
```bash
brew install xcodegen
cd ios
xcodegen generate
xcodebuild -project LoneRider.xcodeproj -scheme LoneRider -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO
```

## Physical iPhone / CarPlay
To install on a physical iPhone, set your Apple Development Team and signing in Xcode.
For the CarPlay surface to appear in a real vehicle, Apple must grant the voice-based conversation CarPlay entitlement. After approval, copy `LoneRider.entitlements.example` to `LoneRider.entitlements` and set `CODE_SIGN_ENTITLEMENTS` to that file for the signed target.

The entitlement is intentionally not enabled in CI so an unsigned simulator build can compile without an Apple provisioning profile.
