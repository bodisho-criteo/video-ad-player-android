# Criteo Video Ad Player for Android

## Overview

The Criteo Video Ad Player for Android is an open-source library that provides a ready-to-integrate video ad wrapper for rendering and tracking Onsite Video ads in native Android apps.

### Integration Guide

For integration guidance please visit our [developer portal](https://developers.criteo.com/retailer-integration/docs/video-player-implementation-app-android).

### Known issues

- **Video resumes after a paused video is switched back into focus** — The video automatically resumes when the user switches back to the tab/app, even though they manually paused it beforehand. The resume beacon is correctly not fired, but the playback behaviour is wrong. _(will be fixed soon)_
