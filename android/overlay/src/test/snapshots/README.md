# Measured placement of the v4 pill (WP-B)

`MeasuredVerdictChipTest` measures the production pill twice (with its Pon Tu Precio tray when it has one, and plain), then uses the production placement policy before drawing. Its 16 reviewed PNGs live in `v4/`, named `<screen>_<frame>_<day|night>_<width>_<fontScale>` after the v4 kit's frames: S1 day and night, S2 day and night, S3, S4 day and night, S5 and S9 (a DiDi bid with its decline control and the tray), S10 (bruto), S7 at 1.3×, S1 at 320dp/1.3× and at 2×, and S1 beside map markers (`placement-markers`) and a driver ring (`placement-ring`, 1× and 1.3×). Grey rectangles are observed chrome, the large surface is the card, black rectangles are protected controls, and blue rectangles are marker outer bounds. They illustrate geometry rather than reproduce third-party map artwork. Nothing here authorizes a live layout. S6 (a tall offer under navigation) is asserted, not drawn, by the same test.

Normal `:overlay:testDebugUnitTest` compares each render with `v4/` by the shared rule `BaselineComparison` (`android/test-support`: identical, or any number of pixels by 1 level of 255, at most 0.1 % by more and none by more than 3), with actual output in `overlay/build/outputs/v4-screenshots/`. Explicit recording:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :overlay:testDebugUnitTest --tests '*MeasuredVerdictChipTest' -Pv4.recordScreenshots=true --console=plain
```

Robolectric API 34 native View drawing is consistent with the existing S1/S4 screenshot harness. Unplaced measurement copies are hidden from accessibility. Physical WindowManager offsets, hardware shadow/motion and host replay remain TD-048/TD-047; no authorized device was available.


## Passive stopped tab (v3 C6, the v4 kit's S8)

`ReaderStoppedTabTest` adds five `readiness_C6_*` component comparisons: 393dp day/night at 1×, 393dp at 1.3×, and 320dp at 1.3×/2×. The tab is 240×72 at 1×; native font metrics determine its interior padding, and wrapped large text grows without truncation. These are component stress renders, not certified host placement: production still uses S5's safe-band fit and can suppress an unavailable fit. All three strings are accessible and no click action is exposed. `ReaderStoppedTabGateTest` covers the five-second deadline, foreground changes and immediate loss/pause/recovery hiding. Record only this test with `--tests '*ReaderStoppedTabTest' -Pv3.recordScreenshots=true`; ordinary tests compare with the saved PNGs by the same rule. Physical pass-through/recovery remains TD-049.
