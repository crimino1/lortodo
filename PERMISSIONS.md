# Permissions

Every permission in the final merged manifest, checked against the built release APK.

| Permission | Source | Why LOR needs it |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | LOR | Show reminders, nudges, the daily summary and the ringing alarm's notification (Android 13+; asked for during onboarding). |
| `SCHEDULE_EXACT_ALARM` | LOR | Fire reminders at the exact minute, including in Doze (Android 12+; the user turns it on in system settings during onboarding). Without it, reminders still arrive but may be late. |
| `USE_FULL_SCREEN_INTENT` | LOR | Show the alarm screen over the lock screen. On Android 14+ where it isn't granted, the alarm falls back to a heads-up notification and Settings shows a prompt. |
| `RECEIVE_BOOT_COMPLETED` | LOR | Re-arm all reminders after a reboot, because Android forgets scheduled alarms when it restarts. |
| `VIBRATE` | LOR | Vibrate while the alarm rings (can be turned off in Settings). |
| `WAKE_LOCK` | LOR | Keep the CPU awake while the alarm sound plays with the screen off. |
| `FOREGROUND_SERVICE` | LOR | Keep the alarm ringing as a foreground service until it is answered. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | LOR | Required by Android 14+ for the foreground service type that plays the alarm sound. |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core | Added automatically by the library. It is private to the app and grants nothing beyond LOR itself. |

## Deliberately absent

- `INTERNET`: LOR cannot connect to anything. `ManifestTest` guards this.
- `ACCESS_NETWORK_STATE`: requested by WorkManager, a dependency of the Glance widget library. LOR removes it in its manifest because it never uses the network.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: the battery tip opens the system settings page instead, which needs no permission.
- Storage permissions: backups use the Storage Access Framework, where you pick the file.
