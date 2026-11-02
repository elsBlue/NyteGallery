# Nyte Gallery Privacy Policy

**Effective date:** 2 November 2026

Nyte Gallery is a private photo vault for Android, made by **Nytefall Interactive** ("we", "us").
This policy explains what happens to your information when you use the app.

## Summary

- Your photos stay on your phone, encrypted. We never receive them.
- There are no accounts, no ads, no analytics and no tracking.
- The only information that leaves your phone through the app is your recovery email address and a one-time
  code, and only when you ask for a recovery code (see "Recovery email" below).

## Photos you move into the vault

Photos you move into Nyte Gallery are encrypted (AES-256-GCM) and stored in the app's private storage on your
device. The encryption key is kept in the Android Keystore on your phone and cannot be exported. We do not run
servers that receive your photos, and we cannot see them.

Photo names and dates are kept in an index file inside the app's private storage on your device. This index is
not sent anywhere.

When you move photos in, the app asks Android to remove the originals from your normal gallery. You can decline,
and then the originals stay where they are. When you restore a photo back to your gallery, it is saved as a normal,
unencrypted photo in `Pictures/Nyte Gallery`.

## No accounts, ads or analytics

Nyte Gallery has no sign-in and no user accounts. It shows no ads and does not include analytics, crash
reporting, advertising or tracking tools. We do not sell your data.

## Permissions

- **Photos / storage:** only so you can choose photos to move into the vault, and to put photos back into your
  gallery when you restore them.
- **Notifications:** to show progress while a long task (moving photos, restoring, backing up) runs.
- **Internet:** only for the recovery email described below.
- **Fingerprint:** fingerprint unlock is handled by Android. The app only learns whether the check succeeded; it
  never receives your fingerprint data.

The app does not use your camera, microphone, location or contacts.

## App lock

If you turn on the app lock, your password, PIN or pattern is stored on your device only as a salted hash,
never in plain text. It is not sent anywhere.

## Recovery email

When you set up the app lock, you enter an email address. It is stored on your device. If you later ask for a
recovery code, the app sends your email address and a one-time code to **FormSubmit** (formsubmit.co), a
third-party email delivery service, so the code can be emailed to you. The first time, FormSubmit may ask you to
confirm your address. Nothing else is sent: no photos, photo names or other vault data. FormSubmit handles this
information under its own terms and privacy policy. The connection uses HTTPS.

## Backups

Backups are created only when you choose. Each backup file is encrypted with a password you set, and is saved
wherever you pick using Android's file picker (for example a phone folder, a memory card, or a cloud drive you
use). We never receive the backup file or the password, and **we cannot recover a forgotten backup password**.
If you store a backup with a cloud service, that service's own terms apply.

Android's automatic cloud backup is turned off for Nyte Gallery, so the vault is not copied to Google's backup.

## Feedback and links

If you tap "Send feedback", your own email app opens and you decide what to send. If you email us, we receive your
email address and your message, and use them only to reply. "Privacy policy" and "Terms of service" open in your
browser.

## Deleting your data

All app data is on your device. Uninstalling Nyte Gallery or clearing its storage permanently deletes the vault,
the index, the app lock settings and the encryption key on that device. Photos still in the vault are then gone
for good unless you have a backup file. Photos in Trash are deleted automatically after 30 days, or right away if
you empty Trash. Backup files you saved are not deleted by the app; you manage them yourself.

Because we have no accounts or servers, we hold no data about you to delete. You can still contact us with any
question.

## Children

Nyte Gallery is not directed at children under 13.

## Changes

We will update this page if the way the app handles data changes, for example if paid features are added. The
effective date above shows when it last changed.

## Contact

Nytefall Interactive: **nytefall.interactive@gmail.com**
