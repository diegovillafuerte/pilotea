# S5 measured placement scenarios

`MeasuredVerdictChipTest` measures the production full and compact `Regla`, then uses the production placement policy before drawing. These 11 reviewed PNGs show C8–C12, C12 at 1.3×, N8–N10, 320dp at 1.3×, and 393dp at 2×. Grey rectangles are observed chrome, the large surface is the full card, the black rectangle is its protected action, and blue rectangles are marker outer bounds. They deliberately illustrate geometry rather than reproduce third-party map artwork. Nothing here authorizes a live layout.

The phone is 393×852dp (320dp in the narrow case). C8's half-pixel boundary rounds to native pixels in these mdpi images; the pure positioning tests also assert the exact 2× reference bounds. C10 retains 12dp side/16dp foot margins after font growth. C11, narrow 1.3× and large 2× contain no chip. C12 at 1.3× uses the compact foot over the marker, never over a control. Night changes no geometry.

Normal `:overlay:testDebugUnitTest` compares all pixels against `v3/`, with actual output in `overlay/build/outputs/v3-screenshots/`. Explicit recording:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :overlay:testDebugUnitTest --tests '*MeasuredVerdictChipTest' -Pv3.recordScreenshots=true --console=plain
```

Robolectric API 34 native View drawing is consistent with the existing S1/S4 screenshot harness. Unplaced measurement copies are hidden from accessibility. Physical WindowManager offsets, hardware shadow/motion and host replay remain TD-048/TD-047; no authorized device was available.


## S6 passive stopped tab

`ReaderStoppedTabTest` adds five `readiness_C6_*` component comparisons: 393dp day/night at 1×, 393dp at 1.3×, and 320dp at 1.3×/2×. The tab is 240×72 at 1×; native font metrics determine its interior padding, and wrapped large text grows without truncation. These are component stress renders, not certified host placement: production still uses S5's safe-band fit and can suppress an unavailable fit. All three strings are accessible and no click action is exposed. `ReaderStoppedTabGateTest` covers the five-second deadline, foreground changes and immediate loss/pause/recovery hiding. Record only this test with `--tests '*ReaderStoppedTabTest' -Pv3.recordScreenshots=true`; ordinary tests compare saved pixels. Physical pass-through/recovery remains TD-049.
