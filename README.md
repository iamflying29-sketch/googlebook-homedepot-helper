# Home Depot Helper

Home Depot Helper is a small open-source Android app for my Lenovo Googlebook 15. It takes a Home
Depot order notification from my
[Notification Keeper](https://github.com/iamflying29-sketch/googlebook-notification-keeper) to the
exact place in the Home Depot app, instead of just opening the app. The Home Depot app's own push
notifications never reach my Googlebook, so Notification Keeper turns Home Depot's order emails into
Home Depot notifications. This helper decides what a tap on such a notification should open, and it
opens it.

## What it does

When I tap a Home Depot notification, the helper reads the email's subject and preview, which is all
a notification carries, and acts on them:

* **An order email that names an order number** (shipped, out for delivery, delivered, rescheduled,
  cancelled, confirmed, picked up) opens that order's own details page inside the Home Depot app,
  with its status timeline, tracking, items and return barcode. The helper opens the same address the
  Home Depot app opens for the "Manage Order" button in Home Depot's own emails,
  `https://www.homedepot.com/myaccount/order-details?orderNumber=<number>`, and the app handles that
  address natively.
* **An order email without an order number in the notification** (for example "We received your
  order!" or "Update on your order.", where the number comes later in the email than the preview
  reaches) opens the purchase history in the Home Depot app, newest order first. That is the page the
  app opens for Home Depot's own "Check Order Status" button.
* **A sign-in verification code email** copies the code to the clipboard, opens the Home Depot app,
  and the helper's accessibility service types the code into the app's code field as soon as it sees
  one. If no code field appears within 45 seconds, nothing is typed and the code stays on the
  clipboard for me to paste.
* **Anything else** (a security alert, a password change, a review request, a promotion) simply opens
  the Home Depot app.

If the Home Depot app is not installed, the helper opens homedepot.com instead. If the helper itself is
not installed, Notification Keeper's Home Depot notification opens the Home Depot app, exactly as it
did before this helper existed.

## How I built it

* The Home Depot app registers handlers for `https://www.homedepot.com/myaccount/...` addresses, as
  well as for its email tracking links. I confirmed that by reading the app's manifest and by following
  the redirects behind the buttons in my own Home Depot order emails: "Manage Order" ends at the
  `order-details` address above and "Check Order Status" at `myaccount/purchase-history`. Opening those
  addresses with the Home Depot app named as the target lands on the exact page, so no screen tapping
  is needed for orders at all.
* The first time the Home Depot app is opened by a link, GooglebookOS asks "How do you want to open
  Home Depot links?" Choose **Stay in app** once, and it never asks again.
* The verification code cannot be opened by a link, because it has to be typed into a field. That is
  the one job of the accessibility service, `HdHelperService`. It only receives events from the Home
  Depot app, it only looks at the screen while a code is waiting to be typed, it performs no gestures,
  and it never types into a search box, an email field or a password field. When the app shows one box
  per digit it types one digit into each box.
* The entry point, `OpenActivity`, is invisible and finishes at once. It only accepts an email's
  subject and text from Notification Keeper (and from adb, for testing). Opened by anything else, it
  just opens the Home Depot app, so no other app can make it copy a code or open a page.
* Android switches an accessibility service off whenever its app is updated. With one permission
  granted over adb (below), the helper switches its own service back on by itself after an update and
  after a restart, leaving every other accessibility service untouched.

## Requirements

* A Lenovo Googlebook 15 running GooglebookOS (Android 17 desktop). The app needs Android 14 or
  newer. I have only tested it on my Googlebook.
* The Home Depot app, and Notification Keeper with its Home Depot order notifications.
* The Linux Terminal with adb connected to the Googlebook, plus a JDK, Python 3, the Android SDK
  build tools 35.0.0 with the android-35 platform, and the Kotlin compiler, to build the APK. The
  shared [Getting Started guide](https://github.com/iamflying29-sketch/googlebook-projects/blob/main/GETTING-STARTED.md)
  covers all of it.

## Installation

Replace `<USER_ID>` with your Android user ID (`adb shell am get-current-user`).

1. Build it. The first build creates `keystore.jks` with the password you give; keep both for updates.

   ```bash
   git clone https://github.com/iamflying29-sketch/googlebook-homedepot-helper.git ~/googlebook-homedepot-helper
   cd ~/googlebook-homedepot-helper
   KS_PASS='choose-a-long-password' ./build.sh
   ```

2. Install it:

   ```bash
   adb install -r --user <USER_ID> HomeDepotHelper.apk
   ```

3. Let it keep its own accessibility service on (one time). Without this, you turn the service on by
   hand in Settings, then Accessibility, after every update:

   ```bash
   adb shell pm grant --user <USER_ID> local.hdhelper android.permission.WRITE_SECURE_SETTINGS
   ```

4. Open **Home Depot Helper** once from the launcher. Its status window shows "The helper is on" when
   the accessibility service is running. If it says the helper is off, press **Fix** and turn on
   "Home Depot Helper" in the Accessibility settings.

5. Make sure your Notification Keeper is a version that knows about the helper (its Home Depot
   notifications hand the email's subject and preview to the helper when it is installed). Then tap the
   next Home Depot notification. The first time, GooglebookOS asks how to open Home Depot links;
   choose **Stay in app**.

## Testing it without waiting for an email

These adb commands open the helper the way a notification does:

```bash
# An order: opens order WN74903499 in the Home Depot app (use one of your own order numbers).
adb shell am start --user <USER_ID> -n local.hdhelper/.OpenActivity --es subject "'Order # WN74903499 Shipped: Your order is on its way'"
# No order number: opens the purchase history.
adb shell am start --user <USER_ID> -n local.hdhelper/.OpenActivity --es subject "'We received your order!'"
# A code: copies 123456 and types it into a code field if the Home Depot app shows one.
adb shell am start --user <USER_ID> -n local.hdhelper/.OpenActivity --es subject "'Your Home Depot verification code: 123456'"
```

The helper logs what it did under the tag `HdHelper` (`adb logcat -d -s HdHelper`), and the status
window shows the last action.

## Safety and privacy notes

* No internet permission. The helper opens addresses in the Home Depot app and never fetches anything.
* It stores one line about its last action, for the status window, and nothing else. The code it
  copies goes to the system clipboard, like a code you copy yourself.
* The accessibility service is limited to the Home Depot app by its configuration
  (`res/xml/hd_helper.xml`), reads the screen only while a code is waiting, performs no gestures, and
  stores nothing.
* `WRITE_SECURE_SETTINGS` is used for one thing: adding the helper's own service to the list of
  enabled accessibility services when an update removed it.

## Limitations

* The notification only carries the email's subject and preview. When Home Depot puts the order
  number further down in the email than the preview reaches, the helper opens the purchase history
  instead of the order; the newest order is at the top.
* Typing the code depends on the Home Depot app showing a recognizable code field at that moment. The
  code is always on the clipboard as well.
* GooglebookOS's "How do you want to open Home Depot links?" question has to be answered once by you;
  the helper does not answer it for you.
* The helper opens the Home Depot app in whatever window it already has. The app only works in a
  phone-sized window on the Googlebook, which my
  [Home Depot app phone mode](https://github.com/iamflying29-sketch/googlebook-projects/tree/main/smaller-fixes/homedepot-phone-mode)
  fix takes care of, including a window that has become a fullscreen task.
* English only, and tested only on my Googlebook.

## Files

| File | Purpose |
| --- | --- |
| `AndroidManifest.xml` | Package `local.hdhelper`, the entry activity, the status window, the accessibility service and the boot receiver |
| `build.sh` | Builds and signs `HomeDepotHelper.apk` (aapt2, kotlinc, d8, zipalign, apksigner) |
| `src/kotlin/local/hdhelper/Target.kt` | Works out where an email points: an order, the purchase history, a code, or the app |
| `src/kotlin/local/hdhelper/OpenActivity.kt` | The invisible entry point a notification opens |
| `src/kotlin/local/hdhelper/HdHelperService.kt` | The accessibility service that types a code |
| `src/kotlin/local/hdhelper/Guard.kt` | Switches the service back on after an update |
| `src/kotlin/local/hdhelper/BootReceiver.kt` | Runs the guard after a restart or an update |
| `src/kotlin/local/hdhelper/MainActivity.kt` | The status window |
| `src/kotlin/local/hdhelper/Status.kt` | The one-line status for the window |
| `res/xml/hd_helper.xml` | The accessibility service's configuration |
| `res/values/strings.xml` | All app text |

## Disclaimer and license

This is an independent, unofficial project. It is not affiliated with, endorsed by, or sponsored by
The Home Depot, Google or Lenovo. All product names belong to their owners. Released under the MIT
License. Copyright (c) 2026 Thomas Liviakis.
