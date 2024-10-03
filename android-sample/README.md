# Open-Measurement-ReferenceApp-Android

Note: The files in the OM-TestApp GitHub repo are included in the SDK distribution archive under OM-DemoApp.
The files in the GitHub repo are not namespaced but the files within the SDK distribution are namespaced by partner name. 

## Overview
This is a simple sample application demonstrating the use of the Android Open Measurement SDK (OMSDK)

Included are 5 sample implementations:

* Native display ad
* Pre-rendered HTML display ad 
* HTML display ad
* Native video ad
* Native audio ad

## Building the app
- To build the app, switch the app to the defaultsDebug variant
- Now you can build & run in Android Studio 

##### NOTE: This project uses Android Studio 3.0+

### Running this app
- NOTE: First, manually enable storage permissions : Settings -> Apps -> OMSDKSampleApp -> Permissions -> Storage
(The app may crash on recent Android versions without doing the above)
- If using emulator, try a standard & recent emulator like Pixel / API 27
  If you find that some cases don't work on emulator (eg. Html display creative) then you’ll need to disable any network security protection you may have enabled on your laptop (since the refApp runs a web server and if emulator is being used then your PC’s network security settings might block the refApp's web server / ports)

### Testing notes
Set up your device or emulator to proxy through Charles.

In Charles, look for urls containing: complianceomsdk.iabtechlab.com/sendmessage?
All OMID events & event data will be mapped to the above 

For native ad sessions, an external url is required to access `omid-validation-verification-script-v1`. This is specified in the 
`build.config` `VERIFICATION_URL` field. 
For HTML ad sessions the `omid-validation-verification-script-v1` found in the assets folder is used.
Both the static asset as well as the externally hosted script should ideally be the same.

### Contributions welcome!
