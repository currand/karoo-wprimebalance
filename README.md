# Karoo W' Balance Extension

A Hammerhead Karoo extension to display the W' Balance (sometimes referred to as Functional Reserve Capacity).
The formula and basis of the code for calculation comes from [RT-Critical-Power](https://github.com/Berg0162/RT-Critical-Power).

## Features
- Display W' Balance in Joules
- Display available W' Balance as a percentage available
- Display time to exhaustion of W' Balance based on 10s average power
- Display "matches" burned. A match is defined as an effort over FTP/CP60 that consumes at least 2000J
- Display duration of the last effort
- Display Joules burned in the last effort

## Requirements
- Karoo (tested on last Karoo ) with version 1.527 or later
- A relatively accurate assessment of your 60-minute Critical Power (CP60) or your Functional Threshold Power (FTP)
- A power meter

## Installation

You can sideload the app using the following steps for Karoo 2

1. Download the APK from the releases .
1. Prepare your Karoo for sideloading by following the [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html) by DC Rainmaker.
1. Install the app using the command `adb install app-release.apk`.
1. **Reboot your Karoo**

If you've Karoo 3 and v > 1.527 you can sideload the app using the following steps:

1. Share the apk link from the [releases page](https://github.com/currand/karoo-wprimebalance/releases) with Hammerhead companion app
2. Install the app using the Hammerhead companion app.

To configure:
1. From your Karoo, add a new sensor. Click the extension icon at the top and select "_W' Balance_"
1. In the Extension screen, configure FTP and W' estimate in Joules. This can be from a test or from an educated guess
1. If you are unsure of your W', select the option to estimate your Critical Power and W' while riding.
1. Add desired data fields.

**It's mandatory to reset the Karoo after the installation (shutdown and start again).**

## Links

[RT-Critical-Power](https://github.com/Berg0162/RT-Critical-Power)
