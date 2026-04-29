# Muting Sequence Implementation

## Overview
The muting sequence has been modified to follow a 4-state cycle as requested. Each time an ad is detected from the same app while already in a mute cycle, the sequence advances to the next state.

## Muting Sequence States

### State 0 (Normal)
- Media volume is at normal level
- No muting active
- When an ad is detected: Transition to State 1

### State 1 (Muted)
- Media volume is set to 0 (muted)
- No sounds are played
- Toast notification: "State 1: Muted"
- When another ad is detected: Transition to State 2

### State 2 (Play In Sound)
- Media volume remains at 0 (muted)
- IN sound is played (not affected by mute)
- Toast notification: "State 2: Playing in sound"
- When another ad is detected: Transition to State 3

### State 3 (Play Out Sound)
- Media volume remains at 0 (muted)
- OUT sound is played (not affected by mute)
- Toast notification: "State 3: Playing out sound"
- When another ad is detected: Transition to State 0 (unmute)

### State 0 (Unmute) - Final
- Media volume is restored to the original level before muting
- Cooldown timer is activated
- Toast notification: "State 4: Unmuted"
- Back to normal state

## Key Features

1. **4-State Cycling**: The sequence cycles through 4 distinct states with each ad detection from the same app
2. **Sound Independence**: IN and OUT sounds are played on the NOTIFICATION stream (AudioManager.STREAM_NOTIFICATION), which is independent of the MUSIC stream mute. This ensures sounds are always audible regardless of mute state.
3. **Volume Notification**: Notification stream volume is set to 50% of max to ensure sounds are heard during the cycle
4. **State Tracking**: The `muteSequenceState` variable tracks the current position in the sequence
5. **Reset on Unmute**: When State 0 is reached again, the sequence resets and cooldown is activated

## Implementation Details

### Modified Methods

#### `startMuteCycle(packageName: String, content: String, notificationKey: String)`
- Implements the 4-state machine logic
- Each state transition is clearly defined with appropriate actions
- Logging and toast messages for user feedback

#### `onNotificationPosted(sbn: StatusBarNotification?)`
- Updated to call `startMuteCycle` on each ad detection
- Distinguishes between:
  - First ad from an app (State 0 → 1)
  - Subsequent ads from same app (advance through states)
  - Ads from different apps (ignored while in cycle)

#### `endMuteCycle(reason: String)`
- Now resets `muteSequenceState` to 0
- Ensures proper cleanup when cycle is manually ended

#### `playRawSound()` and `playCustomSound()`
- Enhanced to explicitly set NOTIFICATION stream volume
- Ensures sounds play at 50% of max notification volume regardless of mute state

### State Variables Added

- `muteSequenceState`: Int (0-3) - Tracks current position in the 4-state cycle

## Behavior Flow

```
Ad Detected (App A, not in mute) → State 1 (Mute)
     ↓
Ad Detected (App A, in mute) → State 2 (Play IN sound, stay muted)
     ↓
Ad Detected (App A, in mute) → State 3 (Play OUT sound, stay muted)
     ↓
Ad Detected (App A, in mute) → State 0 (Unmute, restore volume)
     ↓
Cooldown Active → Wait for cooldown to expire before new cycle can start
```

## Testing Recommendations

1. **Single App Detection**: Test with ads from one app to verify state transitions
2. **Multiple Apps**: Verify that ads from other apps are ignored during an active cycle
3. **Sound Playback**: Confirm IN and OUT sounds play even when media is muted
4. **Cooldown**: Verify cooldown prevents new cycles from starting immediately
5. **Volume Restoration**: Ensure volume is properly restored after unmute

## Notes

- If no ad is detected from the same app, the sequence can be reset by the `endMuteCycle` method
- The cooldown timer prevents rapid cycling through the sequence
- User receives clear feedback via toast notifications for each state transition
